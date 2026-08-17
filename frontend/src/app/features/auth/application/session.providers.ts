import { type EnvironmentProviders, inject, makeEnvironmentProviders } from '@angular/core';
import { from, map, tap } from 'rxjs';

import { SESSION_REFRESHER, type SessionRefresher } from '../../../core/session/session-recovery';
import { SessionStore } from '../../../core/session/session.store';
import { AuthApi } from '../infrastructure/auth.api';

/**
 * Conecta el puerto de renovacion de `core` con el adaptador de cuentas.
 *
 * <p>Es el unico punto donde las dos partes se encuentran, y esta aqui y no en
 * `core` para que la flecha apunte en la direccion correcta: la funcionalidad
 * conoce a `core`, nunca al reves.
 */
export function provideAuthSession(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: SESSION_REFRESHER,
      useFactory: (): SessionRefresher => {
        const api = inject(AuthApi);
        const sesion = inject(SessionStore);

        // La sesion queda guardada antes de completar: quien esperaba el
        // refresco reintenta despues, y para entonces el token nuevo ya esta.
        return () =>
          from(api.refresh()).pipe(
            tap((renovada) => sesion.set(renovada)),
            map(() => undefined),
          );
      },
    },
  ]);
}
