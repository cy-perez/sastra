import type { AppConfig, CompanyInfo, LegalVersions } from './app-config';

/** Solo la parte del entorno que nos interesa: asi la funcion es pura y se prueba sin Node. */
export type EnvironmentVariables = Readonly<Record<string, string | undefined>>;

const FALLBACK_DEFAULT_LOCALE = 'es';
const FALLBACK_AVAILABLE_LOCALES = 'es,en';

/**
 * El mismo valor por omision que usa el perfil local del backend.
 *
 * No se exige la variable, como si se exige API_BASE_URL, para no romper el
 * arranque en la maquina de quien programa. El riesgo de olvidarla en un
 * despliegue esta cubierto por otro lado: el texto que se sirve con esta version
 * es el borrador, y el borrador dice en su primera linea que no tiene valor
 * legal. Un despliegue que la olvide no falla en silencio, grita.
 */
const VERSION_DE_BORRADOR = 'borrador-local';

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
    legalVersions: leerVersionesLegales(env),
    company: leerEmpresa(env),
  };
}

/**
 * Los datos de la empresa para el pie. Ninguno es obligatorio aqui.
 *
 * <p>Comparten nombre con las variables del backend a proposito: es la misma
 * empresa y no tendria sentido que el pie dijera un NIT y los correos otro.
 */
function leerEmpresa(env: EnvironmentVariables): CompanyInfo {
  return {
    name: opcional(env['COMPANY_NAME']),
    taxId: opcional(env['COMPANY_TAX_ID']),
    address: opcional(env['COMPANY_ADDRESS']),
    supportEmail: opcional(env['SUPPORT_EMAIL']),
  };
}

/** Una variable en blanco es una variable ausente, igual que en el resto del archivo. */
function opcional(valor: string | undefined): string | null {
  const limpio = valor?.trim();
  return limpio && limpio.length > 0 ? limpio : null;
}

/**
 * Lo que falta y no impide arrancar, para que quede dicho en el registro.
 *
 * <p>Ninguno de estos datos tumba el servidor: el pie los omite y el sitio
 * funciona. Pero un despliegue sin ellos es un despliegue a medias y no debe
 * pasar en silencio, que es justo como se llega a produccion sin NIT en el pie.
 *
 * <p>{@code SUPPORT_EMAIL} es el que mas pesa: sin el no hay canal visible para
 * ejercer los derechos del titular de los datos y se incumple la Ley 1581
 * (docs/operacion/datos-personales.md).
 *
 * <p>Devuelve los avisos en vez de escribirlos para que la funcion siga siendo
 * pura y se pueda probar sin capturar la consola. Quien los escribe es
 * src/server.ts, al arrancar.
 */
export function avisosDeConfiguracion(config: AppConfig): string[] {
  const faltantes: string[] = [];
  const { company } = config;

  if (company.name === null) faltantes.push('COMPANY_NAME');
  if (company.taxId === null) faltantes.push('COMPANY_TAX_ID');
  if (company.address === null) faltantes.push('COMPANY_ADDRESS');
  if (company.supportEmail === null) faltantes.push('SUPPORT_EMAIL');

  if (faltantes.length === 0) {
    return [];
  }

  const aviso =
    `Faltan variables de empresa: ${faltantes.join(', ')}. El pie omitira esos datos. ` +
    'Ver docs/operacion/configuracion.md.';

  return company.supportEmail === null
    ? [
        aviso,
        'Sin SUPPORT_EMAIL el pie no ofrece canal de contacto, y ese canal es ' +
          'obligatorio para ejercer los derechos del titular de los datos ' +
          '(Ley 1581 de 2012, docs/operacion/datos-personales.md).',
      ]
    : [aviso];
}

function leerVersionesLegales(env: EnvironmentVariables): LegalVersions {
  return {
    terms: conValorPorOmision(env['LEGAL_TERMS_VERSION']),
    privacy: conValorPorOmision(env['LEGAL_PRIVACY_VERSION']),
    cookies: conValorPorOmision(env['LEGAL_COOKIES_VERSION']),
  };
}

function conValorPorOmision(valor: string | undefined): string {
  const limpio = valor?.trim();
  return limpio && limpio.length > 0 ? limpio : VERSION_DE_BORRADOR;
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
      legalVersions: {
        terms: VERSION_DE_BORRADOR,
        privacy: VERSION_DE_BORRADOR,
        cookies: VERSION_DE_BORRADOR,
      },
      company: { name: null, taxId: null, address: null, supportEmail: null },
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
