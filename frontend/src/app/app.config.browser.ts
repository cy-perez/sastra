import {
  type ApplicationConfig,
  inject,
  mergeApplicationConfig,
  provideAppInitializer,
} from '@angular/core';
import { provideClientHydration, withEventReplay } from '@angular/platform-browser';

import { appConfig } from './app.config';
import { provideAppConfigFromTransferState } from './core/config/app-config.providers';
import { provideActiveLocaleFromDocument, provideI18n } from './core/i18n/i18n.providers';
import { HttpTranslationLoader } from './core/i18n/translation-loaders';
import { SessionRecovery } from './core/session/session-recovery';
import { ApiError } from './core/http/api-error';
import { SessionStore } from './core/session/session.store';

const browserConfig: ApplicationConfig = {
  providers: [
    provideClientHydration(withEventReplay()),
    provideAppConfigFromTransferState(),
    provideActiveLocaleFromDocument(),
    provideI18n(HttpTranslationLoader),

    /**
     * Recupera la sesion al cargar la pagina.
     *
     * El token de acceso vive en memoria y se pierde en cada recarga; la cookie
     * de refresco no. Sin esto, recargar equivaldria a cerrar sesion y los 30
     * dias del token de refresco no servirian de nada.
     *
     * No se espera al resultado a proposito: bloquear el arranque en una
     * peticion de red retrasaria la hidratacion tambien para quien visita sin
     * cuenta. Mientras llega, la interfaz se pinta como anonima y cambia sola
     * cuando la senal de sesion se actualiza.
     *
     * El coste es una peticion que devuelve 401 en la primera visita de quien no
     * tiene cuenta. Se evitaria si el servidor dejara una cookie legible que
     * dijera "aqui hay sesion", y esa cookie no existe todavia: la de refresco
     * es HttpOnly justamente para que el JavaScript no la vea.
     */
    provideAppInitializer(() => {
      const sesion = inject(SessionStore);

      inject(SessionRecovery)
        .recuperar()
        .subscribe({
          // No hay sesion que recuperar, que es lo normal. Se marca como resuelta
          // igualmente: es lo que saca a la cabecera del estado de espera y le
          // permite pintar "Entrar" sabiendo que es verdad. El interceptor ya se
          // encarga si mas adelante alguna peticion recibe un 401.
          //
          // **Salvo si es la carrera del refresco.** Este refresco no pasa por
          // `refreshInterceptor` -que se excluye a si mismo de las rutas de sesion-
          // asi que aqui hay que distinguir a mano. Y es justo el caso que ADR-0030
          // pone de ejemplo: dos pestanas que arrancan a la vez llaman las dos a
          // `auth/refresh` desde este inicializador. Cerrar la sesion aqui seria el
          // mismo defecto por la unica puerta que quedaba sin arreglar.
          //
          // **No se reintenta, se deja pasar.** El reintento vive en el interceptor,
          // que corre sobre peticiones de verdad; aqui no hay ninguna que rehacer. La
          // sesion se queda como estaba y la resuelve el primer 401 que llegue.
          error: (fallo: unknown) => {
            if (fallo instanceof ApiError && fallo.code === 'AUTH_SESSION_RACE') {
              return;
            }
            sesion.clear();
          },
        });
    }),
  ],
};

export const config = mergeApplicationConfig(appConfig, browserConfig);
