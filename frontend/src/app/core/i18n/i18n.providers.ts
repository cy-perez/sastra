import {
  DOCUMENT,
  EnvironmentProviders,
  inject,
  InjectionToken,
  isDevMode,
  makeEnvironmentProviders,
  provideAppInitializer,
  type Type,
} from '@angular/core';
import {
  provideTransloco,
  TRANSLOCO_CONFIG,
  translocoConfig,
  TranslocoService,
  type TranslocoLoader,
} from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { APP_CONFIG } from '../config/app-config';

/**
 * Idioma con el que se atiende esta ejecucion. Lo resuelve cada plataforma: el
 * servidor a partir de las cabeceras de la peticion, el navegador a partir del
 * atributo lang que el servidor ya escribio en el HTML.
 *
 * No se usa TRANSLOCO_LANG para esto: ese token es una anulacion local para un
 * subarbol de componentes, y el servicio sigue con el idioma por omision. El
 * resultado seria una pagina con el cuerpo traducido y el titulo, la
 * descripcion y el atributo lang en otro idioma.
 */
export const ACTIVE_LOCALE = new InjectionToken<string>('sastra.active-locale');

/**
 * Configuracion comun de Transloco. Los idiomas no estan escritos aqui: salen
 * de la configuracion que entrega el servidor, para que agregar un idioma sea
 * una variable de entorno y no un despliegue de codigo.
 */
export function provideI18n(loader: Type<TranslocoLoader>): EnvironmentProviders {
  return makeEnvironmentProviders([
    // Registra el cargador y las piezas internas de Transloco: transpilador,
    // manejador de claves ausentes y estrategia de reserva.
    provideTransloco({ loader, config: {} }),
    {
      // Sustituye la configuracion estatica de arriba por una que sale de la
      // configuracion de ejecucion.
      provide: TRANSLOCO_CONFIG,
      useFactory: () => {
        const config = inject(APP_CONFIG);
        return translocoConfig({
          availableLangs: [...config.availableLocales],
          defaultLang: config.defaultLocale,
          fallbackLang: config.defaultLocale,
          reRenderOnLangChange: true,
          prodMode: !isDevMode(),
        });
      },
    },
    // Se fija el idioma activo y se esperan sus traducciones antes de la primera
    // pintura. Sin esto, el servidor entregaria el HTML con las claves crudas.
    provideAppInitializer(() => {
      const transloco = inject(TranslocoService);
      const locale = inject(ACTIVE_LOCALE);

      transloco.setActiveLang(locale);
      return firstValueFrom(transloco.load(locale));
    }),
  ]);
}

/**
 * En el navegador el idioma ya viene decidido en el atributo lang del documento
 * que envio el servidor. Volver a deducirlo aqui abriria la puerta a que las dos
 * plataformas discrepen y la hidratacion reescriba toda la pagina.
 */
export function provideActiveLocaleFromDocument(): EnvironmentProviders {
  return makeEnvironmentProviders([
    {
      provide: ACTIVE_LOCALE,
      useFactory: () => {
        const config = inject(APP_CONFIG);
        const declared = inject(DOCUMENT).documentElement.lang;
        return config.availableLocales.includes(declared) ? declared : config.defaultLocale;
      },
    },
  ]);
}
