import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { MOTIVOS_DE_RECHAZO, type RejectionReason } from '../../../shared/domain/rejection-reason';
import { ReviewStore } from '../application/review.store';
import { hayDiscrepanciaDeTitular, IMAGENES } from '../domain/pending-verification';
import { VerificationImage } from './verification-image';

/** Lo que hay pendiente de confirmar. `null` cuando no se ha pulsado nada. */
type Decision = 'aprobar' | 'rechazar' | null;

/**
 * El detalle de una solicitud, donde se decide. HU-006.
 *
 * <p>Los datos salen de la bandeja que ya está cargada, no de una consulta propia: el
 * servidor no ofrece un endpoint por solicitud, y pedir la bandeja entera para quedarse
 * con una fila es lo que hay. Al recargar con la dirección directa, la bandeja se carga
 * igual y la fila aparece cuando llega.
 *
 * <p><strong>Las imágenes no se piden aquí.</strong> Cada una es un acceso registrado, y
 * las pide su propio componente cuando alguien la abre.
 */
@Component({
  selector: 'sastra-review-detail-page',
  imports: [TranslocoPipe, RouterLink, VerificationImage],
  templateUrl: './review-detail-page.html',
  styleUrl: './review.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewDetailPage {
  /** Lo entrega el router con withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly store = inject(ReviewStore);
  private readonly router = inject(Router);

  protected readonly consulta = this.store.inbox;
  protected readonly aprobacion = this.store.approval;
  protected readonly rechazo = this.store.rejection;

  protected readonly motivos = MOTIVOS_DE_RECHAZO;
  protected readonly imagenes = IMAGENES;

  protected readonly solicitud = computed(() => this.store.solicitud(this.id()));

  protected readonly discrepa = computed(() => {
    const actual = this.solicitud();
    return actual !== undefined && hayDiscrepanciaDeTitular(actual);
  });

  /** Criterio 10: aprobar y rechazar se confirman una vez. No se deshacen. */
  protected readonly porConfirmar = signal<Decision>(null);

  protected readonly motivoElegido = signal<RejectionReason | ''>('');
  protected readonly nota = signal('');

  /**
   * Criterio 12 y RN-060: sobre lo propio no se decide, y se dice antes de intentarlo.
   *
   * <p>El servidor lo rechaza igual —esconder el botón no es la regla— pero enterarse
   * después de pulsar, con un correo ya prometido, es peor experiencia y no hace falta.
   */
  protected readonly esPropia = computed(() => this.solicitud()?.own === true);

  /** Criterio 9: sin motivo elegido, la acción no se puede enviar. */
  protected readonly puedeRechazar = computed(
    () => !this.esPropia() && this.motivoElegido() !== '',
  );

  protected readonly enCurso = computed(
    () => this.aprobacion.isPending() || this.rechazo.isPending(),
  );

  /** Criterio 11: se dice qué pasó, no «error inesperado». */
  protected readonly yaResuelta = computed(
    () =>
      ReviewStore.yaResuelta(this.aprobacion.error()) ||
      ReviewStore.yaResuelta(this.rechazo.error()),
  );

  protected readonly claveDeError = computed(() => {
    const fallo = this.aprobacion.error() ?? this.rechazo.error();
    return fallo === null ? null : ReviewStore.claveDeError(fallo);
  });

  protected pedirConfirmacion(cual: Exclude<Decision, null>): void {
    this.porConfirmar.set(cual);
  }

  protected cancelar(): void {
    this.porConfirmar.set(null);
  }

  protected elegirMotivo(valor: string): void {
    this.motivoElegido.set(valor as RejectionReason | '');
  }

  protected async confirmar(): Promise<void> {
    const decision = this.porConfirmar();

    if (decision === 'aprobar') {
      await this.aprobacion.mutateAsync(this.id());
    } else if (decision === 'rechazar' && this.puedeRechazar()) {
      await this.rechazo.mutateAsync({
        id: this.id(),
        motivo: this.motivoElegido() as RejectionReason,
        nota: this.nota().trim() === '' ? null : this.nota().trim(),
      });
    } else {
      return;
    }

    this.porConfirmar.set(null);
    // Criterio 8: vuelve a la lista. Se navega en vez de quedarse porque la solicitud
    // ya no está en la bandeja y esta pantalla se quedaría diciendo que no existe.
    await this.router.navigate(['/moderacion/verificaciones']);
  }
}
