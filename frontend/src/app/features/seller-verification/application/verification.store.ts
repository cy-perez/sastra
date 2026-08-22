import { inject, Injectable } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import type { SellerVerification } from '../domain/verification';
import type { DatosDeLaCuenta, DatosDelDocumento } from '../infrastructure/seller-verification.api';
import { SellerVerificationApi } from '../infrastructure/seller-verification.api';
import { queryKeys } from './query-keys';

/**
 * El estado de la verificación de vendedor. HU-002.
 *
 * Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * **Ninguna mutación reintenta, y no es descuido.** Las tres que suben algo mandan
 * imágenes: un reintento automático manda el archivo dos veces y, si el primero sí
 * llegó, deja un archivo huérfano en el almacén reservado. Enviar a revisión tampoco
 * reintenta, y ese es el importante: cada envío gasta un intento de RN-014, así que un
 * reintento automático le quitaría a la persona un intento que no pidió gastar.
 *
 * Cada mutación refresca la consulta con lo que devolvió el servidor en lugar de
 * pedirlo otra vez: es exactamente lo que hay en la base, y evita que el progreso tarde
 * un viaje más en aparecer.
 */
@Injectable({ providedIn: 'root' })
export class VerificationStore {
  private readonly api = inject(SellerVerificationApi);
  private readonly consultas = inject(QueryClient);
  private readonly sesion = inject(SessionStore);

  /**
   * El estado propio.
   *
   * Sin tiempo de frescura: una solicitud que el moderador acaba de aprobar tiene que
   * aparecer al mirar, no un minuto después.
   *
   * Sin reintentos porque el 404 es una respuesta normal aquí —significa «no has
   * empezado»— y reintentar tres veces un 404 solo retrasa la pantalla.
   *
   * La señal de sesión se lee **aquí y no dentro de la función**: TanStack invoca las
   * opciones fuera del ámbito reactivo, así que leerla dentro nacería deshabilitada y no
   * se reactivaría nunca. Es el fallo que dejó `/mi-cuenta` sin cargar
   * (frontend/CLAUDE.md).
   */
  readonly verification = injectQuery(() => ({
    queryKey: queryKeys.verification,
    queryFn: () => this.api.estado(),
    staleTime: 0,
    retry: false,
    enabled: this.sesion.isAuthenticated(),
  }));

  readonly start = injectMutation(() => ({
    mutationFn: () => this.api.iniciar(),
    retry: false,
    onSuccess: (estado: SellerVerification) => this.refrescar(estado),
  }));

  readonly documentSubmission = injectMutation(() => ({
    mutationFn: (envio: { datos: DatosDelDocumento; frente: Blob; reverso: Blob }) =>
      this.api.entregarDocumento(envio.datos, envio.frente, envio.reverso),
    retry: false,
    onSuccess: (estado: SellerVerification) => this.refrescar(estado),
  }));

  readonly selfieSubmission = injectMutation(() => ({
    mutationFn: (imagen: Blob) => this.api.entregarSelfie(imagen),
    retry: false,
    onSuccess: (estado: SellerVerification) => this.refrescar(estado),
  }));

  readonly bankAccountSubmission = injectMutation(() => ({
    mutationFn: (datos: DatosDeLaCuenta) => this.api.registrarCuenta(datos),
    retry: false,
    onSuccess: (estado: SellerVerification) => this.refrescar(estado),
  }));

  /** Gasta un intento de RN-014, así que nunca reintenta solo. */
  readonly review = injectMutation(() => ({
    mutationFn: () => this.api.enviarARevision(),
    retry: false,
    onSuccess: (estado: SellerVerification) => this.refrescar(estado),
  }));

  /**
   * La clave de traducción de un fallo.
   *
   * Delega en {@link ApiError}, que es quien sabe leer el `code` del cuerpo de error:
   * escribir aquí otro acceso a `error.code` daría dos sitios donde arreglarlo el día
   * que el contrato de error cambie.
   */
  static claveDeError(fallo: unknown): string {
    return fallo instanceof ApiError ? fallo.translationKey : 'errors.fallback';
  }

  private refrescar(estado: SellerVerification): void {
    this.consultas.setQueryData(queryKeys.verification, estado);
  }
}
