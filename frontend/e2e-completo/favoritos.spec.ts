import { expect, test } from '@playwright/test';

import { correoNuevo, ingresar, publicarYAprobar, registrar, salirSiHaySesion } from './recorridos';

/**
 * Los favoritos, de punta a punta. HU-011.
 *
 * <p><strong>Lo que solo se puede demostrar aquí y en ningún otro sitio:</strong>
 *
 * <ul>
 *   <li>Que la intención sobrevive al ingreso <strong>con la recarga de por medio</strong>.
 *       El token de acceso vive en memoria y se pierde; la sesión se recupera después con
 *       la cookie de refresco. Una prueba de componente pone la sesión a mano y nunca pasa
 *       por ese hueco, que es justo donde ADR-0029 decide algo.
 *   <li>Que RN-071 se cumple contra la base real: se pausa la publicación desde la cuenta
 *       de quien vende y desaparece de la lista de quien la guardó, <strong>sin que nadie
 *       la desmarque</strong>. Con dobles, esa prueba solo demostraría que el doble filtra.
 *   <li>Que el acuerdo sobre el 403 de RN-072 entre las dos mitades se sostiene: una
 *       prueba de componente inventa ese código al simular la respuesta.
 * </ul>
 */
test.use({ locale: 'es-CO' });

const RUTA_FAVORITOS = '/mis-favoritos';

test.describe('favoritos', () => {
  /**
   * El ciclo entero, en una sola prueba porque cada paso necesita el estado del anterior.
   * Trocearlo obligaría a fabricar ese estado llamando a la API, que es lo que esta suite
   * existe para no hacer.
   */
  test('se guarda desde la ficha sin sesion, se conserva al entrar y desaparece al pausarla', async ({
    page,
  }) => {
    const titulo = `Camisa de favoritos ${Date.now()}`;
    const vendedora = await publicarYAprobar(page, titulo, 'favoritos');

    // --- Sin sesión: el control se ofrece igual (criterio 7) ---
    await page.goto('/catalogo');
    await page.getByRole('link').filter({ hasText: titulo }).first().click();
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    const ficha = page.url();
    const guardar = page.getByRole('button', { name: 'Guardar' });
    await expect(guardar).toBeVisible();

    // --- Se pulsa y lleva a entrar (criterio 8) ---
    await guardar.click();
    await expect(page.getByRole('heading', { name: 'Entra a tu cuenta' })).toBeVisible();
    expect(page.url()).toContain('redirectTo=');

    // Quien compra es otra persona: sobre lo propio el control no se ofrece (criterio 5),
    // y eso se comprueba más abajo con la cuenta de quien vende.
    const compradora = correoNuevo('compradora');
    await registrar(page, compradora, 'Quien Guarda');
    await ingresar(page, compradora);

    // --- Al volver a la ficha, el favorito ya está guardado ---
    //
    // Se navega a mano y no se depende de la vuelta automática porque `registrar` pasa por
    // el enlace del correo y se lleva la navegación por delante. Lo que la prueba fija es
    // lo de después: que la intención sobrevivió a todo eso y se consume al abrir la ficha.
    await page.goto(ficha);
    await expect(page.getByRole('button', { name: 'Quitar de favoritos' })).toBeVisible();

    // Criterio 2: sigue marcado después de recargar. Es la comprobación que separa
    // «se pintó» de «se guardó».
    await page.reload();
    await expect(page.getByRole('button', { name: 'Quitar de favoritos' })).toBeVisible();

    // --- Y está en la lista (criterio 11) ---
    await page.goto(RUTA_FAVORITOS);
    await expect(page.getByRole('heading', { name: 'Tus favoritos' })).toBeVisible();
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();

    // --- La vendedora la pausa ---
    await salirSiHaySesion(page);
    await ingresar(page, vendedora);

    // Se pausa desde la fila de la lista, que es donde esta el boton. Acotado a la fila
    // de esta publicacion: la cuenta puede tener otras, y un `getByRole` suelto pausaria
    // la primera que encuentre.
    await page.goto('/mis-publicaciones');
    const fila = page.getByRole('listitem').filter({ hasText: titulo });
    await fila.getByRole('button', { name: 'Pausar' }).click();
    await expect(fila.getByRole('button', { name: 'Reactivar' })).toBeVisible();

    // --- Y desaparece de la lista de quien la guardó, sin que nadie la desmarcara ---
    //
    // RN-071 contra la base real. La fila del favorito sigue ahí —nada la borró— y lo que
    // cambió es que dejó de casar con el filtro de estado.
    await salirSiHaySesion(page);
    await ingresar(page, compradora);

    await page.goto(RUTA_FAVORITOS);
    await expect(page.getByRole('heading', { name: 'Tus favoritos' })).toBeVisible();
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(0);
    await expect(page.getByText('Todavía no has guardado nada')).toBeVisible();
  });

  /**
   * RN-072 por la puerta de verdad. El control no se ofrece sobre lo propio, y quien lo
   * decide es el servidor: la pantalla no sabe de quién es la publicación.
   */
  test('no se ofrece guardar la publicacion propia, RN-072', async ({ page }) => {
    const titulo = `Camisa propia ${Date.now()}`;
    const vendedora = await publicarYAprobar(page, titulo, 'propia');

    await ingresar(page, vendedora);
    await page.goto('/catalogo');
    await page.getByRole('link').filter({ hasText: titulo }).first().click();

    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Guardar' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Quitar de favoritos' })).toHaveCount(0);
  });

  /** Criterio 16: sin sesión no se ve la lista de nadie. Se explica y se ofrece entrar. */
  test('sin sesion la lista no existe y se ofrece entrar, criterio 16', async ({ page }) => {
    await salirSiHaySesion(page);

    await page.goto(RUTA_FAVORITOS);

    await expect(page.getByRole('heading', { name: 'Tus favoritos' })).toBeVisible();
    await expect(page.getByText('Entra para verlos')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' }).first()).toBeVisible();
  });
});
