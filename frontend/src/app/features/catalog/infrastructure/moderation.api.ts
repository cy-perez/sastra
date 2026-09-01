import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import type { ListingRejectionReason } from '../../../shared/domain/listing';
import type { RevocationReason } from '../../../shared/domain/revocation-reason';

/** Lo que se manda al bajar una publicación. Es de esta capa y no sale de ella. */
interface RemoveListingRequestDto {
  readonly reason: ListingRejectionReason;
  readonly note: string | null;
}

/** Lo mismo al revocar, con la otra lista cerrada (RN-069). */
interface RevokeVerificationRequestDto {
  readonly reason: RevocationReason;
  readonly note: string | null;
}

/** Lo justo para saber si hay sello que revocar, y sobre cuál. */
export interface VerificationSummary {
  readonly id: string;
  readonly status: string;
}

/**
 * Las dos acciones con las que un moderador deshace lo que aprobó. HU-010.
 *
 * <p><strong>Vive en `catalog` aunque una de las dos sea de identidad</strong>, y no es un
 * descuido de ubicación: las dos se pintan en pantallas de `catalog` —la ficha pública y
 * el perfil del vendedor— y **una funcionalidad no importa de otra** (frontend/CLAUDE.md).
 * La alternativa era subir a `shared` un adaptador de moderación, y `shared` es para lo
 * que no es de nadie, no para lo que es de dos. Lo que sí subió es la lista cerrada de
 * motivos, que es vocabulario.
 *
 * <p>`removal` duplica una línea de `listing-review`, que tiene el mismo POST sin usar.
 * Es una llamada, y la regla de no cruzar funcionalidades vale más que ahorrarla.
 */
@Injectable({ providedIn: 'root' })
export class ModerationApi {
  private readonly http = inject(HttpClient);

  /**
   * Baja una publicación que ya era visible. RN-024.
   *
   * <p>Ruta propia y no la de archivar del vendedor, aunque el estado final sea el mismo:
   * el vendedor archiva lo suyo y no da explicaciones, y aquí el motivo es obligatorio
   * porque va en el correo que avisa.
   */
  async bajar(id: string, motivo: ListingRejectionReason, nota: string | null): Promise<void> {
    const cuerpo: RemoveListingRequestDto = { reason: motivo, note: nota };
    await firstValueFrom(this.http.post(`listings/${id}/removal`, cuerpo));
  }

  /**
   * La verificación de un vendedor, por el identificador que da su perfil público.
   *
   * <p>Existe porque las decisiones van sobre el identificador de la **verificación** y el
   * perfil entrega el del vendedor. Responde 404 cuando esa persona nunca empezó, que no
   * es un error: es la respuesta a «no hay sello que revocar».
   */
  async verificacionDe(vendedor: string): Promise<VerificationSummary> {
    return firstValueFrom(
      this.http.get<VerificationSummary>(`verifications/by-seller/${vendedor}`),
    );
  }

  /** Quita el sello. RN-013 y RN-069: la lista de motivos no es la del rechazo. */
  async revocar(
    verificacion: string,
    motivo: RevocationReason,
    nota: string | null,
  ): Promise<void> {
    const cuerpo: RevokeVerificationRequestDto = { reason: motivo, note: nota };
    await firstValueFrom(this.http.post(`verifications/${verificacion}/revocation`, cuerpo));
  }
}
