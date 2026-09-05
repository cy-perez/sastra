import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  untracked,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ListingStore } from '../application/listing.store';
import { esAccionDeModeracionConocida, type ModerationEvent } from '../../../shared/domain/listing';

/** Un paso del rastro, ya listo para pintar. */
interface PasoDelRastro {
  readonly clave: string;
  readonly etiqueta: string;
  readonly motivo: string | null;
  readonly iso: string;
  readonly cuando: string;
}

/**
 * El rastro de moderación de una publicación propia. HU-013.
 *
 * <p>Va **dentro de la publicación y no como pantalla aparte**: se llega desde
 * `/mis-publicaciones` y desde el borrador rechazado, que es donde hoy ya se ve el motivo
 * del último rechazo. Por eso es un componente y no una ruta.
 *
 * <p><strong>Se abre plegado y solo uno a la vez.</strong> `/mis-publicaciones` pinta hasta
 * veinte filas, y un rastro desplegado por fila serían veinte peticiones que nadie pidió.
 * Cuál está abierto lo guarda el store, así que abrir uno cierra el otro sin que ninguno
 * de los dos componentes sepa que el otro existe.
 *
 * <p><strong>Con texto y no solo con color</strong>, que es RN-012 aplicado a otra
 * pantalla: un rechazo no puede distinguirse de una aprobación por el tono.
 *
 * <p>Sus cuatro estados —cargando, vacío, error y listo— quedan acotados a este bloque. Un
 * rastro que no carga no puede tumbar la publicación de la que cuelga, que es lo mismo que
 * decidió HU-012 para las cifras y HU-008 para la bandeja.
 */
@Component({
  selector: 'sendik-moderation-trail',
  imports: [TranslocoPipe],
  templateUrl: './moderation-trail.html',
  styleUrl: './moderation-trail.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModerationTrail {
  private readonly store = inject(ListingStore);
  private readonly idioma = inject(TranslocoService);

  /** De qué publicación es el rastro. */
  readonly publicacion = input.required<string>();

  protected readonly consulta = this.store.history;

  /**
   * Si este rastro es el que está abierto.
   *
   * <p>Derivado del store y no de una señal local: con una local, abrir el de otra fila
   * dejaría a esta con el botón desplegado enseñando eventos que no son suyos, porque la
   * consulta es una sola.
   */
  protected readonly abierto = computed(() => this.store.rastroAbierto() === this.publicacion());

  /**
   * El identificador de la región que el botón despliega.
   *
   * <p>Sale del identificador de la publicación y no de un contador ni de
   * `crypto.randomUUID`: tiene que ser el mismo en el servidor y en el cliente o la
   * hidratación encuentra dos documentos distintos. Es único porque en una pantalla no hay
   * dos veces la misma publicación.
   */
  protected readonly idDeLaRegion = computed(() => `rastro-${this.publicacion()}`);

  constructor() {
    // Si esta fila desaparece con su rastro abierto —al archivar, al cambiar de página—,
    // el store se quedaria creyendo que sigue a la vista y pidiendolo. Solo se limpia si
    // el abierto era el propio: cerrar el de otro al destruirse este seria peor.
    inject(DestroyRef).onDestroy(() => {
      if (untracked(this.abierto)) {
        this.store.mirarElRastro(null);
      }
    });
  }

  protected alternar(): void {
    this.store.mirarElRastro(this.abierto() ? null : this.publicacion());
  }

  protected reintentar(): void {
    void this.consulta.refetch();
  }

  /**
   * Los pasos, ya traducidos y fechados.
   *
   * <p><strong>Una acción que esta versión no conoce se pinta igual</strong>, con su fecha
   * y una descripción genérica. Es el caso borde de la historia y es lo contrario de lo que
   * hacen las cifras del panel, que descartan un estado desconocido: allí una cifra sin
   * nombre no se puede explicar, aquí omitir la fila escondería que algo pasó, que es lo
   * único que este rastro existe para no hacer.
   */
  protected readonly pasos = computed<readonly PasoDelRastro[]>(() =>
    (this.consulta.data() ?? []).map((evento, posicion) => ({
      // La posición entra en la clave porque tiene que ser única y nada más lo garantiza:
      // dos eventos pueden compartir instante y acción, y una clave repetida en un `@for`
      // es un error en tiempo de ejecución. El coste es repintar la lista entera cuando
      // llega un evento nuevo, que en una lista de unas pocas filas no se nota.
      clave: `${posicion}#${evento.occurredAt}#${evento.action}`,
      etiqueta: esAccionDeModeracionConocida(evento.action)
        ? `listing.trail.action.${evento.action}`
        : 'listing.trail.action.unknown',
      motivo: evento.reason === null ? null : `listing.rejectionReason.${evento.reason}`,
      iso: evento.occurredAt,
      cuando: this.fechaDe(evento),
    })),
  );

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }

  /**
   * La fecha, en la zona y el formato de la configuración regional activa. Criterio 9.
   *
   * <p>Con `Intl` y la zona del navegador, no en UTC crudo: «2026-09-04T22:10:00Z» no le
   * dice a nadie en Colombia qué día pasó eso.
   */
  private fechaDe(evento: ModerationEvent): string {
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(evento.occurredAt));
  }
}
