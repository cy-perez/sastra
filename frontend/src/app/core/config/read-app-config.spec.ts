import { describe, expect, it } from 'vitest';

import {
  assertRenderingEnvironment,
  readAppConfig,
  readAppConfigForBootstrap,
} from './read-app-config';

const MINIMUM = { API_BASE_URL: 'https://api.sastra.co/api/v1' };

describe('readAppConfig', () => {
  it('aplica los valores por omision cuando solo esta lo obligatorio', () => {
    const config = readAppConfig(MINIMUM);

    expect(config.apiBaseUrl).toBe('https://api.sastra.co/api/v1');
    expect(config.defaultLocale).toBe('es');
    expect(config.availableLocales).toEqual(['es', 'en']);
    expect(config.enableDevtools).toBe(false);
    expect(config.sentryDsn).toBeNull();
  });

  it('no arranca si falta la direccion de la API', () => {
    expect(() => readAppConfig({})).toThrowError(/API_BASE_URL/);
  });

  it('trata una variable en blanco como ausente', () => {
    expect(() => readAppConfig({ API_BASE_URL: '   ' })).toThrowError(/API_BASE_URL/);
  });

  // Una barra de mas produce //api/v1, que en muchos servidores es un 404 y
  // cuesta una tarde encontrar.
  it('quita la barra final de la direccion de la API', () => {
    const config = readAppConfig({ API_BASE_URL: 'https://api.sastra.co/api/v1/' });

    expect(config.apiBaseUrl).toBe('https://api.sastra.co/api/v1');
  });

  it('recorta los espacios de la lista de idiomas', () => {
    const config = readAppConfig({ ...MINIMUM, AVAILABLE_LOCALES: ' es , en ' });

    expect(config.availableLocales).toEqual(['es', 'en']);
  });

  it('rechaza un idioma por omision que no este entre los disponibles', () => {
    expect(() =>
      readAppConfig({ ...MINIMUM, DEFAULT_LOCALE: 'pt', AVAILABLE_LOCALES: 'es,en' }),
    ).toThrowError(/DEFAULT_LOCALE/);
  });

  it('rechaza una lista de idiomas vacia', () => {
    expect(() => readAppConfig({ ...MINIMUM, AVAILABLE_LOCALES: ' , ' })).toThrowError(
      /AVAILABLE_LOCALES/,
    );
  });

  it('solo activa las herramientas de desarrollo con el valor true', () => {
    expect(readAppConfig({ ...MINIMUM, ENABLE_DEVTOOLS: 'TRUE' }).enableDevtools).toBe(true);
    expect(readAppConfig({ ...MINIMUM, ENABLE_DEVTOOLS: '1' }).enableDevtools).toBe(false);
    expect(readAppConfig({ ...MINIMUM, ENABLE_DEVTOOLS: 'false' }).enableDevtools).toBe(false);
  });

  it('deja el DSN de Sentry en nulo si viene vacio', () => {
    expect(readAppConfig({ ...MINIMUM, SENTRY_DSN: '  ' }).sentryDsn).toBeNull();
    expect(readAppConfig({ ...MINIMUM, SENTRY_DSN: 'https://dsn' }).sentryDsn).toBe('https://dsn');
  });
});

describe('readAppConfigForBootstrap', () => {
  // Angular arranca la aplicacion al construir para extraer las rutas, y ahi no
  // hay entorno. Si esto lanzara, no se podria compilar en integracion continua.
  it('devuelve una configuracion de relleno en vez de lanzar', () => {
    const config = readAppConfigForBootstrap({});

    expect(config.apiBaseUrl).toBe('');
    expect(config.availableLocales).toEqual(['es', 'en']);
  });

  it('usa el entorno real cuando esta completo', () => {
    expect(readAppConfigForBootstrap(MINIMUM).apiBaseUrl).toBe('https://api.sastra.co/api/v1');
  });
});

describe('assertRenderingEnvironment', () => {
  // Sin esta variable Angular no lanza: sirve la pagina sin renderizar. El sitio
  // parece funcionar y el buscador recibe un documento vacio.
  it('exige la lista de dominios permitidos', () => {
    expect(() => assertRenderingEnvironment({})).toThrowError(/NG_ALLOWED_HOSTS/);
  });

  it('pasa cuando esta declarada', () => {
    expect(() => assertRenderingEnvironment({ NG_ALLOWED_HOSTS: 'sastra.co' })).not.toThrow();
  });
});
