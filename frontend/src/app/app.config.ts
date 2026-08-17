import { type ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import {
  provideRouter,
  TitleStrategy,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from './core/http/interceptors';
import { provideQuery } from './core/query/query.providers';
import { TranslatedTitleStrategy } from './core/seo/translated-title.strategy';

/**
 * Lo comun a las dos plataformas. La configuracion de entorno y el cargador de
 * traducciones cambian segun donde se ejecute la aplicacion y se anaden en
 * app.config.browser.ts y en app.config.server.ts.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      // El token del enlace de verificacion llega como parametro de consulta y
      // entra directo al input() del componente, sin ActivatedRoute de por medio.
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled', anchorScrolling: 'enabled' }),
    ),
    // withFetch es lo que permite que las peticiones hechas durante el
    // renderizado en servidor se transfieran al navegador en vez de repetirse.
    provideHttpClient(
      withFetch(),
      withInterceptors([apiUrlInterceptor, authInterceptor, languageInterceptor, errorInterceptor]),
    ),
    provideQuery(),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
  ],
};
