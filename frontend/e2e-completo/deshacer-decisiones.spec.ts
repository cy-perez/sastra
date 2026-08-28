import { expect, test } from '@playwright/test';

import { entrarComoModeradora, publicarYAprobar, salirSiHaySesion } from './recorridos';

/**
 * Deshacer una decisión del moderador, de punta a punta. HU-010.
 *
 * <p><strong>Es el camino de vuelta.</strong> HU-006 y HU-008 probaron el de ida —aprobar
 * un sello, aprobar una publicación— y hasta ahora nada recorría la vuelta: los dos
 * endpoints existían desde HU-002 y HU-007 y no había forma de llegar a ellos desde la
 * interfaz.
 *
 * <p>Lo que solo se puede comprobar aquí y en ningún otro sitio:
 *
 * <ul>
 *   <li>Que un moderador con sesión abierta llega de la ficha al perfil del vendedor. Es un
 *       enlace que hasta HU-010 <strong>no se pintaba para él</strong>, porque la misma ruta
 *       le devolvía `sellerId` en nulo. Una prueba de componente no lo veía: le pasa el
 *       cuerpo que ella misma escribe.
 *   <li>Que revocar el sello deja las publicaciones visibles (RN-013). Eso es una regla
 *       sobre dos agregados de contextos distintos, y solo se ve contra la base real.
 *   <li>Que lo que se baja desaparece del catálogo para quien no tiene cuenta (RN-068).
 * </ul>
 *
 * <p>Va en una sola prueba porque cada paso necesita el estado del anterior, y trocearlo
 * obligaría a fabricar ese estado llamando a la API, que es lo que esta suite existe para
 * no hacer.
 */
test.use({ locale: 'es-CO' });

test.describe('deshacer una decisión del moderador', () => {
  test('revocar el sello deja lo publicado, y bajarlo lo quita del catalogo', async ({ page }) => {
    const titulo = `Camisa por deshacer ${Date.now()}`;

    await publicarYAprobar(page, titulo, 'deshacer');

    // Desde aquí modera. `publicarYAprobar` cerró la sesión al terminar.
    await entrarComoModeradora(page);

    await page.goto('/catalogo');
    await page.getByRole('link').filter({ hasText: titulo }).first().click();
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    const ficha = page.url();

    // --- Del producto a la persona -----------------------------------------
    //
    // El enlace que no existía para un moderador. Se abre desde la ficha y no por su
    // dirección directa a propósito: el camino es la prueba.
    //
    // Por su sitio en la ficha y no por el nombre: el de la cuenta es fijo para todas las
    // vendedoras que crea el recorrido, así que buscarlo por texto encontraría también la
    // de otra ejecución si algún día aparece en la misma pantalla.
    const vendedor = page.locator('.ficha__vendedor a');
    await expect(vendedor).toHaveCount(1);

    const nombre = (await vendedor.textContent())?.trim() ?? '';
    await vendedor.click();

    await expect(page.getByRole('heading', { name: nombre })).toBeVisible();
    await expect(page.locator('.insignia-verificado').first()).toBeVisible();

    // --- Revocar el sello ---------------------------------------------------
    await page.getByRole('button', { name: 'Revocar el sello' }).click();

    // Criterio 13: se dice antes de confirmar, no después.
    await expect(page.getByText('Lo que ya tiene publicado sigue visible')).toBeVisible();

    // Exacto: `getByLabel` casa por subcadena y sin distinguir mayusculas, y el texto de
    // la confirmacion dice "recibe el motivo por correo", asi que sin esto el localizador
    // encuentra tambien el panel entero.
    await page.getByLabel('Motivo', { exact: true }).selectOption('DOCUMENT_NOT_ITS_HOLDER');
    await page.getByRole('button', { name: 'Confirmar' }).click();

    // Criterio 14: la insignia desaparece sin recargar.
    await expect(page.locator('.insignia-verificado')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Revocar el sello' })).toHaveCount(0);

    // --- RN-013: lo publicado sigue publicado ------------------------------
    //
    // Es la mitad de la regla que más fácil se incumple sin que nadie lo note, porque
    // "revocar" suena a que arrastra lo demás.
    await page.goto(ficha);
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    // --- Bajar la publicación ----------------------------------------------
    await page.getByRole('button', { name: 'Bajar esta publicación' }).click();
    await page.getByLabel('Motivo', { exact: true }).selectOption('PROHIBITED_ITEM');
    await page.getByRole('button', { name: 'Confirmar' }).click();

    /*
     * Para quien acaba de bajarla, lo que cambia es que ya no se ofrece bajarla.
     *
     * Y **no** que la ficha diga "ya no está disponible": a un moderador esta misma ruta le
     * sirve la publicación en cualquier estado, que es justo lo que le permite mirar lo que
     * acaba de retirar. El mensaje es lo que ve quien no modera, y se comprueba abajo.
     */
    await expect(page.getByRole('button', { name: 'Bajar esta publicación' })).toHaveCount(0);

    // Criterio 5, ahora sí: sin cuenta, la ficha lo dice y el catálogo no la trae (RN-068).
    await salirSiHaySesion(page);

    await page.goto(ficha);
    await expect(page.getByText('Esta publicación ya no está disponible')).toBeVisible();

    await page.goto('/catalogo');
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(0);
  });
});
