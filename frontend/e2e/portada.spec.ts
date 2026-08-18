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

    const hero = page.locator('.hero');
    const caja = await hero.boundingBox();

    expect(caja?.x).toBe(0);
    expect(caja?.width).toBe(1280);
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
   * El anillo de foco sobre la franja oscura es tinta sobre tinta si falta
   * .franja-oscura, que lo redefine. Se comprueba que el color efectivo del
   * anillo no sea el mismo que el fondo.
   */
  test('el foco del boton principal es visible sobre la franja', async ({ page }) => {
    await page.goto('/');
    const cta = page.getByRole('link', { name: 'Crear cuenta' });
    await cta.focus();

    const { anillo, fondo } = await cta.evaluate((elemento) => ({
      anillo: getComputedStyle(elemento).outlineColor,
      fondo: getComputedStyle(elemento.closest('.hero') as HTMLElement).backgroundColor,
    }));

    expect(anillo).not.toBe(fondo);
  });

  test('el pie muestra los datos de la empresa y el canal de contacto', async ({ page }) => {
    await page.goto('/');
    const pie = page.locator('footer');

    await expect(pie).toContainText('Sastra S.A.S.');
    await expect(pie).toContainText('1054994043-1');
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
