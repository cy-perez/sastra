import { computed, inject, Injectable } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import type { RejectionReason } from '../../../shared/domain/rejection-reason';
import {
  porAntiguedad,
  type PendingVerification,
  type VerificationImage,
} from '../domain/pending-verification';
import { VerificationReviewApi } from '../infrastructure/verification-review.api';
import { queryKeys } from './query-keys';

/**
 * El estado de la bandeja del moderador. HU-006.
 *
 * Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * <p><strong>Las imágenes no pasan por aquí</strong>, y es deliberado. Cada lectura de
 * una imagen deja una fila en la bitácora (RN-046), así que una consulta con caché
 * convertiría «lo miré una vez» en un número de accesos que no se corresponde con nada:
 * o se registra de más al reintentar, o de menos al servir desde caché. El componente la
 * pide directamente al adaptador cuando alguien la abre, una vez, y gestiona su URL de
 * objeto.
 */
@Injectable({ providedIn: 'root' })
export class ReviewStore {
  private readonly api = inject(VerificationReviewApi);
  private readonly consultas = inject(QueryClient);
  private readonly sesion = inject(SessionStore);

  /**
   * La bandeja.
   *
   * <p>Sin tiempo de frescura: dos personas pueden estar revisando a la vez y una
   * solicitud que la otra acaba de resolver no puede seguir ofreciéndose.
   *
   * <p>La señal de sesión se lee **aquí y no dentro de la función**: TanStack invoca las
   * opciones fuera del ámbito reactivo, así que leerla dentro nacería deshabilitada y no
   * se reactivaría nunca. Es el fallo que dejó `/mi-cuenta` sin cargar
   * (frontend/CLAUDE.md).
   */
  readonly inbox = injectQuery(() => ({
    queryKey: queryKeys.inbox,
    queryFn: () => this.api.pendientes(),
    staleTime: 0,
    // Sin reintentos automaticos. La pantalla tiene su boton de reintentar (criterio 4),
    // y con tres reintentos y espera creciente el fallo tarda segundos en aparecer:
    // quien revisa se queda mirando un esqueleto sin saber si carga o esta roto. El
    // reintento manual es la misma accion, con el control de quien la pide.
    retry: false,
    enabled: this.sesion.isAuthenticated(),
  }));

  /**
   * Ordenadas por antigüedad, que es el criterio 1.
   *
   * <p>El servidor ya las manda así; se reordena igual porque depender de eso es
   * depender de algo que ninguna prueba de esta mitad comprueba.
   */
  readonly pendientes = computed<readonly PendingVerification[]>(() =>
    porAntiguedad(this.inbox.data() ?? []),
  );

  readonly total = computed(() => this.pendientes().length);

  /**
   * Aprobar y rechazar. **Ninguna reintenta**, y esa es la decisión importante.
   *
   * <p>Las dos otorgan o niegan un sello y mandan un correo a una persona. Un reintento
   * automático sobre un tiempo de espera agotado —cuando la primera sí llegó— duplicaría
   * el correo y dejaría dos filas en la bitácora para una sola decisión. Si falla, lo
   * dice y quien revisa vuelve a pulsar.
   *
   * <p>Tras cada una se invalida la bandeja en vez de quitar la fila a mano: lo que
   * cambió no es solo esta solicitud, también puede haber llegado otra, y quien revisa
   * necesita ver la lista de verdad antes de abrir la siguiente.
   */
  readonly approval = injectMutation(() => ({
    mutationFn: (id: string) => this.api.aprobar(id),
    retry: false,
    onSuccess: () => this.refrescarBandeja(),
  }));

  readonly rejection = injectMutation(() => ({
    mutationFn: (decision: { id: string; motivo: RejectionReason; nota: string | null }) =>
      this.api.rechazar(decision.id, decision.motivo, decision.nota),
    retry: false,
    onSuccess: () => this.refrescarBandeja(),
  }));

  /** Una solicitud concreta, de las que ya están en la bandeja. */
  solicitud(id: string): PendingVerification | undefined {
    return this.pendientes().find((pendiente) => pendiente.id === id);
  }

  /**
   * Los bytes de una imagen. Va directo al adaptador, sin caché: ver la explicación de
   * arriba.
   */
  imagen(id: string, cual: VerificationImage): Promise<Blob> {
    return this.api.imagen(id, cual);
  }

  /**
   * La clave de traducción de un fallo.
   *
   * Delega en {@link ApiError}, que es quien sabe leer el `code` del cuerpo de error.
   */
  static claveDeError(fallo: unknown): string {
    return fallo instanceof ApiError ? fallo.translationKey : 'errors.fallback';
  }

  /** Si el fallo es «esto ya no está pendiente», que tiene su propio mensaje. */
  static yaResuelta(fallo: unknown): boolean {
    return fallo instanceof ApiError && fallo.code === 'SELLER_VERIFICATION_INVALID_STATE';
  }

  private refrescarBandeja(): void {
    void this.consultas.invalidateQueries({ queryKey: queryKeys.inbox });
  }
}
