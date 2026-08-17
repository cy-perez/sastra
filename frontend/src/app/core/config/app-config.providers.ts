import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideEnvironmentInitializer,
  TransferState,
} from '@angular/core';
import { APP_CONFIG, APP_CONFIG_STATE_KEY, type AppConfig } from './app-config';

/**
 * En el servidor: se toma la configuracion ya leida del entorno y se deja en el
 * estado transferido para que el navegador la reciba dentro del mismo HTML, sin
 * una peticion adicional ni un parpadeo de "todavia no se cual es la API".
 */
export function provideAppConfig(config: AppConfig): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: APP_CONFIG, useValue: config },
    provideEnvironmentInitializer(() => {
      inject(TransferState).set(APP_CONFIG_STATE_KEY, config);
    }),
  ]);
}

/**
 * En el navegador: se lee del estado transferido. Si no llego, algo se rompio
 * en el renderizado del servidor y es mejor saberlo de inmediato que arrastrar
 * una URL de API vacia hasta la primera peticion.
 */
export function provideAppConfigFromTransferState(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: APP_CONFIG,
      useFactory: (): AppConfig => {
        const config = inject(TransferState).get(APP_CONFIG_STATE_KEY, null);
        if (config === null) {
          throw new Error(
            'La configuracion no llego en el estado transferido. La aplicacion se sirve ' +
              'siempre con renderizado en servidor: revisa src/server.ts.',
          );
        }
        return config;
      },
    },
  ]);
}
