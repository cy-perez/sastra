import { expect, test, type Page } from '@playwright/test';

import { MODERADORA } from '../playwright.completo.config';
import {
  NOMBRE_MODERADORA,
  RUTA_MIS_PUBLICACIONES,
  correoNuevo,
  dejarUnaVendedoraVerificada,
  entrarComoModeradora,
  ingresar,
  publicarYEnviarARevision,
  registrar,
  salirSiHaySesion,
} from './recorridos';

/**
 * El ciclo de publicación, de punta a punta. HU-008.
 *
 * <p>Esta suite existe por lo que ninguna prueba de componente puede demostrar: que el
 * acuerdo entre las dos mitades se sostiene. En concreto, que
 * `CATALOG_LISTING_INVALID_STATE` es el código que el backend manda y el que
 * `ListingReviewStore.yaNoEstaPendiente` reconoce — hasta ahora eso solo lo afirmaba una
 * prueba de componente **que inventaba ella misma ese código** al simular la respuesta.
 *
 * <p>Y que el ciclo se cierra: HU-007 dejaba la publicación en `PENDING_REVIEW` sin nada
 * que pudiera sacarla de ahí. Aquí un vendedor envía, un moderador decide, y el vendedor
 * ve el resultado.
 *
 * <p>Va con `FEATURE_PUBLISHING` encendida, que la configuración de esta suite fija. Sin
 * ella el formulario y la bandeja no existen y sus rutas responden 404.
 *
 * <p><strong>Tres de estas pruebas nacieron en rojo y encontraron un defecto de
 * HU-007.</strong> Las del ciclo de publicación se caían en `publicarYEnviarARevision`,
 * justo después de «Empezar»: crear un borrador con solo la categoría —el cuerpo exacto
 * que manda esa pantalla— respondía 500, porque la tabla `products` exigía `NOT NULL` en
 * doce columnas que el criterio 5 permite dejar en blanco y el repositorio desreferenciaba
 * lo anulable sin guarda. No lo veía nadie porque `/publicar` no se había ejercitado nunca
 * contra un backend real: las pruebas de recorrido creaban siempre el producto completo.
 *
 * <p>Detrás de ese defecto había otro: la respuesta de subir una toma trae el producto
 * entero, y la pantalla la volcaba encima de lo que se estaba escribiendo. Quien tecleaba
 * y arrastraba una foto seguida perdía lo tecleado, en silencio.
 *
 * <p>Se dejaron fallando en vez de apagarlas con `test.fixme`, y por eso los dos defectos
 * se arreglaron. El diagnóstico completo, con lo que la prueba misma tenía mal, está en
 * `docs/producto/historias/HU-008-moderacion-de-publicaciones.md`.
 */
test.use({ locale: 'es-CO' });

/** La cola es de esta suite y no de los recorridos: solo la abre quien modera. */
const RUTA_COLA = '/moderacion/publicaciones';

/** El backend de verdad, para las pruebas que tienen que ver lo que hace Tomcat con la URI. */
const API = 'http://localhost:8081/api/v1';

/**
 * Abre el detalle de una publicación concreta, por su identificador.
 *
 * <p><strong>Antes la buscaba en la cola, y por eso esta suite dejaba de pasar.</strong>
 * La cola es FIFO —lo más viejo primero, veinte por página— así que lo que se acaba de
 * enviar va al final, y contra una base que arrastra pendientes de corridas anteriores no
 * aparece en la primera página. Como la pantalla no ofrece forma de pasar de esa primera
 * página, no había ningún camino: el síntoma era un tiempo de espera agotado buscando una
 * fila que sí existía.
 *
 * <p><strong>Lo que esto deja sin guardián, dicho en voz alta:</strong> que una publicación
 * recién enviada aparezca en la cola. No es un descuido de esta suite. Mientras la cola no
 * se pueda paginar desde la interfaz, esa comprobación solo se puede hacer con la base
 * vacía, y una prueba que solo vale con la base vacía es la que había. Lo que sí sigue
 * probado aquí es lo contrario —que después de decidir la fila ya no está—, que es lo que
 * pide el criterio 8.
 */
async function abrirElDetalle(page: Page, id: string, titulo: string): Promise<void> {
  await page.goto(`${RUTA_COLA}/${id}`);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();
}

test.describe('moderación de publicaciones', () => {
  /**
   * Criterio 2. <strong>La que más importa de esta suite.</strong>
   *
   * <p>Una cuenta corriente no puede enterarse de que la cola existe. Si el guard se
   * cayera, o si alguien quitara el `canActivate` de la ruta, esto lo diría.
   */
  test('una cuenta sin el rol no llega a la cola', async ({ page }) => {
    await registrar(page, correoNuevo('curiosa'));

    await page.goto(RUTA_COLA);

    await expect(page.getByRole('heading', { name: 'Publicaciones pendientes' })).toHaveCount(0);
    await expect(page.locator('.publicacion')).toHaveCount(0);
  });

  test('sin sesion tampoco se llega a la cola', async ({ page }) => {
    await page.goto(RUTA_COLA);

    await expect(page.getByRole('heading', { name: 'Publicaciones pendientes' })).toHaveCount(0);
  });

  /**
   * Criterio 2 sobre el HTML que sale del servidor.
   *
   * <p>Lo consigue el guard, que deniega en el servidor: lo que se sirve es la página de
   * «no existe» (ADR-0021).
   */
  test('el HTML servido de la cola no dice de que va', async ({ request }) => {
    const respuesta = await request.get(RUTA_COLA);
    const html = await respuesta.text();

    expect(respuesta.status()).toBe(200);

    // Se mira el marcado y no el documento crudo: el diccionario de Transloco viaja entero
    // en el estado transferido, asi que el texto de cualquier pantalla aparece ahi por
    // casualidad. Lo que importa es que no se haya PINTADO nada de la cola.
    expect(/<h1[^>]*>\s*Publicaciones pendientes/.test(html)).toBe(false);
    expect(/<title>([^<]*)<\/title>/.exec(html)?.[1]).toBe('Página no encontrada');
  });

  /**
   * El rodeo del `%3F`, contra Tomcat de verdad. HU-013.
   *
   * <p>Es la unica suite que puede verlo. La regla que hace publica la lectura de una
   * publicacion casaba sobre la ruta **ya decodificada**, y con la expresion regular que
   * tenia hasta HU-013 no distinguia el `?` que separa la cadena de consulta del `?` que
   * llego como `%3F` dentro de un segmento: `/api/v1/listings/{uuid}%3Fx/moderation-history`
   * se decodificaba a algo que casaba con ese `permitAll` y **saltaba la regla autenticada**
   * que protege todo lo que cuelga de `/listings`.
   *
   * <p>MockMvc no lo reproduce -no decodifica la URI ni pasa por el `servletPath` del
   * contenedor-, asi que `ListingSecurityTest` daba verde con el agujero abierto. Aqui hay
   * un Tomcat real.
   *
   * <p>Lo que se afirma es que **no sale 200 ni 500**: sin sesion tiene que ser 401, y lo
   * que habia era una peticion sin token entrando al manejador y reventando con una traza.
   */
  test('un identificador con %3F no salta la regla autenticada del rastro', async ({ request }) => {
    const uuid = '01a06fdf-0c21-7bf3-bff7-2ffbb34a63c1';

    const rodeo = await request.get(`${API}/listings/${uuid}%3Fx/moderation-history`, {
      failOnStatusCode: false,
    });
    expect(rodeo.status()).toBe(401);

    // Y la ruta que si es publica lo sigue siendo, con cadena de consulta incluida: el
    // arreglo no podia cerrar de paso el catalogo, que es de lo que trataba el sufijo que
    // se quito.
    const publica = await request.get(`${API}/listings/${uuid}?campana=x`, {
      failOnStatusCode: false,
    });
    expect(publica.status()).toBe(404);
  });

  /**
   * El camino principal: aprobar cierra el ciclo que HU-007 dejaba abierto.
   *
   * <p>Va en una sola prueba porque cada paso necesita el estado del anterior, y trocearlo
   * obligaría a fabricar ese estado llamando a la API.
   */
  test('aprobar deja la publicacion publicada', async ({ page }) => {
    const titulo = `Camisa aprobada ${Date.now()}`;

    await dejarUnaVendedoraVerificada(page, 'publica');
    const id = await publicarYEnviarARevision(page, titulo);

    await entrarComoModeradora(page);
    await abrirElDetalle(page, id, titulo);

    // Criterio 7: lo que hace falta para decidir está a la vista.
    await expect(page.getByText('Usada dos veces, sin manchas ni descosidos.')).toBeVisible();
    await expect(page.locator('.toma')).toHaveCount(8);

    await page.getByRole('button', { name: 'Aprobar' }).click();
    await page.getByRole('button', { name: 'Confirmar' }).click();

    // Criterio 8: vuelve a la cola con la confirmación, y la fila ya no está.
    await expect(page.getByText('Publicación aprobada')).toBeVisible();
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(0);
  });

  /**
   * Rechazar con motivo, y que el vendedor pueda corregir y reenviar.
   *
   * <p>Es la otra mitad del ciclo y la que cierra RN-022: el motivo que elige quien modera
   * es el que lee quien publicó, con el mismo texto en los dos sitios.
   */
  test('rechazar deja al vendedor corregir y volver a enviar', async ({ page }) => {
    const titulo = `Camisa rechazada ${Date.now()}`;

    const vendedora = await dejarUnaVendedoraVerificada(page, 'rechazada');
    const id = await publicarYEnviarARevision(page, titulo);

    await entrarComoModeradora(page);
    await abrirElDetalle(page, id, titulo);

    // Criterio 9: sin motivo no se envía.
    await page.getByRole('button', { name: 'Rechazar' }).click();
    await expect(page.getByText('Elige un motivo para rechazar.')).toBeVisible();

    await page.getByLabel('Motivo del rechazo').selectOption('PHOTOS_UNUSABLE');
    await page
      .getByLabel('Nota para el vendedor (opcional)')
      .fill('La frontal está borrosa. Vuelve a tomarla con más luz.');
    await page.getByRole('button', { name: 'Rechazar' }).click();
    await page.getByRole('button', { name: 'Confirmar' }).click();
    await expect(page.getByText('Publicación rechazada')).toBeVisible();

    // Y el vendedor lo ve, con el mismo texto del motivo.
    await salirSiHaySesion(page);
    await ingresar(page, vendedora);
    await page.goto(RUTA_MIS_PUBLICACIONES);
    await page.getByRole('link').filter({ hasText: titulo }).first().click();

    // Por el encabezado de la tarjeta de rechazo: el texto suelto casa también con la
    // ayuda del estado, que dice lo mismo y sigue con «Abajo te decimos por qué».
    await expect(page.getByRole('heading', { name: 'No pudimos publicarla' })).toBeVisible();
    await expect(page.getByText('Las fotos no se pueden usar')).toBeVisible();
    await expect(page.getByText('La frontal está borrosa.')).toBeVisible();

    // El rastro, desde el propio bloque de rechazo, con la primera vuelta ya dentro
    // (HU-013). Todavía es una sola: envió y se la rechazaron.
    await page.getByRole('button', { name: 'Ver qué ha pasado' }).click();
    await expect(page.getByText('Se rechazó')).toBeVisible();
    await expect(page.getByText('La enviaste a revisión')).toBeVisible();

    await page.getByRole('button', { name: 'Corregir y volver a enviar' }).click();
    await expect(page.getByRole('button', { name: 'Enviar a revisión' })).toBeVisible();

    // --- Y el final que le faltaba a este recorrido: la segunda vuelta -------
    //
    // Criterio 4 de HU-013, que es la razón de que el envío se anote como evento. Hasta
    // aquí el recorrido dejaba al vendedor con el botón de reenviar delante y no
    // comprobaba nunca que reenviar dejara rastro de las dos idas.

    await page.getByRole('button', { name: 'Enviar a revisión' }).click();
    await expect(page.getByText('En revisión')).toBeVisible();

    await salirSiHaySesion(page);
    await entrarComoModeradora(page);
    await abrirElDetalle(page, id, titulo);
    await page.getByRole('button', { name: 'Aprobar' }).click();
    await page.getByRole('button', { name: 'Confirmar' }).click();
    await expect(page.getByText('Publicación aprobada')).toBeVisible();

    // El vendedor abre el rastro desde el panel -la publicación ya está viva, así que el
    // bloque de rechazo no existe- y ve **las dos vueltas**, no solo la última.
    await salirSiHaySesion(page);
    await ingresar(page, vendedora);
    await page.goto(RUTA_MIS_PUBLICACIONES);

    // Por rol y no por clase: esta suite existe para ver el producto como lo ve una persona,
    // y ahí el nombre de una clase de CSS es un detalle interno. La fila se localiza por su
    // título, que es lo que distingue una publicación de otra en la pantalla.
    const fila = page.getByRole('listitem').filter({ hasText: titulo });
    await fila.getByRole('button', { name: 'Ver qué ha pasado' }).click();

    const pasos = fila.getByRole('list').getByRole('listitem');
    await expect(pasos).toHaveCount(4);
    await expect(pasos.nth(0)).toContainText('Se aprobó y quedó publicada');
    await expect(pasos.nth(1)).toContainText('La enviaste a revisión');
    await expect(pasos.nth(2)).toContainText('Se rechazó');
    await expect(pasos.nth(2)).toContainText('Las fotos no se pueden usar');
    await expect(pasos.nth(3)).toContainText('La enviaste a revisión');

    // Criterio 5 y RN-074: no dice quién decidió ni repite la nota. Se afirma sobre **el
    // nombre y el correo reales de la moderadora**, no sobre la palabra «moderador», que no
    // iba a aparecer ahí bajo ninguna circunstancia y hacía que esta comprobación no pudiera
    // fallar.
    const rastro = await fila.innerText();
    expect(rastro).not.toContain('La frontal está borrosa');
    expect(rastro).not.toContain(NOMBRE_MODERADORA);
    expect(rastro).not.toContain(MODERADORA);
  });

  /**
   * Criterios 11 y 13, y el motivo por el que esta suite existe.
   *
   * <p>El vendedor retira la publicación mientras quien modera la tiene abierta. El
   * backend responde `CATALOG_LISTING_INVALID_STATE` y la pantalla tiene que reconocerlo y
   * decir «ya no está en revisión», no «error inesperado». **Es el único sitio donde ese
   * acuerdo entre las dos mitades se comprueba de verdad**: la prueba de componente
   * inventa el código ella misma al simular la respuesta.
   */
  test('decidir sobre algo que ya no esta pendiente lo dice con su motivo', async ({
    page,
    browser,
  }) => {
    const titulo = `Camisa retirada ${Date.now()}`;

    const vendedora = await dejarUnaVendedoraVerificada(page, 'retirada');
    const id = await publicarYEnviarARevision(page, titulo);

    // Quien modera abre el detalle en su propia pestaña y lo deja ahí.
    const otra = await browser.newPage();
    try {
      await entrarComoModeradora(otra);
      await abrirElDetalle(otra, id, titulo);

      // Mientras tanto, el vendedor la retira.
      await page.goto(RUTA_MIS_PUBLICACIONES);
      await page.getByRole('link').filter({ hasText: titulo }).first().click();
      await page.getByRole('button', { name: 'Retirar de revisión' }).click();
      await expect(page.getByRole('button', { name: 'Enviar a revisión' })).toBeVisible();

      // Y quien modera decide sobre lo que ya no está.
      await otra.getByRole('button', { name: 'Aprobar' }).click();
      await otra.getByRole('button', { name: 'Confirmar' }).click();

      await expect(otra.getByText('Esta publicación ya no está en revisión.')).toBeVisible();
    } finally {
      await otra.close();
    }

    expect(vendedora).toContain('@');
  });
});
