import { expect, test, type Page } from '@playwright/test';

/**
 * El tipo, medido sobre la letra que de verdad se pinta.
 *
 * Por que existe este archivo: el kit de Sendik llego con las variantes
 * ITALICAS de Inter y de Archivo dentro de `fuentes/`. El CSS declaraba
 * `font-style: normal`, el navegador lo respetaba, y aun asi el sitio entero se
 * veia inclinado, porque la inclinacion venia dibujada en el archivo. Ninguna
 * prueba de las que habia podia verlo: todas miran clases, tokens y estilos
 * calculados, y los tres decian «normal».
 *
 * El defecto esta en `fuentes.py` del generador, que toma `variables[:1]` sobre
 * la lista de archivos de google/fonts. Ahi `Inter-Italic[opsz,wght].ttf` ordena
 * antes que `Inter[opsz,wght].ttf`, porque el guion va antes que el corchete.
 * Con Archivo pasa igual. Se reporto a diseno; mientras el kit no se corrija,
 * cualquiera que ejecute `fuentes.py` vuelve a meter las italicas sin enterarse.
 *
 * Por eso lo que se mide aqui son PIXELES y no estilos: se pinta una letra en un
 * canvas con la fuente del sitio y se compara donde empieza la tinta arriba con
 * donde empieza abajo. En una letra recta las dos coinciden; en una inclinada,
 * la de arriba esta desplazada a la derecha.
 *
 * Igual que en portada.spec.ts, al documento se llega desde el elemento del
 * locator y no por la global `document`: el hook de convenciones prohibe esa
 * global en todo el frontend por el renderizado en servidor, y aunque una prueba
 * de extremo a extremo nunca corre en el servidor, esta via dice lo mismo y no
 * obliga a hacerle una excepcion a la regla.
 */
test.use({ locale: 'es-CO' });

/**
 * Desplazamiento horizontal, en pixeles, entre la parte alta y la parte baja de
 * una letra pintada con `familia`. Cero o casi cero es recta.
 *
 * Se usa la «H» porque tiene dos astas verticales y ninguna curva ni remate que
 * confunda la medida, y se pinta grande para que la inclinacion, si la hay,
 * salga de varios pixeles y no del ruido del antialias.
 */
const inclinacion = (page: Page, familia: string, peso: number): Promise<number> =>
  page.locator('body').evaluate(
    async (cuerpo, [nombre, grosor]) => {
      const doc = cuerpo.ownerDocument;
      const LADO = 200;
      const lienzo = doc.createElement('canvas');
      lienzo.width = LADO;
      lienzo.height = LADO;
      const pincel = lienzo.getContext('2d');
      if (!pincel) throw new Error('sin contexto 2d');

      // Sin esperar a la fuente, el canvas pinta con la de respaldo y la medida no
      // dice nada de la del sitio.
      await doc.fonts.load(`${grosor} 150px "${nombre}"`, 'H');

      pincel.fillStyle = '#000';
      pincel.font = `${grosor} 150px "${nombre}"`;
      pincel.textBaseline = 'alphabetic';
      pincel.fillText('H', 20, 170);

      const pixeles = pincel.getImageData(0, 0, LADO, LADO).data;
      const primeraColumnaConTinta = (fila: number): number => {
        for (let x = 0; x < LADO; x += 1) {
          if (pixeles[(fila * LADO + x) * 4 + 3] > 128) return x;
        }
        return -1;
      };

      // Dos filas dentro del cuerpo de la letra, lejos de los extremos para que el
      // antialias del borde no cuente.
      let alta = -1;
      let baja = -1;
      for (let y = 0; y < LADO && alta === -1; y += 1) {
        if (primeraColumnaConTinta(y) !== -1) alta = y + 10;
      }
      for (let y = LADO - 1; y >= 0 && baja === -1; y -= 1) {
        if (primeraColumnaConTinta(y) !== -1) baja = y - 10;
      }
      if (alta === -1 || baja === -1 || baja <= alta) throw new Error('no se pinto la letra');

      return primeraColumnaConTinta(alta) - primeraColumnaConTinta(baja);
    },
    [familia, String(peso)] as const,
  );

test.describe('tipografia', () => {
  /**
   * El umbral es 4px sobre una letra de 150px. Una recta da 0 o 1 por el
   * antialias; la italica de Inter tiene unos 10 grados, que a esa altura son
   * mas de 20px de desplazamiento. No hay zona gris entre los dos casos.
   */
  test('no hay texto en italica', async ({ page }) => {
    await page.goto('/');

    expect(Math.abs(await inclinacion(page, 'Inter', 400)), 'Inter regular').toBeLessThan(4);
    expect(Math.abs(await inclinacion(page, 'Inter', 600)), 'Inter fuerte').toBeLessThan(4);
    expect(Math.abs(await inclinacion(page, 'Archivo', 600)), 'Archivo').toBeLessThan(4);
  });

  /**
   * Que las dos familias carguen de verdad. Si un archivo faltara, el navegador
   * caeria al respaldo sin decir nada: la pagina se veria correcta y no seria la
   * marca.
   */
  test('las dos familias del sistema cargan', async ({ page }) => {
    await page.goto('/');

    const cargadas = await page.locator('body').evaluate(async (cuerpo) => {
      const doc = cuerpo.ownerDocument;
      await doc.fonts.ready;
      return Array.from(doc.fonts)
        .filter((cara) => cara.status === 'loaded')
        .map((cara) => cara.family.replace(/["']/g, ''));
    });

    expect(cargadas).toContain('Inter');
    expect(cargadas).toContain('Archivo');
  });

  /**
   * Los cuatro archivos se sirven. Son dos familias por dos subconjuntos —latino
   * y latino extendido— y el navegador elige por `unicode-range`. Un 404 aqui no
   * rompe la pagina: la deja con la fuente de respaldo, que es justo el fallo que
   * no se ve.
   */
  test('los cuatro archivos de fuente responden', async ({ request }) => {
    const archivos = [
      'inter-latin.woff2',
      'inter-latin-ext.woff2',
      'archivo-latin.woff2',
      'archivo-latin-ext.woff2',
    ];

    for (const archivo of archivos) {
      const respuesta = await request.get(`/fuentes/${archivo}`);
      expect(respuesta.status(), archivo).toBe(200);
    }
  });
});
