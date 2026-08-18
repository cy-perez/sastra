import { InjectionToken, makeStateKey } from '@angular/core';

/**
 * Configuracion que el servidor entrega en cada peticion. No se compila dentro
 * del paquete: el mismo artefacto sirve para dev y para prod, y cambiar la URL
 * de la API no exige volver a construir. Ver docs/operacion/configuracion.md.
 */
/**
 * Version vigente de cada documento legal.
 *
 * <p>Las de terminos y tratamiento tienen que valer <strong>lo mismo</strong> que
 * las del backend, que es quien las guarda como evidencia del consentimiento.
 * Salen de las mismas variables de entorno por eso: si el texto que se muestra y
 * el que quedo escrito no son el mismo, la prueba no vale
 * (docs/operacion/datos-personales.md).
 *
 * <p>La de cookies no la conoce el backend porque nadie consiente cookies en un
 * formulario: existe solo para versionar el archivo del texto.
 */
export interface LegalVersions {
  readonly terms: string;
  readonly privacy: string;
  readonly cookies: string;
}

/**
 * Quien responde por el sitio: razon social, NIT, direccion y canal de contacto.
 *
 * <p>En Colombia estos datos en el pie son una senal de que la empresa existe, y
 * el canal de contacto es ademas la via por la que se ejercen los derechos del
 * titular de los datos (docs/operacion/datos-personales.md). Son datos publicos
 * de la empresa: aqui no viaja nada personal de nadie.
 *
 * <p><strong>Todos pueden faltar.</strong> El backend si los exige, porque los
 * necesita para los correos y para la evidencia del consentimiento; el frontend
 * solo los pinta. Tumbar el renderizado entero porque falta una direccion seria
 * cambiar un pie incompleto por un sitio caido.
 */
export interface CompanyInfo {
  readonly name: string | null;
  readonly taxId: string | null;
  readonly address: string | null;
  readonly supportEmail: string | null;
}

export interface AppConfig {
  /** Base de la API, incluida la version. Ejemplo: https://api.sastra.co/api/v1 */
  readonly apiBaseUrl: string;
  readonly defaultLocale: string;
  readonly availableLocales: readonly string[];
  readonly enableDevtools: boolean;
  readonly sentryDsn: string | null;
  readonly legalVersions: LegalVersions;
  readonly company: CompanyInfo;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('sastra.app-config');

/**
 * Clave con la que la configuracion viaja del servidor al navegador. Solo lleva
 * valores publicos: nada de aqui es secreto ni personal.
 */
export const APP_CONFIG_STATE_KEY = makeStateKey<AppConfig>('sastra.app-config');
