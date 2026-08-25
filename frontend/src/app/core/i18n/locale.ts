/**
 * Resolucion del idioma. Todo lo de este archivo es TypeScript puro: se ejecuta
 * en el servidor antes de pintar, y se prueba sin TestBed.
 */

/** Se guarda en cookie y no en localStorage porque el servidor tiene que poder leerlo. */
export const LOCALE_COOKIE = 'sendik_locale';

/** Un ano: la eleccion de idioma no deberia caducar en mitad de una visita. */
export const LOCALE_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export interface LocaleResolution {
  readonly cookieHeader?: string | null;
  readonly acceptLanguage?: string | null;
  readonly availableLocales: readonly string[];
  readonly defaultLocale: string;
}

/**
 * Orden de preferencia: lo que la persona eligio, luego lo que pide su
 * navegador, y al final el idioma por omision. Se compara solo la parte
 * principal de la etiqueta, de modo que "es-CO" y "es-419" resuelven a "es".
 */
export function resolveLocale(resolution: LocaleResolution): string {
  const { cookieHeader, acceptLanguage, availableLocales, defaultLocale } = resolution;

  const chosen = parseCookies(cookieHeader)[LOCALE_COOKIE];
  const fromCookie = matchLocale(chosen, availableLocales);
  if (fromCookie !== null) {
    return fromCookie;
  }

  for (const tag of parseAcceptLanguage(acceptLanguage)) {
    const match = matchLocale(tag, availableLocales);
    if (match !== null) {
      return match;
    }
  }

  return defaultLocale;
}

export function parseCookies(header: string | null | undefined): Readonly<Record<string, string>> {
  const cookies: Record<string, string> = {};
  if (!header) {
    return cookies;
  }

  for (const part of header.split(';')) {
    const separator = part.indexOf('=');
    if (separator < 1) {
      continue;
    }
    const name = part.slice(0, separator).trim();
    const value = part.slice(separator + 1).trim();
    if (name.length > 0) {
      cookies[name] = decodeURIComponent(value);
    }
  }
  return cookies;
}

/**
 * Devuelve las etiquetas de Accept-Language ordenadas por calidad descendente.
 * Un valor de q ilegible se trata como el menos preferido en vez de tumbar la
 * peticion: el idioma es una preferencia, no una regla de negocio.
 */
export function parseAcceptLanguage(header: string | null | undefined): readonly string[] {
  if (!header) {
    return [];
  }

  return header
    .split(',')
    .map((entry) => {
      const [tag, ...parameters] = entry.split(';');
      const quality = parameters
        .map((parameter) => parameter.trim())
        .find((parameter) => parameter.startsWith('q='));
      const parsed = quality === undefined ? 1 : Number.parseFloat(quality.slice(2));
      return { tag: (tag ?? '').trim(), quality: Number.isFinite(parsed) ? parsed : 0 };
    })
    .filter((entry) => entry.tag.length > 0 && entry.quality > 0)
    .sort((a, b) => b.quality - a.quality)
    .map((entry) => entry.tag);
}

/** "es-CO" encaja con "es". Devuelve null si no hay coincidencia. */
export function matchLocale(
  tag: string | undefined,
  availableLocales: readonly string[],
): string | null {
  if (!tag) {
    return null;
  }
  const normalized = tag.toLowerCase();
  const primary = normalized.split('-')[0] ?? normalized;

  return (
    availableLocales.find((locale) => locale.toLowerCase() === normalized) ??
    availableLocales.find((locale) => locale.toLowerCase() === primary) ??
    null
  );
}

/** Cookie de primera parte, sin datos personales: no necesita consentimiento previo. */
export function buildLocaleCookie(locale: string): string {
  return (
    `${LOCALE_COOKIE}=${encodeURIComponent(locale)}; Path=/; ` +
    `Max-Age=${LOCALE_COOKIE_MAX_AGE_SECONDS}; SameSite=Lax`
  );
}
