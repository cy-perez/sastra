import { expect, test, type Page } from '@playwright/test';

/**
 * HU-004. Lo que se comprueba aqui es lo que no puede verse en una prueba de
 * componente: el ancho real del documento, el hero sangrando de verdad hasta el
 * borde y el recorrido con teclado.
 *
 * El idioma se fija por el mismo motivo que en shell.spec.ts: Chromium arranca
 * en ingles y estas pruebas leen textos en espanol.
 *
 * Las medidas salen de locators y no de una evaluacion global sobre el arbol de
 * la pagina: el hook de convenciones prohibe alcanzar las API del navegador
 * directamente en todo el frontend, y aunque aqui no hay riesgo de renderizado
 * en servidor, la via del locator dice lo mismo y no obliga a hacerle una
 * excepcion a la regla.
 */
test.use({ locale: 'es-CO' });

/** Si el documento es mas ancho que la ventana, hay barra horizontal. */
const desbordaHorizontalmente = (page: Page): Promise<boolean> =>
  page.locator('html').evaluate((raiz) => raiz.scrollWidth > raiz.clientWidth);

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

    expect(await desbordaHorizontalmente(page)).toBe(false);
  });

  // Tambien a 320px, que es el minimo que el sistema de diseno contempla.
  test('no hay desplazamiento horizontal a 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/');

    expect(await desbordaHorizontalmente(page)).toBe(false);
  });

  /**
   * El hero tiene que llegar de borde a borde. Es lo que no conseguia el
   * .contenedor anterior y la razon de la rejilla de carriles de app.css: una
   * franja oscura con margenes claros a los lados no es el diseno.
   *
   * <p>Es ademas la prueba que habria cazado el fallo del carril: con
   * `display: contents` en el host pero sin declarar `grid-column` desde el, el
   * hero se auto-colocaba en la columna de sangria y medi­a 16px.
   */
  test('el hero sangra hasta los dos bordes de la ventana', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');

    const caja = await page.locator('.hero').boundingBox();
    // Contra el ancho real del documento y no contra el del viewport: con barra
    // de desplazamiento clasica no son el mismo numero, y la prueba seria
    // inestable segun la maquina.
    const anchoDocumento = await page.locator('html').evaluate((raiz) => raiz.clientWidth);

    expect(Math.round(caja?.x ?? -1)).toBe(0);
    expect(Math.round(caja?.width ?? -1)).toBe(anchoDocumento);
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

  /**
   * Tabula desde el principio del documento hasta el CTA. Enfocarlo con focus()
   * demostraria que se puede enfocar, no que se pueda ALCANZAR: si algo del
   * encabezado atrapara el foco, focus() lo saltaria y la prueba pasaria igual.
   *
   * <p>Tabular tiene ademas otra ventaja: `:focus-visible` depende de que la
   * ultima interaccion haya sido de teclado, asi que un focus() programatico
   * puede no pintar el anillo y dejar sin sentido la prueba que lo mide.
   */
  const tabularHastaElCta = async (page: Page) => {
    const cta = page.getByRole('link', { name: 'Crear cuenta' });
    for (let intento = 0; intento < 15; intento += 1) {
      await page.keyboard.press('Tab');
      if ((await page.locator(':focus').getAttribute('href')) === '/registro') {
        return cta;
      }
    }
    throw new Error('El boton principal no se alcanza con el teclado en 15 tabulaciones');
  };

  test('el boton principal se alcanza y se activa con el teclado', async ({ page }) => {
    await page.goto('/');

    const cta = await tabularHastaElCta(page);
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
    const cta = await tabularHastaElCta(page);

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
    await expect(pie.getByRole('link', { name: 'soporte@example.test' })).toHaveAttribute(
      'href',
      'mailto:soporte@example.test',
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

    expect(await desbordaHorizontalmente(page)).toBe(false);
  });
});
