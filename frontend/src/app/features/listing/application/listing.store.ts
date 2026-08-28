import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import { PhotoNormalizer } from '../../../shared/infrastructure/photo-normalizer';
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
  private readonly consultas = inject(QueryClient);
  private readonly sesion = inject(SessionStore);

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
      this.api.editar(cambio.id, cambio.datos),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly changePrice = injectMutation(() => ({
    mutationFn: (cambio: { id: string; precio: Money }) =>
      this.api.cambiarPrecio(cambio.id, cambio.precio),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly changeShipping = injectMutation(() => ({
    mutationFn: (cambio: { id: string; envio: Shipping }) =>
      this.api.cambiarEnvio(cambio.id, cambio.envio),
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
      const normalizada = await this.normalizador.normalizar(toma.imagen);

      this.avance.set({ posicion: toma.posicion, fraccion: 0 });

      try {
        return await this.subirConAvance(toma.id, toma.posicion, normalizada, toma.desdeGaleria);
      } finally {
        this.avance.set(null);
      }
    },
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly removeImage = injectMutation(() => ({
    mutationFn: (imagen: { id: string; imagenId: string }) =>
      this.api.borrarImagen(imagen.id, imagen.imagenId),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly submit = injectMutation(() => ({
    mutationFn: (id: string) => this.api.enviarARevision(id),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly withdraw = injectMutation(() => ({
    mutationFn: (id: string) => this.api.retirarDeRevision(id),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly reopen = injectMutation(() => ({
    mutationFn: (id: string) => this.api.retomar(id),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly pause = injectMutation(() => ({
    mutationFn: (id: string) => this.api.pausar(id),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  readonly resume = injectMutation(() => ({
    mutationFn: (id: string) => this.api.reanudar(id),
    retry: false,
    onSuccess: (publicacion: Listing) => this.refrescar(publicacion),
  }));

  /** Irreversible: la publicación no vuelve y sus fotos se borran. */
  readonly archive = injectMutation(() => ({
    mutationFn: (id: string) => this.api.archivar(id),
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
