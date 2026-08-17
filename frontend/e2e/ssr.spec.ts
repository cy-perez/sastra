import { expect, test } from '@playwright/test';

/**
 * Estas comprobaciones se hacen sobre el HTML crudo, sin ejecutar JavaScript.
 * Es la unica forma de demostrar lo que promete ADR-0006: que el buscador y la
 * vista previa de WhatsApp reciben la pagina ya montada. Un `page.goto` pasaria
 * igual aunque el servidor entregara un documento vacio y todo lo pintara el
 * navegador despues.
 */
test.describe('renderizado en servidor', () => {
  test('entrega el HTML en espanol por omision', async ({ request }) => {
    const response = await request.get('/');
    const html = await response.text();

    expect(response.status()).toBe(200);
    expect(html).toContain('lang="es"');
    expect(html).toContain('Compra y vende moda con respaldo');
  });

  test('entrega el HTML ya traducido segun Accept-Language', async ({ request }) => {
    const response = await request.get('/', {
      headers: { 'Accept-Language': 'en-US,en;q=0.9' },
    });
    const html = await response.text();

    expect(response.status()).toBe(200);
    expect(html).toContain('lang="en"');
    expect(html).toContain('Buy and sell fashion');
    expect(html).not.toContain('Compra y vende moda');
  });

  test('el idioma elegido gana sobre el del navegador', async ({ request }) => {
    const response = await request.get('/', {
      headers: { 'Accept-Language': 'en-US,en;q=0.9', Cookie: 'sastra_locale=es' },
    });

    expect(await response.text()).toContain('lang="es"');
  });

  test('el tema llega resuelto en el HTML, sin parpadeo', async ({ request }) => {
    const claro = await request.get('/');
    expect(await claro.text()).toContain('data-tema="claro"');

    const oscuro = await request.get('/', { headers: { Cookie: 'sastra_theme=dark' } });
    expect(await oscuro.text()).toContain('data-tema="oscuro"');
  });

  test('los metadatos de la pagina salen traducidos', async ({ request }) => {
    const response = await request.get('/', { headers: { 'Accept-Language': 'en' } });
    const html = await response.text();

    expect(html).toContain('<meta name="description" content="Buy and sell fashion');
    expect(html).toContain('<meta property="og:locale" content="en">');
  });

  // Un "no existe" servido con 200 es un soft 404: el buscador lo indexa como
  // pagina buena y termina ofreciendo direcciones rotas.
  test('una direccion inexistente responde 404 de verdad', async ({ request }) => {
    const response = await request.get('/esta-ruta-no-existe');

    expect(response.status()).toBe(404);
    expect(await response.text()).toContain('Esta página no existe');
  });

  // Sin Vary, una cache intermedia le daria a un visitante la pagina
  // renderizada para otro, en otro idioma y con otro tema.
  test('declara Vary para que ninguna cache mezcle idiomas', async ({ request }) => {
    const response = await request.get('/');
    const vary = response.headers()['vary'] ?? '';

    expect(vary).toContain('Cookie');
    expect(vary).toContain('Accept-Language');
  });
});
