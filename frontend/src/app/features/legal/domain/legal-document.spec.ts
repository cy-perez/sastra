import { describe, expect, it } from 'vitest';

import { RUTAS_LEGALES } from '../../../core/routes/legal-routes';
import { esBorrador, rutaDelTexto } from './legal-document';

describe('rutaDelTexto', () => {
  /**
   * La version va en el nombre del archivo: cambiarla y no subir el texto nuevo
   * da un 404 ruidoso en vez de servir el texto viejo con la etiqueta nueva, que
   * es el fallo que invalidaria la evidencia del consentimiento.
   */
  it('ata el archivo a la version y al idioma', () => {
    expect(rutaDelTexto({ id: 'terms', version: '2026-08-01', locale: 'es' })).toBe(
      '/legal/terms.2026-08-01.es.html',
    );
    expect(rutaDelTexto({ id: 'privacy', version: '2026-09-15', locale: 'en' })).toBe(
      '/legal/privacy.2026-09-15.en.html',
    );
  });

  // Empieza por barra para que el interceptor de la API la deje pasar: es un
  // activo de este sitio, no una llamada al backend.
  it('produce una ruta del propio sitio', () => {
    expect(rutaDelTexto({ id: 'cookies', version: 'v1', locale: 'es' })).toMatch(/^\//);
  });
});

describe('RUTAS_LEGALES', () => {
  // Las direcciones van en espanol y no cambian al cambiar de idioma: el trafico
  // vendra de Colombia y el ingles existe por accesibilidad, no por buscadores.
  it('da una direccion en espanol a cada documento', () => {
    expect(RUTAS_LEGALES.terms).toBe('/terminos');
    expect(RUTAS_LEGALES.privacy).toBe('/tratamiento-de-datos');
    expect(RUTAS_LEGALES.cookies).toBe('/politica-de-cookies');
  });
});

describe('esBorrador', () => {
  it('reconoce la version de relleno', () => {
    expect(esBorrador('borrador-local')).toBe(true);
    expect(esBorrador('2026-08-01')).toBe(false);
  });
});
