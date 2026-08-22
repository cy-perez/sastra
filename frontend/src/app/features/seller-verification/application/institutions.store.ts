import { inject, Injectable } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { SessionStore } from '../../../core/session/session.store';
import { SellerVerificationApi } from '../infrastructure/seller-verification.api';
import { queryKeys } from './query-keys';

/**
 * El catálogo de entidades financieras.
 *
 * <p><strong>Aparte de `VerificationStore` a propósito.</strong> Una consulta declarada
 * como campo se dispara en cuanto alguien instancia la clase que la contiene, así que
 * tenerla junto al estado de la solicitud hacía que la pantalla de progreso pidiera
 * veintiocho nombres de bancos que no usa. Lo descubrió una prueba que se quedó esperando
 * una petición que nadie iba a responder.
 *
 * <p>A diferencia del estado de la solicitud, esto **sí** se cachea: son nombres de
 * bancos que no cambian en la sesión de nadie, y agregar uno es una migración. Una hora
 * es de sobra.
 */
@Injectable({ providedIn: 'root' })
export class InstitutionsStore {
  private readonly api = inject(SellerVerificationApi);
  private readonly sesion = inject(SessionStore);

  readonly institutions = injectQuery(() => ({
    queryKey: queryKeys.institutions,
    queryFn: () => this.api.entidades(),
    staleTime: 60 * 60 * 1000,
    retry: false,
    enabled: this.sesion.isAuthenticated(),
  }));
}
