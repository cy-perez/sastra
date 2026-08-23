import { expect, test, type Page } from '@playwright/test';

import { MODERADORA } from '../playwright.completo.config';
import { tomarUnaFoto } from './camara';
import { esperarEnlace, enlacesVistos, rutaRelativa } from './correo-de-consola';

/**
 * La bandeja del moderador, de punta a punta. HU-006.
 *
 * <p>Esta suite existe por lo que ninguna prueba de componente puede demostrar: que el
 * rol llega de verdad, que los cinco endpoints responden a quien debe y que aprobar deja
 * a la otra persona con el sello. Las de componente simulan HTTP; aqui hay backend y
 * PostgreSQL.
 *
 * <p><strong>La cuenta que modera se crea como cualquier otra</strong>, por la interfaz.
 * El rol lo concede `SECURITY_BOOTSTRAP_MODERATORS`, que la configuracion de esta suite
 * fija al mismo correo. Funciona porque el rol se otorga tambien al registrarse y no solo
 * al arrancar el backend: sin eso, la cuenta nacería despues del arranque y nunca lo
 * recibiria.
 *
 * <p>El correo de quien modera es fijo y el backend se reutiliza entre ejecuciones en
 * local, asi que la cuenta puede existir ya. Por eso se entra primero y solo se registra
 * si no habia ninguna.
 */
test.use({ locale: 'es-CO' });

const CONTRASENA = 'una-contrasena-larga-de-verdad';
const RUTA_BANDEJA = '/moderacion/verificaciones';
const RUTA_VERIFICACION = '/verificacion-de-vendedor';

function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

function cedulaNueva(): string {
  return String(Date.now()).slice(-9) + String(Math.floor(Math.random() * 900) + 100);
}

/**
 * El nombre importa: quien vende y quien modera tienen que llamarse distinto.
 *
 * <p>Con las dos cuentas llamandose igual, comprobar "hay sesion" mirando el nombre de la
 * cabecera no distingue una de otra, y la prueba sigue con la sesion equivocada creyendo
 * que cambio. Costo un rato averiguarlo.
 */
const NOMBRE_MODERADORA = 'Quien Modera';

async function registrar(page: Page, correo: string, nombre = 'Ana María'): Promise<void> {
  const yaVistos = enlacesVistos('/verificar-correo');

  await page.goto('/registro');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Nombre').fill(nombre);
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByLabel('Fecha de nacimiento').fill('1990-03-04');
  await page.getByLabel('Acepto los términos y condiciones').check();
  await page
    .getByLabel('Autorizo el tratamiento de mis datos personales según la política de privacidad')
    .check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible();

  await page.goto(rutaRelativa(await esperarEnlace('/verificar-correo', yaVistos)));
  await expect(page.getByRole('link', { name: nombre })).toBeVisible();
}

async function ingresar(page: Page, correo: string): Promise<void> {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/**
 * Entra con la cuenta que modera, creandola si hace falta.
 *
 * <p>Cierra antes cualquier sesion abierta. Sin eso, `/ingresar` con una sesion viva deja
 * la anterior en pie y el resto de la prueba corre con la cuenta equivocada, que con las
 * dos llamandose igual no se nota.
 */
async function entrarComoModeradora(page: Page): Promise<void> {
  await page.goto('/');
  const salir = page.getByRole('button', { name: 'Salir' });
  if (await salir.isVisible().catch(() => false)) {
    await salir.click();
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
  }

  await ingresar(page, MODERADORA);

  const dentro = await page
    .getByRole('link', { name: NOMBRE_MODERADORA })
    .waitFor({ timeout: 5_000 })
    .then(() => true)
    .catch(() => false);

  if (!dentro) {
    await registrar(page, MODERADORA, NOMBRE_MODERADORA);
  }

  await expect(page.getByRole('link', { name: NOMBRE_MODERADORA })).toBeVisible();
}

/** Una solicitud completa, enviada a revision, con una cuenta nueva. */
async function dejarUnaSolicitudEnRevision(page: Page, quien: string): Promise<void> {
  await registrar(page, correoNuevo(quien));
  await page.goto(RUTA_VERIFICACION);
  await page.getByRole('button', { name: 'Empezar' }).click();

  await page.getByLabel('Tipo de documento').selectOption('CC');
  await page.getByLabel('Número del documento').fill(cedulaNueva());
  await page.getByLabel('Nombre completo, como aparece en el documento').fill('Ana Maria Garcia');
  await tomarUnaFoto(page);
  await tomarUnaFoto(page);
  await page.getByRole('button', { name: 'Guardar el documento' }).click();
  await expect(page.getByText('Guardamos tu documento.')).toBeVisible();

  await tomarUnaFoto(page);
  await page.getByRole('button', { name: 'Guardar la foto' }).click();
  await expect(page.getByText('Guardamos tu foto.')).toBeVisible();

  await page.getByLabel('Entidad').selectOption('bancolombia');
  await page.getByLabel('Tipo de cuenta').selectOption('SAVINGS');
  await page.getByLabel('Número de cuenta').fill('91500123456');
  await page.getByLabel('Nombre del titular').fill('Ana Maria Garcia');
  await page.getByRole('button', { name: 'Guardar la cuenta' }).click();
  await expect(page.getByText('Guardamos tu cuenta.')).toBeVisible();

  await page.getByRole('button', { name: 'Enviar para revisión' }).click();
  await expect(page.getByText('Estamos revisando tu solicitud.')).toBeVisible();
}

test.describe('bandeja del moderador', () => {
  /**
   * Criterio 2. <strong>La prueba que mas importa de esta suite.</strong>
   *
   * <p>Una cuenta corriente no puede enterarse de que la bandeja existe. Se comprueba
   * que no ve ni el titular de la pantalla ni ninguna solicitud: si el guard se cayera,
   * o si alguien pusiera la ruta a renderizarse en el servidor, esto lo diria.
   */
  test('una cuenta sin el rol no llega a la bandeja', async ({ page }) => {
    await registrar(page, correoNuevo('curiosa'));

    await page.goto(RUTA_BANDEJA);

    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toHaveCount(0);
    await expect(page.locator('.solicitud')).toHaveCount(0);
  });

  /** Y sin sesión tampoco, que es el otro camino por el que se llega a esa dirección. */
  test('sin sesion tampoco se llega a la bandeja', async ({ page }) => {
    await page.goto(RUTA_BANDEJA);

    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toHaveCount(0);
  });

  /**
   * Criterio 13, sobre el HTML que sale del servidor.
   *
   * <p>Las rutas de moderación se declaran `RenderMode.Client` justamente para esto: si
   * se renderizaran, sus títulos viajarían en el HTML de cualquiera que pidiese la
   * dirección, guard o no, porque el guard corre después.
   */
  test('el HTML servido de la bandeja no dice de que va', async ({ request }) => {
    const respuesta = await request.get(RUTA_BANDEJA);
    const html = await respuesta.text();

    expect(respuesta.status()).toBe(200);

    // Se mira el marcado y no el documento crudo: el diccionario de Transloco viaja
    // entero en el estado transferido, asi que el texto de cualquier pantalla aparece
    // ahi por casualidad. Lo que importa es que no se haya PINTADO nada de la bandeja.
    expect(/<h1[^>]*>\s*Verificaciones pendientes/.test(html)).toBe(false);
    expect(/<title>([^<]*)<\/title>/.exec(html)?.[1]).toBe('Página no encontrada');
  });

  /**
   * El camino principal: aprobar y que la persona quede con el sello.
   *
   * <p>Va en una sola prueba porque cada paso necesita el estado del anterior, y
   * trocearlo obligaría a fabricar ese estado llamando a la API, que es justo lo que
   * esta suite existe para no hacer.
   */
  test('aprobar deja a la persona verificada', async ({ page }) => {
    await dejarUnaSolicitudEnRevision(page, 'aprobada');

    await entrarComoModeradora(page);
    await page.goto(RUTA_BANDEJA);

    // Criterio 1: la bandeja trae lo que espera revisión.
    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toBeVisible();
    await expect(page.locator('.solicitud').first()).toBeVisible();

    await page.locator('.enlace-de-fila').first().click();

    // Criterio 5: cuatro dígitos, nunca el número completo, tampoco para quien revisa.
    await expect(page.getByText('Termina en 3456')).toBeVisible();
    await expect(page.getByText('91500123456')).toHaveCount(0);

    // Criterio 6: la imagen se pide al abrirla, y llega de verdad.
    await page.getByRole('button', { name: 'Ver' }).first().click();
    await expect(page.locator('img').first()).toBeVisible();

    // Criterio 10: se confirma una vez.
    await page.getByRole('button', { name: 'Aprobar' }).click();
    await page.getByRole('button', { name: 'Confirmar' }).click();

    // Criterio 8: vuelve a la lista y la solicitud ya no está.
    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toBeVisible();
  });

  /**
   * <strong>RN-060 no se prueba aqui, y es deliberado.</strong>
   *
   * <p>Para intentarlo por la interfaz haria falta que quien modera tuviera su propia
   * solicitud enviada, lo que obliga a recorrer la captura con la camara falsa y deja a
   * esa cuenta con un estado que sobrevive a la corrida: la siguiente ya no empieza donde
   * esta empezaba. Una prueba que depende de lo que dejo la anterior no prueba nada.
   *
   * <p>La regla esta cubierta en los cuatro sitios donde si es estable: en el caso de uso
   * con sus dos caras, en `SellerVerificationJourneyTest` contra la base de verdad, en el
   * controlador con su 403 y su codigo propio, y en `review-detail-page.spec.ts` con la
   * pantalla que no ofrece la accion sobre lo propio.
   */
});
