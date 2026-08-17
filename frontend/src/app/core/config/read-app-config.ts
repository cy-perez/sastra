import type { AppConfig } from './app-config';

/** Solo la parte del entorno que nos interesa: asi la funcion es pura y se prueba sin Node. */
export type EnvironmentVariables = Readonly<Record<string, string | undefined>>;

const FALLBACK_DEFAULT_LOCALE = 'es';
const FALLBACK_AVAILABLE_LOCALES = 'es,en';

/**
 * Lee la configuracion del entorno y falla al arrancar si algo obligatorio no
 * esta. Es preferible no levantar el servidor a descubrirlo con un visitante
 * dentro, que es la misma regla que sigue el backend.
 */
export function readAppConfig(env: EnvironmentVariables): AppConfig {
  const apiBaseUrl = trimTrailingSlash(requireValue(env, 'API_BASE_URL'));

  const defaultLocale = (env['DEFAULT_LOCALE'] ?? FALLBACK_DEFAULT_LOCALE).trim();
  const availableLocales = (env['AVAILABLE_LOCALES'] ?? FALLBACK_AVAILABLE_LOCALES)
    .split(',')
    .map((locale) => locale.trim())
    .filter((locale) => locale.length > 0);

  if (availableLocales.length === 0) {
    throw new Error('AVAILABLE_LOCALES no puede quedar vacia.');
  }
  if (!availableLocales.includes(defaultLocale)) {
    throw new Error(
      `DEFAULT_LOCALE es "${defaultLocale}" pero no aparece en AVAILABLE_LOCALES ` +
        `(${availableLocales.join(', ')}). Un idioma por omision que no existe deja el sitio sin texto.`,
    );
  }

  const sentryDsn = env['SENTRY_DSN']?.trim();

  return {
    apiBaseUrl,
    defaultLocale,
    availableLocales,
    enableDevtools: env['ENABLE_DEVTOOLS']?.trim().toLowerCase() === 'true',
    sentryDsn: sentryDsn && sentryDsn.length > 0 ? sentryDsn : null,
  };
}

/**
 * Comprobacion aparte porque esta variable no la lee la aplicacion sino
 * @angular/ssr: es la lista de nombres de dominio a los que el servidor acepta
 * responder, y protege contra falsificacion de peticiones del lado del servidor.
 *
 * Se valida al arrancar porque, si falta, Angular no lanza: registra un aviso y
 * entrega la pagina sin renderizar, para que la pinte el navegador. El sitio
 * parece funcionar mientras el buscador y la vista previa de WhatsApp reciben un
 * documento vacio, que es justo lo que ADR-0006 existe para impedir.
 */
export function assertRenderingEnvironment(env: EnvironmentVariables): void {
  requireValue(env, 'NG_ALLOWED_HOSTS');
}

/**
 * Variante que nunca lanza.
 *
 * Al construir, Angular arranca la aplicacion en un entorno vacio para extraer
 * las rutas, y ahi no existe ninguna variable. Fallar en ese momento impediria
 * compilar en una maquina de integracion continua, que es precisamente donde no
 * hay ni debe haber configuracion de ejecucion.
 *
 * La validacion de verdad ocurre al arrancar el servidor, en src/server.ts, que
 * es el momento en el que fallar sirve de algo: antes de atender a nadie. Si
 * pese a todo se llegara a renderizar con esta configuracion de relleno, la
 * primera peticion a la API falla con un mensaje explicito y no en silencio.
 */
export function readAppConfigForBootstrap(env: EnvironmentVariables): AppConfig {
  try {
    return readAppConfig(env);
  } catch {
    return {
      apiBaseUrl: '',
      defaultLocale: FALLBACK_DEFAULT_LOCALE,
      availableLocales: FALLBACK_AVAILABLE_LOCALES.split(','),
      enableDevtools: false,
      sentryDsn: null,
    };
  }
}

function requireValue(env: EnvironmentVariables, name: string): string {
  const value = env[name]?.trim();
  if (!value) {
    throw new Error(
      `Falta la variable de entorno ${name}. Las claves del frontend estan en ` +
        'docs/operacion/configuracion.md y en .env.example.',
    );
  }
  return value;
}

/** Evita que una barra final duplique la barra que pone el interceptor de la API. */
function trimTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}
