import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import type { RejectionReason } from '../../../shared/domain/rejection-reason';
import type { PendingVerificationsPage, VerificationImage } from '../domain/pending-verification';

/** Lo que se manda al rechazar. Es de esta capa y no sale de ella. */
interface RejectRequestDto {
  readonly reason: RejectionReason;
  readonly note: string | null;
}

/**
 * El motivo que se anota en la bitácora al abrir una imagen.
 *
 * <p>Criterio 6: la interfaz lo manda siempre y el moderador no lo escribe. HU-002 exige
 * que todo acceso quede registrado «con actor y motivo», y pedirle a quien revisa que
 * teclee lo mismo veinte veces al día acabaría en un campo relleno de puntos.
 *
 * <p>No es texto de pantalla y por eso no lleva clave de Transloco: no lo ve nadie más
 * que quien lea la bitácora, y ahí importa que sea estable, no que esté traducido.
 */
const MOTIVO_DE_LECTURA = 'Revision de solicitud pendiente';

/**
 * Adaptador HTTP de la bandeja del moderador. HU-006.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p>Los cinco endpoints ya existían con HU-002. Aquí se usan cuatro: la revocación
 * queda fuera de esta historia porque actúa sobre una verificación ya aprobada y la
 * bandeja solo devuelve lo pendiente, así que no hay forma de llegar a ese identificador
 * desde la interfaz.
 */
@Injectable({ providedIn: 'root' })
export class VerificationReviewApi {
  private readonly http = inject(HttpClient);

  /**
   * La bandeja. El servidor acota el tamaño a 50 y ordena por antigüedad.
   *
   * <p><strong>`page` y `size`, no `limite`.</strong> El parámetro se llamaba en español
   * y no había desplazamiento, así que esta ruta era una excepción al contrato y no había
   * forma de pasar de las primeras veinte. Son los mismos nombres que usa la cola de
   * publicaciones.
   */
  async pendientes(pagina = 0, tamano = 20): Promise<PendingVerificationsPage> {
    const parametros = new HttpParams().set('page', pagina).set('size', tamano);

    return firstValueFrom(
      this.http.get<PendingVerificationsPage>('verifications', { params: parametros }),
    );
  }

  /**
   * Una imagen, como bytes.
   *
   * <p><strong>Bytes y no una dirección, tampoco firmada.</strong> Es lo que permite
   * saber quién miró: un enlace que funciona por sí solo no puede registrar quién lo usó
   * (ADR-0018, RN-046). Cada llamada aquí deja una fila en la bitácora, y por eso la
   * pantalla pide las imágenes de una en una, al abrirlas, y no las tres al cargar.
   *
   * <p>Devuelve un `Blob` y no una URL de objeto: quien la pinte tiene que crearla y
   * revocarla, y ese ciclo de vida es del componente. Dejarlo aquí filtraría memoria en
   * cada solicitud revisada.
   */
  async imagen(id: string, cual: VerificationImage): Promise<Blob> {
    return firstValueFrom(
      this.http.get(`verifications/${id}/images/${cual}`, {
        responseType: 'blob',
        params: { motivo: MOTIVO_DE_LECTURA },
      }),
    );
  }

  async aprobar(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`verifications/${id}/approval`, {}));
  }

  async rechazar(id: string, motivo: RejectionReason, nota: string | null): Promise<void> {
    const cuerpo: RejectRequestDto = { reason: motivo, note: nota };

    await firstValueFrom(this.http.post(`verifications/${id}/rejection`, cuerpo));
  }
}
