import { computed, inject, Injectable, signal } from '@angular/core';
import {
  injectMutation,
  injectQuery,
  keepPreviousData,
  QueryClient,
} from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import type { Category, ListingRejectionReason } from '../../../shared/domain/listing';
import { porEspera, type PendingListing } from '../domain/pending-listing';
import { ListingReviewApi } from '../infrastructure/listing-review.api';
import { queryKeys } from './query-keys';

/**
 * El estado de la bandeja de moderación de publicaciones. HU-008.
 *
 * Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * <p><strong>Las tomas sí pasan por aquí</strong>, al revés que en HU-006. Allí cada
 * imagen era un acceso a un dato personal que dejaba fila en la bitácora, así que
 * cachearlas falseaba la cuenta. Aquí son fotos de una prenda en el almacén público:
 * mirarlas es el trabajo, no hay nada que registrar, y llegan dentro de la publicación.
 */
/** Una hora. El árbol no cambia mientras alguien revisa, y pedirlo por fila sería absurdo. */
const TIEMPO_DE_FRESCURA_DEL_ARBOL = 60 * 60 * 1000;

@Injectable({ providedIn: 'root' })
export class ListingReviewStore {
  private readonly api = inject(ListingReviewApi);
  private readonly consultas = inject(QueryClient);
  private readonly sesion = inject(SessionStore);

  /** Qué publicación está abierta en el detalle. La pone la propia pantalla. */
  private readonly abierta = signal<string | null>(null);

  /**
   * La cola.
   *
   * <p>Sin tiempo de frescura: dos personas pueden estar moderando a la vez, y una
   * publicación que la otra acaba de decidir no puede seguir ofreciéndose.
   *
   * <p>La señal de sesión se lee **aquí y no dentro de la función**: TanStack invoca las
   * opciones fuera del ámbito reactivo, así que leerla dentro nacería deshabilitada y no
   * se reactivaría nunca. Es el fallo que dejó `/mi-cuenta` sin cargar
   * (frontend/CLAUDE.md).
   */
  /**
   * En qué página está quien revisa.
   *
   * <p>En el estado y no en la dirección, como en la otra cola: esto es una fila que se
   * vacía, así que una página guardada en un enlace apunta a un sitio distinto cada día.
   */
  private readonly paginaActual = signal(0);

  /** El tamaño de página. Es el mismo que el servidor da por omisión. */
  private static readonly TAMANO = 20;

  readonly queue = injectQuery(() => ({
    // La página se lee **aquí**, en las opciones, que es donde TanStack sí observa las
    // señales. Dentro de `queryFn` se leería una sola vez y pasar de página no pediría
    // nada nuevo.
    queryKey: queryKeys.queuePagina(this.paginaActual()),
    queryFn: () => this.api.pendientes(this.paginaActual(), ListingReviewStore.TAMANO),
    staleTime: 0,
    // La página anterior se queda en pantalla mientras llega la siguiente. No es
    // cosmético: sin esto la consulta vuelve a `pending` al cambiar de página, el bloque
    // de paginación se desmonta con el foco dentro y quien navega con teclado acaba en
    // `body`. Es la misma lección que ya pagó la bandeja de verificaciones.
    placeholderData: keepPreviousData,
    // Sin reintentos automaticos. La pantalla tiene su boton de reintentar (criterio 5),
    // y con tres reintentos y espera creciente el fallo tarda segundos en aparecer: quien
    // revisa se queda mirando un esqueleto sin saber si carga o esta roto.
    retry: false,
    enabled: this.sesion.isAuthenticated(),
  }));

  /**
   * La publicación abierta, con sus ocho tomas y sus medidas.
   *
   * <p>Consulta propia y no una fila de la cola: la bandeja devuelve filas, y el detalle
   * necesita el producto entero. Es la diferencia con HU-006, donde el detalle se sacaba
   * de la bandeja ya cargada porque no había endpoint por solicitud. Aquí sí lo hay, y
   * además hace que recargar la dirección directa funcione sin pedir la cola.
   */
  readonly listing = injectQuery(() => {
    const id = this.abierta();
    return {
      queryKey: queryKeys.listing(id ?? ''),
      queryFn: () => this.api.una(id as string),
      staleTime: 0,
      retry: false,
      enabled: id !== null && this.sesion.isAuthenticated(),
    };
  });

  /**
   * El árbol de categorías, para el nombre que pide el criterio 7.
   *
   * <p>Con tiempo de frescura largo, al revés que la cola: son treinta y siete nombres
   * iguales para todo el mundo que no cambian mientras alguien revisa.
   */
  readonly categories = injectQuery(() => ({
    queryKey: queryKeys.categories,
    queryFn: () => this.api.categorias(),
    staleTime: TIEMPO_DE_FRESCURA_DEL_ARBOL,
    retry: false,
    enabled: this.sesion.isAuthenticated(),
  }));

  /**
   * Ordenadas por espera, que es el criterio 1.
   *
   * <p>El servidor ya las manda así; se reordena igual porque depender de eso es depender
   * de algo que ninguna prueba de esta mitad comprueba.
   */
  readonly pendientes = computed<readonly PendingListing[]>(() =>
    porEspera(this.queue.data()?.items ?? []),
  );

  readonly pagina = this.paginaActual.asReadonly();

  /**
   * Si puede haber otra página. Lo contesta el servidor.
   *
   * <p>No se deduce de que la página venga llena: esa deducción no distingue una página
   * llena con más detrás de una página llena que es la última, y con el total múltiplo
   * exacto del tamaño ofrece un «Siguiente» hacia una página vacía.
   *
   * <p>Sin datos todavía va en `false`: mientras carga no se ofrece pasar de página.
   */
  readonly hayMas = computed(() => this.queue.data()?.hasMore ?? false);

  readonly hayAnterior = computed(() => this.paginaActual() > 0);

  /**
   * Si la cola se está actualizando con algo ya en pantalla. No es lo mismo que
   * `isPending`: eso es «no hay nada que enseñar todavía» y lo cuenta el esqueleto.
   */
  readonly actualizando = computed(() => this.queue.isFetching() && !this.queue.isPending());

  paginaSiguiente(): void {
    if (this.hayMas()) {
      this.paginaActual.update((actual) => actual + 1);
    }
  }

  paginaAnterior(): void {
    this.paginaActual.update((actual) => Math.max(0, actual - 1));
  }

  /**
   * Aprobar y rechazar. **Ninguna reintenta**, y esa es la decisión importante.
   *
   * <p>Las dos publican o rechazan una prenda y mandan un correo al vendedor. Un
   * reintento automático sobre un tiempo de espera agotado —cuando la primera sí llegó—
   * duplicaría el correo y dejaría dos filas en la bitácora para una sola decisión.
   *
   * <p>Tras cada una se invalida la cola en vez de quitar la fila a mano: lo que cambió
   * no es solo esta publicación, también puede haber llegado otra.
   */
  readonly approval = injectMutation(() => ({
    mutationFn: (id: string) => this.api.aprobar(id),
    retry: false,
    onSuccess: () => this.refrescarCola(),
    onError: (fallo: unknown) => this.refrescarSiYaNoEstaPendiente(fallo),
  }));

  readonly rejection = injectMutation(() => ({
    mutationFn: (decision: { id: string; motivo: ListingRejectionReason; nota: string | null }) =>
      this.api.rechazar(decision.id, decision.motivo, decision.nota),
    retry: false,
    onSuccess: () => this.refrescarCola(),
    onError: (fallo: unknown) => this.refrescarSiYaNoEstaPendiente(fallo),
  }));

  /** El árbol plano, o vacío si todavía no llegó. */
  arbol(): readonly Category[] {
    return this.categories.data() ?? [];
  }

  /** Qué publicación mira el detalle. */
  abrir(id: string): void {
    this.abierta.set(id);
  }

  /**
   * La fila de la cola, si está cargada.
   *
   * <p>Es de donde sale `own` para RN-063: la publicación completa dice quién es el
   * vendedor, pero comparar identificadores en la pantalla es reimplementar la regla en
   * el sitio equivocado. El servidor ya respondió si es tuya.
   */
  fila(id: string): PendingListing | undefined {
    return this.pendientes().find((pendiente) => pendiente.id === id);
  }

  /**
   * La clave de traducción de un fallo.
   *
   * Delega en {@link ApiError}, que es quien sabe leer el `code` del cuerpo de error.
   */
  static claveDeError(fallo: unknown): string {
    return fallo instanceof ApiError ? fallo.translationKey : 'errors.fallback';
  }

  /**
   * Si el fallo es «esto ya no está en revisión», que tiene su propio mensaje.
   *
   * <p>Cubre los criterios 11 y 13 con el mismo código: que otra persona decidiera antes
   * y que el vendedor la retirara. Al moderador le pasa lo mismo en los dos casos —ya no
   * le toca— y distinguirlos solo serviría para contarle qué hizo otra persona.
   */
  static yaNoEstaPendiente(fallo: unknown): boolean {
    return fallo instanceof ApiError && fallo.code === 'CATALOG_LISTING_INVALID_STATE';
  }

  /**
   * Criterios 11 y 13, la mitad que se olvida: cuando la publicación ya no está en la
   * cola, la bandeja que se está mirando ya no es la que hay. Se refresca sola, o quien
   * revisa volvería a abrir la siguiente fila fantasma.
   */
  private refrescarSiYaNoEstaPendiente(fallo: unknown): void {
    if (ListingReviewStore.yaNoEstaPendiente(fallo)) {
      this.refrescarCola();
    }
  }

  private refrescarCola(): void {
    void this.consultas.invalidateQueries({ queryKey: queryKeys.queue });
  }
}
