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

  /**
   * HU-004, criterio 15. Sin esto, mover los bloques a @defer vaciaria el HTML
   * servido y toda la suite seguiria en verde: las pruebas de componente montan
   * el componente, y las de navegador ejecutan JavaScript. Esta es la unica que
   * mira lo que recibe un buscador.
   */
  test('la portada llega entera en el HTML, sin ejecutar JavaScript', async ({ request }) => {
    const html = await (await request.get('/')).text();

    // Hero
    expect(html).toContain('Compra y vende moda con respaldo');
    expect(html).toContain('Publicar es gratis');
    // Los tres pasos
    expect(html).toContain('Publicas tu prenda');
    expect(html).toContain('Alguien la compra');
    expect(html).toContain('Cobras al confirmarse la entrega');
    // Las tres tarjetas de confianza
    expect(html).toContain('El pago queda retenido');
    expect(html).toContain('Vendedores verificados');
    expect(html).toContain('Publicaciones revisadas');
    // Pie
    expect(html).toContain('Sastra S.A.S.');
    expect(html).toContain('000000000-0');
    expect(html).toContain('mailto:hola@sastra.co');
  });

  /**
   * Criterio 2, sobre la pagina completa y no solo sobre la portada: el acento
   * ocre aparece una vez por pantalla, cabecera y pie incluidos. Es la erosion
   * que el criterio quiere evitar y que una prueba de componente no ve.
   */
  test('solo hay un acento ocre en toda la pagina', async ({ request }) => {
    const html = await (await request.get('/')).text();
    const elementos = html.match(/<[a-z]+[^>]*\bclass="[^"]*\bbtn-cta\b[^"]*"/g) ?? [];

    expect(elementos).toHaveLength(1);
  });

  /**
   * Criterio 9, tambien en el arbol ingles: alli tampoco se promete lo que no hay.
   *
   * <p>Se mira el texto visible, no el HTML crudo. El documento trae los scripts
   * de Angular y el estado transferido, y ahi la palabra `return` de cualquier
   * funcion haria fallar la prueba sin que la portada prometa nada.
   */
  test('la portada no promete devoluciones en ninguno de los dos idiomas', async ({ request }) => {
    for (const idioma of ['es', 'en']) {
      const html = await (
        await request.get('/', { headers: { 'Accept-Language': idioma } })
      ).text();
      const visible = html
        .slice(html.indexOf('<body'))
        .replace(/<script[\s\S]*?<\/script>/gi, ' ')
        .replace(/<style[\s\S]*?<\/style>/gi, ' ')
        .replace(/<[^>]+>/g, ' ');

      expect(visible, idioma).not.toMatch(/devoluci[oó]n|reembolso|\brefunds?\b|\breturns?\b/i);
    }
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

  /**
   * Criterio 16, la mitad que la paridad de traducciones no puede ver: que la
   * portada entera este traducida, no solo el titular, y que la direccion no
   * cambie con el idioma.
   */
  test('la portada entera llega traducida al ingles, en la misma direccion', async ({
    request,
  }) => {
    const response = await request.get('/', { headers: { 'Accept-Language': 'en' } });
    const html = await response.text();

    expect(new URL(response.url()).pathname).toBe('/');
    expect(html).toContain('You list your item');
    expect(html).toContain('Reviewed listings');
    expect(html).toContain('Tax ID');
    // Ningun texto en espanol se quedo sin traducir por el camino.
    expect(html).not.toContain('Publicas tu prenda');
    expect(html).not.toContain('Publicaciones revisadas');
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
