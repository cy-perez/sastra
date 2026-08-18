import { describe, expect, it } from 'vitest';

import {
  assertRenderingEnvironment,
  avisosDeConfiguracion,
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

  /**
   * Sin las variables se cae al borrador, que es la misma version que usa el
   * perfil local del backend. No se exigen para no romper el arranque en la
   * maquina de quien programa: un despliegue que las olvide sirve el texto de
   * borrador, que dice en su primera linea que no tiene valor legal.
   */
  it('cae en la version de borrador si no se declaran las versiones legales', () => {
    const config = readAppConfig(MINIMUM);

    expect(config.legalVersions).toEqual({
      terms: 'borrador-local',
      privacy: 'borrador-local',
      cookies: 'borrador-local',
    });
  });

  /**
   * Las de terminos y tratamiento tienen que valer lo mismo que las del backend,
   * que es quien las guarda como evidencia: por eso salen de las mismas
   * variables de entorno (docs/operacion/datos-personales.md).
   */
  it('toma cada version legal de su variable', () => {
    const config = readAppConfig({
      ...MINIMUM,
      LEGAL_TERMS_VERSION: '2026-08-01',
      LEGAL_PRIVACY_VERSION: '2026-09-15',
      LEGAL_COOKIES_VERSION: '  2026-07-02  ',
    });

    expect(config.legalVersions.terms).toBe('2026-08-01');
    expect(config.legalVersions.privacy).toBe('2026-09-15');
    expect(config.legalVersions.cookies).toBe('2026-07-02');
  });

  it('deja el DSN de Sentry en nulo si viene vacio', () => {
    expect(readAppConfig({ ...MINIMUM, SENTRY_DSN: '  ' }).sentryDsn).toBeNull();
    expect(readAppConfig({ ...MINIMUM, SENTRY_DSN: 'https://dsn' }).sentryDsn).toBe('https://dsn');
  });

  /**
   * HU-004, criterio 11: el pie los muestra tomados de aqui, nunca escritos en
   * la plantilla.
   */
  it('lee los datos de la empresa para el pie', () => {
    const config = readAppConfig({
      ...MINIMUM,
      COMPANY_NAME: 'Sastra S.A.S.',
      COMPANY_TAX_ID: '1054994043-1',
      COMPANY_ADDRESS: '  Medellin, Colombia  ',
      SUPPORT_EMAIL: 'hola@sastra.co',
    });

    expect(config.company).toEqual({
      name: 'Sastra S.A.S.',
      taxId: '1054994043-1',
      address: 'Medellin, Colombia',
      supportEmail: 'hola@sastra.co',
    });
  });

  /**
   * Caso borde de HU-004: el frontend solo los pinta. Quien los exige es el
   * backend, que los necesita para los correos. Tumbar el renderizado por una
   * direccion que falta cambiaria un pie incompleto por un sitio caido.
   */
  it('no exige ningun dato de la empresa y trata el blanco como ausente', () => {
    expect(readAppConfig(MINIMUM).company).toEqual({
      name: null,
      taxId: null,
      address: null,
      supportEmail: null,
    });
    expect(readAppConfig({ ...MINIMUM, COMPANY_NAME: '   ' }).company.name).toBeNull();
  });
});

/**
 * Caso borde de HU-004: un despliegue con la configuracion de empresa a medias
 * arranca igual, pero lo dice. Descubrirlo mirando el pie en produccion es tarde.
 */
describe('avisosDeConfiguracion', () => {
  const conEmpresa = (variables: Record<string, string>) =>
    avisosDeConfiguracion(readAppConfig({ ...MINIMUM, ...variables }));

  const COMPLETA = {
    COMPANY_NAME: 'Sastra S.A.S.',
    COMPANY_TAX_ID: '1054994043-1',
    COMPANY_ADDRESS: 'Medellin, Colombia',
    SUPPORT_EMAIL: 'hola@sastra.co',
  };

  it('no avisa de nada cuando la configuracion esta completa', () => {
    expect(conEmpresa(COMPLETA)).toEqual([]);
  });

  it('nombra cada variable que falta', () => {
    const avisos = conEmpresa({ ...COMPLETA, COMPANY_TAX_ID: '', COMPANY_ADDRESS: '' }).join(' ');

    expect(avisos).toContain('COMPANY_TAX_ID');
    expect(avisos).toContain('COMPANY_ADDRESS');
    expect(avisos).not.toContain('COMPANY_NAME');
  });

  /**
   * Este no es un dato mas: sin el no hay canal visible para ejercer los
   * derechos del titular y se incumple la Ley 1581, asi que se avisa aparte y
   * diciendo por que.
   */
  it('avisa del correo de soporte por separado, citando la obligacion legal', () => {
    const avisos = conEmpresa({ ...COMPLETA, SUPPORT_EMAIL: '' });

    expect(avisos).toHaveLength(2);
    expect(avisos.join(' ')).toContain('Ley 1581');
  });

  it('avisa de las cuatro cuando no hay ninguna', () => {
    const avisos = conEmpresa({}).join(' ');

    for (const variable of Object.keys(COMPLETA)) {
      expect(avisos).toContain(variable);
    }
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
