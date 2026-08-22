import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import type {
  BankAccountType,
  FinancialInstitution,
  IdentityDocumentType,
  SellerVerification,
} from '../domain/verification';

/**
 * Lo que se manda al registrar la cuenta bancaria. Es de esta capa y no sale de ella:
 * la plantilla ve modelos de dominio, nunca un DTO (frontend/CLAUDE.md).
 */
interface BankAccountRequestDto {
  readonly bank: string;
  readonly accountType: BankAccountType;
  readonly accountNumber: string;
  readonly holderName: string;
}

/** Lo que se manda con el documento, sin los archivos. */
export interface DatosDelDocumento {
  readonly tipo: IdentityDocumentType;
  readonly numero: string;
  readonly titular: string;
}

/** Lo que se manda con la cuenta. */
export interface DatosDeLaCuenta {
  readonly entidad: string;
  readonly tipo: BankAccountType;
  readonly numero: string;
  readonly titular: string;
}

/**
 * Adaptador HTTP de la verificación de vendedor.
 *
 * Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * Las imágenes viajan como `multipart` y no en base64 dentro de un JSON: en base64
 * ocupan un tercio más y obligan a tener cada imagen en memoria dos veces.
 */
@Injectable({ providedIn: 'root' })
export class SellerVerificationApi {
  private readonly http = inject(HttpClient);

  /** El catálogo de entidades activas, para el desplegable del formulario. */
  async entidades(): Promise<readonly FinancialInstitution[]> {
    return firstValueFrom(this.http.get<FinancialInstitution[]>('financial-institutions'));
  }

  /** El estado propio. Responde 404 mientras no se haya empezado. */
  async estado(): Promise<SellerVerification> {
    return firstValueFrom(this.http.get<SellerVerification>('users/me/verification'));
  }

  /** Idempotente: quien ya lo tenía empezado recibe lo que llevaba. */
  async iniciar(): Promise<SellerVerification> {
    return firstValueFrom(this.http.post<SellerVerification>('users/me/verification', {}));
  }

  async entregarDocumento(
    datos: DatosDelDocumento,
    frente: Blob,
    reverso: Blob,
  ): Promise<SellerVerification> {
    const cuerpo = new FormData();
    cuerpo.append('tipo', datos.tipo);
    cuerpo.append('numero', datos.numero);
    cuerpo.append('titular', datos.titular);
    // Se nombran para que el servidor los reciba como archivo y no como texto. El
    // nombre no decide nada: el tipo se detecta por los bytes de cabecera (ADR-0018).
    cuerpo.append('frente', frente, 'frente');
    cuerpo.append('reverso', reverso, 'reverso');

    return firstValueFrom(
      this.http.put<SellerVerification>('users/me/verification/document', cuerpo),
    );
  }

  async entregarSelfie(imagen: Blob): Promise<SellerVerification> {
    const cuerpo = new FormData();
    cuerpo.append('archivo', imagen, 'selfie');

    return firstValueFrom(
      this.http.put<SellerVerification>('users/me/verification/selfie', cuerpo),
    );
  }

  async registrarCuenta(datos: DatosDeLaCuenta): Promise<SellerVerification> {
    const cuerpo: BankAccountRequestDto = {
      bank: datos.entidad,
      accountType: datos.tipo,
      accountNumber: datos.numero,
      holderName: datos.titular,
    };

    return firstValueFrom(
      this.http.put<SellerVerification>('users/me/verification/bank-account', cuerpo),
    );
  }

  async enviarARevision(): Promise<SellerVerification> {
    return firstValueFrom(
      this.http.post<SellerVerification>('users/me/verification/submission', {}),
    );
  }
}
