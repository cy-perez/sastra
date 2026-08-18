import { expect, test } from '@playwright/test';

/**
 * HU-004. Lo que se comprueba aqui es lo que no puede verse en una prueba de
 * componente: el ancho real del documento, el hero sangrando de verdad hasta el
 * borde y el recorrido con teclado.
 *
 * El idioma se fija por el mismo motivo que en shell.spec.ts: Chromium arranca
 * en ingles y estas pruebas leen textos en espanol.
 */
test.use({ locale: 'es-CO' });

test.describe('portada', () => {
  test('el boton principal lleva al registro', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('link', { name: 'Crear cuenta' }).click();

    await expect(page).toHaveURL('/registro');
  });

  /**
   * Caso borde de la historia: 360px es el ancho real de buena parte de los
   * telefonos en Colombia, y la mayoria de las visitas llegan de ahi.
   */
  test('no hay desplazamiento horizontal en un telefono', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await page.goto('/');

    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );

    expect(desborda).toBe(false);
  });

  // Tambien a 320px, que es el minimo que el sistema de diseno contempla.
  test('no hay desplazamiento horizontal a 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/');

    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );

    expect(desborda).toBe(false);
  });

  /**
   * El hero tiene que llegar de borde a borde. Es lo que no conseguia el
   * .contenedor anterior y la razon de la rejilla de carriles de app.css: una
   * franja oscura con margenes claros a los lados no es el diseno.
   */
  test('el hero sangra hasta los dos bordes de la ventana', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');

    // Contra el ancho real del documento y no contra el del viewport: con barra
    // de desplazamiento clasica no son el mismo numero, y la prueba seria
    // inestable segun la maquina.
    const { heroX, heroAncho, anchoDocumento } = await page.evaluate(() => {
      const caja = (document.querySelector('.hero') as HTMLElement).getBoundingClientRect();
      return {
        heroX: Math.round(caja.x),
        heroAncho: Math.round(caja.width),
        anchoDocumento: document.documentElement.clientWidth,
      };
    });

    expect(heroX).toBe(0);
    expect(heroAncho).toBe(anchoDocumento);
  });

  /**
   * Criterio 10: las tres tarjetas se apilan en una columna. Sin esto, una
   * rejilla de tres columnas comprimidas a 100px pasaria la prueba de
   * desplazamiento horizontal y el diseno estaria roto igual.
   */
  test('las tarjetas de confianza se apilan en una columna en movil', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await page.goto('/');

    const cajas = await page.locator('ul.tarjetas li').evaluateAll((tarjetas) =>
      tarjetas.map((tarjeta) => {
        const caja = tarjeta.getBoundingClientRect();
        return { x: Math.round(caja.x), y: Math.round(caja.y) };
      }),
    );

    expect(cajas).toHaveLength(3);
    // Misma coordenada horizontal y cada una debajo de la anterior.
    expect(new Set(cajas.map((caja) => caja.x)).size).toBe(1);
    expect(cajas.map((caja) => caja.y)).toEqual(
      [...cajas.map((caja) => caja.y)].sort((a, b) => a - b),
    );
  });

  test('el boton principal se alcanza y se activa con el teclado', async ({ page }) => {
    await page.goto('/');

    const cta = page.getByRole('link', { name: 'Crear cuenta' });
    await cta.focus();
    await expect(cta).toBeFocused();

    await page.keyboard.press('Enter');
    await expect(page).toHaveURL('/registro');
  });

  /**
   * El anillo de foco se dibuja alrededor del boton, que esta relleno de ocre.
   * Lo que tiene que contrastar es anillo contra ocre, no anillo contra la
   * franja: un anillo ocre sobre boton ocre es el fallo real, y comparandolo
   * con el fondo del hero pasaria desapercibido.
   *
   * <p>Sin .franja-oscura, que redefine --color-foco, el anillo es tinta y el
   * boton esta sobre tinta. El criterio 17 pide ademas 3px.
   */
  test('el foco del boton principal es visible y mide 3px', async ({ page }) => {
    await page.goto('/');
    const cta = page.getByRole('link', { name: 'Crear cuenta' });
    await cta.focus();

    const { anillo, relleno, grosor } = await cta.evaluate((elemento) => {
      const estilo = getComputedStyle(elemento);
      return {
        anillo: estilo.outlineColor,
        relleno: estilo.backgroundColor,
        grosor: estilo.outlineWidth,
      };
    });

    expect(anillo).not.toBe(relleno);
    expect(grosor).toBe('3px');
  });

  test('el pie muestra los datos de la empresa y el canal de contacto', async ({ page }) => {
    await page.goto('/');
    const pie = page.locator('footer');

    await expect(pie).toContainText('Sastra S.A.S.');
    await expect(pie).toContainText('000000000-0');
    await expect(pie.getByRole('link', { name: 'hola@sastra.co' })).toHaveAttribute(
      'href',
      'mailto:hola@sastra.co',
    );
  });

  test('desde la portada se llega a la politica de tratamiento de datos', async ({ page }) => {
    await page.goto('/');

    await page
      .locator('footer')
      .getByRole('link', { name: /tratamiento de datos/i })
      .click();

    await expect(page).toHaveURL('/tratamiento-de-datos');
  });

  /** Ningun enlace de la portada puede llevar a una ruta que responda 404. */
  test('ningun enlace de la portada esta roto', async ({ page, request }) => {
    await page.goto('/');
    const destinos = await page
      .locator('a[href^="/"]')
      .evaluateAll((enlaces) =>
        Array.from(new Set(enlaces.map((enlace) => enlace.getAttribute('href') ?? ''))),
      );

    expect(destinos.length).toBeGreaterThan(0);
    for (const destino of destinos) {
      expect((await request.get(destino)).status(), `enlace roto: ${destino}`).toBe(200);
    }
  });
});

/**
 * Caso borde de la historia: el titular en ingles es bastante mas largo que el
 * espanol y 320px es el ancho mas estrecho que el sistema contempla. Es la
 * combinacion la que rompe, no cada una por separado.
 *
 * <p>Va en su propio bloque porque el idioma se negocia con la cabecera que
 * manda el navegador, y `test.use` solo se puede fijar por bloque.
 */
test.describe('portada en ingles', () => {
  test.use({ locale: 'en-US' });

  test('no desborda a 320px con el titular largo en ingles', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toContainText('Buy and sell fashion');

    const desborda = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );

    expect(desborda).toBe(false);
  });
});
