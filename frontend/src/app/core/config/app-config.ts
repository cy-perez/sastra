import { InjectionToken, makeStateKey } from '@angular/core';

/**
 * Configuracion que el servidor entrega en cada peticion. No se compila dentro
 * del paquete: el mismo artefacto sirve para dev y para prod, y cambiar la URL
 * de la API no exige volver a construir. Ver docs/operacion/configuracion.md.
 */
export interface AppConfig {
  /** Base de la API, incluida la version. Ejemplo: https://api.sastra.co/api/v1 */
  readonly apiBaseUrl: string;
  readonly defaultLocale: string;
  readonly availableLocales: readonly string[];
  readonly enableDevtools: boolean;
  readonly sentryDsn: string | null;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('sastra.app-config');

/**
 * Clave con la que la configuracion viaja del servidor al navegador. Solo lleva
 * valores publicos: nada de aqui es secreto ni personal.
 */
export const APP_CONFIG_STATE_KEY = makeStateKey<AppConfig>('sastra.app-config');
