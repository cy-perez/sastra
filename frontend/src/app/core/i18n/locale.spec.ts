import { describe, expect, it } from 'vitest';

import {
  buildLocaleCookie,
  matchLocale,
  parseAcceptLanguage,
  parseCookies,
  resolveLocale,
} from './locale';

const AVAILABLE = ['es', 'en'];

describe('resolveLocale', () => {
  it('cae en el idioma por omision cuando no hay ninguna pista', () => {
    expect(resolveLocale({ availableLocales: AVAILABLE, defaultLocale: 'es' })).toBe('es');
  });

  it('sigue la cabecera del navegador', () => {
    const locale = resolveLocale({
      acceptLanguage: 'en-US,en;q=0.9',
      availableLocales: AVAILABLE,
      defaultLocale: 'es',
    });

    expect(locale).toBe('en');
  });

  // Lo que la persona eligio manda sobre lo que su navegador prefiere: si
  // fuera al reves, cambiar de idioma no serviria de nada.
  it('la eleccion guardada gana sobre la cabecera del navegador', () => {
    const locale = resolveLocale({
      cookieHeader: 'sastra_locale=es',
      acceptLanguage: 'en-US,en;q=0.9',
      availableLocales: AVAILABLE,
      defaultLocale: 'en',
    });

    expect(locale).toBe('es');
  });

  it('ignora una cookie con un idioma que no se ofrece', () => {
    const locale = resolveLocale({
      cookieHeader: 'sastra_locale=pt',
      acceptLanguage: 'en',
      availableLocales: AVAILABLE,
      defaultLocale: 'es',
    });

    expect(locale).toBe('en');
  });

  it('respeta el orden de calidad y no el orden de escritura', () => {
    const locale = resolveLocale({
      acceptLanguage: 'de;q=0.2,en;q=0.9,fr;q=0.8',
      availableLocales: AVAILABLE,
      defaultLocale: 'es',
    });

    expect(locale).toBe('en');
  });

  it('reduce una variante regional a su idioma principal', () => {
    const locale = resolveLocale({
      acceptLanguage: 'es-CO',
      availableLocales: AVAILABLE,
      defaultLocale: 'en',
    });

    expect(locale).toBe('es');
  });
});

describe('parseAcceptLanguage', () => {
  it('descarta las etiquetas rechazadas con q=0', () => {
    expect(parseAcceptLanguage('en;q=0,es')).toEqual(['es']);
  });

  // Una cabecera mal formada es una preferencia, no una regla de negocio: se
  // degrada al final de la lista en vez de tumbar la peticion.
  it('trata una calidad ilegible como la menos preferida', () => {
    expect(parseAcceptLanguage('en;q=abc,es;q=0.5')).toEqual(['es']);
  });

  it('devuelve una lista vacia si no hay cabecera', () => {
    expect(parseAcceptLanguage(null)).toEqual([]);
    expect(parseAcceptLanguage('')).toEqual([]);
  });
});

describe('parseCookies', () => {
  it('lee varias cookies y decodifica el valor', () => {
    expect(parseCookies('a=1; sastra_locale=es; b=hola%20mundo')).toEqual({
      a: '1',
      sastra_locale: 'es',
      b: 'hola mundo',
    });
  });

  it('ignora los fragmentos sin nombre o sin igual', () => {
    expect(parseCookies('; =valor; suelto; a=1')).toEqual({ a: '1' });
  });
});

describe('matchLocale', () => {
  it('no distingue mayusculas', () => {
    expect(matchLocale('ES-co', AVAILABLE)).toBe('es');
  });

  it('devuelve nulo cuando no hay coincidencia', () => {
    expect(matchLocale('pt', AVAILABLE)).toBeNull();
    expect(matchLocale(undefined, AVAILABLE)).toBeNull();
  });
});

describe('buildLocaleCookie', () => {
  it('limita la cookie al sitio y le da un ano de vida', () => {
    const cookie = buildLocaleCookie('en');

    expect(cookie).toContain('sastra_locale=en');
    expect(cookie).toContain('Path=/');
    expect(cookie).toContain('SameSite=Lax');
    expect(cookie).toContain('Max-Age=31536000');
  });
});
