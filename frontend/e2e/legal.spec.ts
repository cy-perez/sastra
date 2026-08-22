import { expect, test, type Page } from '@playwright/test';

import { DOCUMENTOS_LEGALES, RUTAS_LEGALES } from '../src/app/core/routes/legal-routes';

/**
 * Maquetacion de los tres documentos legales.
 *
 * <p>Existe por el mismo fallo que ya habia mordido a las cuatro paginas
 * informativas y que en `features/legal` nunca se corrigio: sin `grid-column` en
 * el host, la rejilla de <main> auto-colocaba la pagina en un carril de sangria
 * y el documento se pintaba en una columna estrechisima pegada al borde
 * izquierdo, sin adaptarse a la ventana. El texto estaba, los encabezados
 * estaban, no habia desplazamiento horizontal, la auditoria de axe pasaba y el
 * HTML servido traia todo: **todo verde, y el documento ilegible**.
 *
 * <p>Por eso se mide el ancho. Es lo unico que lo delata, y es la unica prueba
 * que `contenido.spec.ts` tenia y esta suite no.
 *
 * <p>Las direcciones salen de las constantes de `core/routes` y no escritas a
 * mano: publicar un cuarto documento lo mete solo en la suite. Una pagina que no
 * este en esta lista no se mide y nadie se entera.
 *
 * <p>El idioma se fija como en el resto de las pruebas de navegador: Chromium
 * arranca en ingles y aqui se leen titulares en espanol.
 */
test.use({ locale: 'es-CO' });

const PAGINAS = DOCUMENTOS_LEGALES.map((id) => ({ id, ruta: RUTAS_LEGALES[id] }));

/** Si el documento es mas ancho que la ventana, hay barra horizontal. */
const desbordaHorizontalmente = (page: Page): Promise<boolean> =>
  page.locator('html').evaluate((raiz) => raiz.scrollWidth > raiz.clientWidth);

test.describe('documentos legales, maquetacion', () => {
  /**
   * El fallo original. Se mide el elemento de la ruta, que es el hermano de
   * <router-outlet>: es ahi donde se asigna el carril, y medir el <article> de
   * dentro esconderia el problema si algun dia el host volviera a caer en la
   * sangria con el articulo desbordandolo.
   */
  for (const { id, ruta } of PAGINAS) {
    test(`${ruta} ocupa el carril de contenido`, async ({ page }) => {
      for (const ventana of [360, 1280]) {
        await page.setViewportSize({ width: ventana, height: 900 });
        await page.goto(ruta);
        await page.getByRole('heading', { level: 1 }).waitFor();

        const ancho = await page
          .locator('main > *:not(router-outlet)')
          .first()
          .evaluate((pagina) => Math.round(pagina.getBoundingClientRect().width));

        // Con la sangria del sistema (16px en movil, 24px en escritorio) y el
        // ancho de lectura de 68ch, nunca baja de la mitad de la ventana en
        // movil ni del ancho de lectura en escritorio. Es el mismo umbral que
        // usa contenido.spec.ts para las informativas.
        expect(ancho, `${id} a ${ventana}px`).toBeGreaterThan(Math.min(ventana * 0.8, 700));
      }
    });
  }

  /**
   * 360px es el ancho real de buena parte de los telefonos en Colombia y de ahi
   * llega la mayoria de las visitas. Un documento legal es prosa larga con
   * direcciones web dentro, que es justo lo que desborda la columna.
   */
  for (const { ruta } of PAGINAS) {
    test(`${ruta} no desborda a 360px`, async ({ page }) => {
      await page.setViewportSize({ width: 360, height: 740 });
      await page.goto(ruta);
      await page.getByRole('heading', { level: 1 }).waitFor();

      expect(await desbordaHorizontalmente(page)).toBe(false);
    });
  }

  /**
   * El texto del documento entra por `innerHTML` desde un archivo de `public/`,
   * asi que llega sin ninguna clase y lo maqueta `legal-page.css` por elemento.
   * Si esa hoja dejara de alcanzarlo —quitar `ViewEncapsulation.None` es el modo
   * de fallo real— el documento saldria como texto plano corrido. Se comprueba
   * sobre el margen de un parrafo, que es lo que esa hoja pone y el navegador
   * no.
   */
  test('el texto insertado recibe la maquetacion de la hoja del componente', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(RUTAS_LEGALES.privacy);
    await page.getByRole('heading', { level: 1 }).waitFor();

    const parrafo = page.locator('.texto-del-documento p').first();
    await expect(parrafo).toBeVisible();

    const margen = await parrafo.evaluate((elemento) =>
      Number.parseFloat(getComputedStyle(elemento).marginTop),
    );

    expect(margen).toBeGreaterThan(0);
  });
});
