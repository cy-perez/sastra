import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import { PhotoNormalizer } from '../../../shared/infrastructure/photo-normalizer';
import { CaptureDraftStore, type TomaGuardada } from '../infrastructure/capture-draft.store';
import type { Listing, Money, Shipping } from '../../../shared/domain/listing';
import type { DatosDelProducto } from '../infrastructure/listing.api';
import { ListingApi } from '../infrastructure/listing.api';
import { queryKeys } from './query-keys';

/**
 * El estado de la publicación de producto. HU-007.
 *
 * Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * **Ninguna mutación reintenta, y no es descuido.** La de subir manda un archivo: un
 * reintento automático lo manda dos veces y, si el primero sí llegó, deja una toma
 * duplicada o un archivo huérfano en el almacén. Las de estado tampoco: enviar a revisión
 * dos veces por un reintento le pone al moderador la misma publicación dos veces en la
 * bandeja, y archivar es irreversible.
 *
 * Cada mutación refresca la consulta con lo que devolvió el servidor en lugar de pedirlo
 * otra vez: es exactamente lo que hay en la base, y evita que el avance tarde un viaje más
 * en aparecer.
 */
@Injectable({ providedIn: 'root' })
export class ListingStore {
  private readonly api = inject(ListingApi);
  private readonly normalizador = inject(PhotoNormalizer);
  private readonly borrador = inject(CaptureDraftStore);
  private readonly consultas = inject(QueryClient);
  private readonly sesion = inject(SessionStore);

  /**
   * Las escrituras sobre una publicación salen **de una en una**.
   *
   * <p>Toda escritura del agregado guarda con `WHERE version = :version` (criterio 34),
   * así que dos peticiones que se solapen leen la misma versión y el servidor tumba a la
   * segunda con un 409. Eso es lo correcto entre dos personas —el vendedor edita mientras
   * el moderador decide— pero **entre dos peticiones de la misma pantalla no hay conflicto
   * que resolver**: las dos son de quien está escribiendo, y una debería esperar a la otra
   * en vez de perderse.
   *
   * <p>Se solapaban por cuatro caminos: el guardado automático saliendo otra vez antes de
   * que volviera el anterior, el precio y el envío mandándose a la vez por sus rutas
   * propias, una toma subiendo encima de un guardado —subir también reescribe el
   * agregado— y las medidas, que mandaban una petición por tecla.
   *
   * <p>La cola no se rompe con un fallo: si una escritura falla, la siguiente sale igual.
   * Encadenar sobre la promesa rechazada dejaría la pantalla muda desde el primer error.
   *
   * <p>Es una sola cola para todo el store y no una por publicación, que sería lo exacto.
   * No hace falta: se edita una publicación a la vez —la página abre la de la ruta— y una
   * cola por identificador habría que ir vaciándola para que no creciera sola. Lo que no
   * cubre, y no debe cubrir, es la escritura de **otra persona**: las decisiones del
   * moderador van por su propio store y ahí el 409 sí es un conflicto de verdad.
   */
  private cola: Promise<void> = Promise.resolve();

  /**
   * Pone una escritura a la cola y devuelve su resultado.
   *
   * <p>Se envuelve **solo la llamada de red**, no lo que la prepara: normalizar una foto
   * y guardarla en el borrador no tocan la publicación, y meterlos aquí retrasaría la
   * barra de avance sin ganar nada.
   */
  private enCola<T>(escritura: () => Promise<T>): Promise<T> {
    const turno = this.cola.then(escritura);
    this.cola = turno.then(
      () => undefined,
      () => undefined,
    );
    return turno;
  }

  /**
   * Cuál publicación está abierta.
   *
   * Una señal y no un parámetro de la consulta: la página la fija al resolver la ruta, y
   * TanStack vuelve a pedir sola cuando cambia.
   */
  private readonly abierta = signal<string | null>(null);

  /**
   * Las publicaciones propias.
   *
   * La señal de sesión se lee **aquí y no dentro de la función**: TanStack invoca las
   * opciones fuera del ámbito reactivo, así que leerla dentro nacería deshabilitada y no
   * se reactivaría nunca. Es el fallo que dejó `/mi-cuenta` sin cargar
   * (frontend/CLAUDE.md).
   */
  readonly mine = injectQuery(() => ({
    queryKey: queryKeys.mine,
    queryFn: () => this.api.mias(),
    staleTime: 0,
    // Sin reintentos, como las demas: con los tres de por omision, quien se queda sin
    // listado espera siete segundos antes de que la pantalla le diga que algo fallo.
    retry: false,
    enabled: this.sesion.isAuthenticated(),
  }));

  /**
   * La publicación abierta.
   *
   * Sin reintentos: el 404 es una respuesta normal aquí —significa «no es tuya o no
   * existe», y el criterio 33 hace que las dos sean la misma— y reintentar tres veces un
   * 404 solo retrasa la pantalla.
   */
  readonly current = injectQuery(() => ({
    queryKey: queryKeys.one(this.abierta() ?? 'ninguna'),
    queryFn: () => this.api.una(this.abierta() ?? ''),
    staleTime: 0,
    retry: false,
    enabled: this.abierta() !== null && this.sesion.isAuthenticated(),
  }));

  /**
   * Si hay una pantalla mirando las cifras.
   *
   * <p>Este store es de raíz, así que sus consultas nacen en cuanto alguien lo inyecta —y lo
   * inyectan todas las pantallas de publicación—. Sin esta señal, `/publicar` pedía el
   * resumen que no usa, y **cada guardado suyo lo volvía a pedir**, porque la invalidación de
   * la lista casa por prefijo y arrastra al resumen con ella. Una petición por guardado que
   * nadie mira, en la pantalla que más guarda.
   */
  private readonly panelALaVista = signal(false);

  /**
   * La pantalla del panel dice cuándo está a la vista y cuándo deja de estarlo.
   *
   * <p>Mismo trato que {@link abrir} le da a la publicación abierta: la pantalla es quien
   * sabe, y el store no adivina.
   */
  mirarLasCifras(mirando: boolean): void {
    this.panelALaVista.set(mirando);
  }

  /**
   * Las cifras del panel del vendedor. HU-012.
   *
   * Consulta aparte de {@link mine} y no un conteo sobre ella: la lista viene paginada y
   * contar sus filas daría una cifra que solo habla de la página que se cargó.
   *
   * Se refresca sola con cada mutación, porque su clave cuelga de la de la lista y
   * TanStack invalida por prefijo. Ver `queryKeys.summary`.
   *
   * Las dos señales se leen **aquí y no dentro de la función**, por lo mismo que la de
   * sesión: TanStack invoca las opciones fuera del ámbito reactivo, y leerlas dentro dejaría
   * la consulta deshabilitada para siempre.
   */
  readonly summary = injectQuery(() => ({
    queryKey: queryKeys.summary,
    queryFn: () => this.api.resumen(),
    staleTime: 0,
    retry: false,
    enabled: this.panelALaVista() && this.sesion.isAuthenticated(),
  }));

  /** La página fija cuál se está viendo al resolver la ruta. */
  abrir(id: string | null): void {
    this.abierta.set(id);
  }

  readonly create = injectMutation(() => ({
    mutationFn: (datos: DatosDelProducto) => this.api.crear(datos),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly update = injectMutation(() => ({
    mutationFn: (cambio: { id: string; datos: DatosDelProducto }) =>
      this.enCola(() => this.api.editar(cambio.id, cambio.datos)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly changePrice = injectMutation(() => ({
    mutationFn: (cambio: { id: string; precio: Money }) =>
      this.enCola(() => this.api.cambiarPrecio(cambio.id, cambio.precio)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly changeShipping = injectMutation(() => ({
    mutationFn: (cambio: { id: string; envio: Shipping }) =>
      this.enCola(() => this.api.cambiarEnvio(cambio.id, cambio.envio)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  /**
   * El avance de la toma que se esté subiendo, entre 0 y 1. HU-003 criterio 10.
   *
   * <p>Una señal aparte de la mutación porque TanStack sabe si algo está en vuelo, no
   * cuánto lleva. Solo hay una: el asistente sube de una en una y la rejilla deshabilita
   * el resto mientras tanto, así que dos barras a la vez no existen.
   */
  private readonly avance = signal<{ posicion: number; fraccion: number | null } | null>(null);

  readonly uploadProgress = computed(() => this.avance());

  /**
   * Sube una toma, normalizándola antes.
   *
   * <p>**Las dos entradas pasan por aquí**: el fotograma del asistente y el archivo elegido
   * desde la galería. El recorte a 3:4 y el apretón a 500 KB los hace el mismo worker para
   * las dos, que es lo que pide el criterio 8 de HU-003 —«el mismo recorte forzado»— y lo
   * que evita mandar al servidor archivos que iba a rechazar por proporción.
   *
   * <p>Lo que distingue a una de otra es {@code desdeGaleria}, que solo suma una marca de
   * atención para el moderador; nunca quita una validación.
   *
   * <p>Si el recorte no llega a 900 x 1200, esto rechaza con `ImagenNoNormalizable` **antes
   * de gastar la subida**, que es lo que RN-019 pide del formulario.
   */
  readonly uploadShot = injectMutation(() => ({
    mutationFn: async (toma: {
      id: string;
      posicion: number;
      imagen: Blob;
      desdeGaleria: boolean;
    }): Promise<Listing> => {
      // Se guarda **antes de normalizar y de subir**, y solo lo capturado con la cámara:
      // eso es lo que se perdería al cerrar el navegador (criterio 7). Un archivo de la
      // galería no hace falta guardarlo, porque sigue estando en la galería.
      if (!toma.desdeGaleria) {
        await this.borrador.guardar(toma.id, { posicion: toma.posicion, imagen: toma.imagen });
      }

      const normalizada = await this.normalizador.normalizar(toma.imagen);

      this.avance.set({ posicion: toma.posicion, fraccion: 0 });

      try {
        const publicacion = await this.enCola(() =>
          this.subirConAvance(toma.id, toma.posicion, normalizada, toma.desdeGaleria),
        );

        // Subió: desde aquí la fuente es el servidor y la copia local sobra.
        await this.borrador.olvidar(toma.id, toma.posicion);

        return publicacion;
      } finally {
        this.avance.set(null);
      }
    },
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  /**
   * Las tomas que se congelaron y no llegaron a subir. HU-003 criterio 7.
   *
   * <p>Es la mitad que le faltaba al borrador: sin esta lectura, el almacén escribía y
   * borraba sin que nadie recuperara nunca nada, y «cerrar el navegador por accidente no
   * obliga a empezar de nuevo» no se cumplía.
   *
   * <p>Devuelve solo lo que **no está ya en el servidor**: una toma guardada cuya subida
   * sí llegó, aunque la respuesta no, no hay que volver a mandarla.
   */
  async tomasSinSubir(publicacion: Listing): Promise<readonly TomaGuardada[]> {
    const guardadas = await this.borrador.recuperar(publicacion.id);
    const puestas = new Set(
      publicacion.images.filter((imagen) => imagen.kind === 'SELLER_SHOT').map((i) => i.position),
    );

    return guardadas.filter((toma) => !puestas.has(toma.posicion));
  }

  /** Tira el borrador de una publicación. Se llama al salir del asistente. */
  async olvidarBorrador(id: string): Promise<void> {
    await this.borrador.limpiar(id);
  }

  readonly removeImage = injectMutation(() => ({
    mutationFn: (imagen: { id: string; imagenId: string }) =>
      this.enCola(() => this.api.borrarImagen(imagen.id, imagen.imagenId)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly submit = injectMutation(() => ({
    mutationFn: (id: string) => this.enCola(() => this.api.enviarARevision(id)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly withdraw = injectMutation(() => ({
    mutationFn: (id: string) => this.enCola(() => this.api.retirarDeRevision(id)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly reopen = injectMutation(() => ({
    mutationFn: (id: string) => this.enCola(() => this.api.retomar(id)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly pause = injectMutation(() => ({
    mutationFn: (id: string) => this.enCola(() => this.api.pausar(id)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly resume = injectMutation(() => ({
    mutationFn: (id: string) => this.enCola(() => this.api.reanudar(id)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  /** Irreversible: la publicación no vuelve y sus fotos se borran. */
  readonly archive = injectMutation(() => ({
    mutationFn: (id: string) => this.enCola(() => this.api.archivar(id)),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  /**
   * La clave de traducción de un fallo.
   *
   * Delega en {@link ApiError}, que es quien sabe leer el `code` del cuerpo de error:
   * escribir aquí otro acceso a `error.code` daría dos sitios donde arreglarlo el día que
   * el contrato de error cambie.
   */
  static claveDeError(fallo: unknown): string {
    return fallo instanceof ApiError ? fallo.translationKey : 'errors.fallback';
  }

  /**
   * Los campos que el servidor dice que faltan, para marcarlos en el formulario.
   *
   * El criterio 6 los manda en `errors`, con el nombre que tienen en la petición. Sin
   * esto la persona ve «faltan datos» y tiene que adivinar cuáles.
   */
  static camposQueFaltan(fallo: unknown): readonly string[] {
    return fallo instanceof ApiError ? fallo.fieldErrors.map((campo) => campo.field) : [];
  }

  /**
   * Deja en la caché lo que devolvió el servidor.
   *
   * Invalida además el listado propio: al crear, archivar o publicar algo, «mis
   * publicaciones» dejó de ser cierto y volver atrás mostraría lo de antes.
   */
  private refrescar(publicacion: Listing): void {
    this.consultas.setQueryData(queryKeys.one(publicacion.id), publicacion);
    void this.consultas.invalidateQueries({ queryKey: queryKeys.mine });
  }

  /**
   * Consume el flujo de avance y devuelve la publicación del último evento.
   *
   * <p>Aquí muere el observable: el adaptador lo expone porque es lo que sabe emitir
   * progreso, y de esta capa hacia arriba todo son promesas y señales, que es lo que los
   * componentes consumen. La librería no cruza (frontend/CLAUDE.md).
   */
  private subirConAvance(
    id: string,
    posicion: number,
    imagen: Blob,
    desdeGaleria: boolean,
  ): Promise<Listing> {
    return new Promise<Listing>((listo, fallo) => {
      this.api.subirToma(id, posicion, imagen, desdeGaleria).subscribe({
        next: (paso) => {
          if (paso.publicacion !== null) {
            listo(paso.publicacion);
            return;
          }
          this.avance.set({ posicion, fraccion: paso.fraccion });
        },
        error: (causa: unknown) => fallo(causa),
        // Una respuesta 201 sin cuerpo dejaría la promesa colgada para siempre, y con ella
        // la barra de progreso. No debería ocurrir —el servidor devuelve la publicación—,
        // pero una promesa que nadie resuelve no se distingue de una pantalla congelada.
        complete: () => fallo(new Error('La subida terminó sin devolver la publicación')),
      });
    });
  }
}
