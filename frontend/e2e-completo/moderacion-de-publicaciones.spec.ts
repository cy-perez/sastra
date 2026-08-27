import { expect, test, type Page } from '@playwright/test';

import { MODERADORA } from '../playwright.completo.config';
import { tomarUnaFoto } from './camara';
import { esperarEnlace, enlacesVistos, rutaRelativa } from './correo-de-consola';
import { pngDe } from './png';

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

const CONTRASENA = 'una-contrasena-larga-de-verdad';
const RUTA_COLA = '/moderacion/publicaciones';
const RUTA_PUBLICAR = '/publicar';
const RUTA_MIS_PUBLICACIONES = '/mis-publicaciones';
const RUTA_VERIFICACION = '/verificacion-de-vendedor';

/** El mínimo de RN-019. Una más pequeña se rechaza y la prueba no llegaría a nada. */
const TOMA = () => ({ name: 'toma.png', mimeType: 'image/png', buffer: pngDe(900, 1200) });

const NOMBRE_MODERADORA = 'Quien Modera';

function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

function cedulaNueva(): string {
  return String(Date.now()).slice(-9) + String(Math.floor(Math.random() * 900) + 100);
}

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

/**
 * Entra, y no vuelve hasta que el servidor haya contestado.
 *
 * <p><strong>Esperar la respuesta no es prudencia de mas.</strong> `click()` espera al
 * clic, no a la peticion. Quien llama y navega acto seguido —`dejarUnaVendedoraVerificada`
 * lo hace, y va derecho a /publicar— aborta el `POST /auth/login` en vuelo: en la traza
 * sale con estado -1. La sesion no llega a existir, la cookie de refresco tampoco, y lo
 * que se ve despues es un 401 en la primera peticion con token de la pantalla siguiente,
 * que no se parece en nada a la causa.
 *
 * <p>Se espera la respuesta y no el enlace de la cuenta en la cabecera, porque esto lo
 * usa tambien quien todavia no tiene cuenta: ahi el 401 es la respuesta correcta y quien
 * llama decide que hacer con ella.
 */
async function ingresar(page: Page, correo: string): Promise<void> {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(CONTRASENA);

  await Promise.all([
    page.waitForResponse((respuesta) => respuesta.url().includes('/auth/login')),
    page.getByRole('button', { name: 'Entrar' }).click(),
  ]);
}

async function salirSiHaySesion(page: Page): Promise<void> {
  await page.goto('/');
  const salir = page.getByRole('button', { name: 'Salir' });
  if (await salir.isVisible().catch(() => false)) {
    await salir.click();
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
  }
}

/**
 * Entra con la cuenta que modera, creándola si hace falta.
 *
 * <p>El correo es fijo y el backend se reutiliza entre ejecuciones en local, así que la
 * cuenta puede existir ya. El rol lo concede `SECURITY_BOOTSTRAP_MODERATORS`, y funciona
 * porque se otorga también al registrarse.
 */
async function entrarComoModeradora(page: Page): Promise<void> {
  await salirSiHaySesion(page);
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

/**
 * Deja una cuenta nueva verificada como vendedora.
 *
 * <p>RN-011: sin el sello no se puede publicar, así que el recorrido de HU-008 empieza
 * necesariamente por el de HU-002. Se hace por la interfaz y no llamando a la API, que es
 * justo lo que esta suite existe para no hacer.
 */
async function dejarUnaVendedoraVerificada(page: Page, quien: string): Promise<string> {
  const correo = correoNuevo(quien);
  // El titular tiene que ser unico: la bandeja de verificaciones lo muestra, y es lo unico
  // que permite aprobar LA solicitud de esta prueba. Con un nombre fijo se aprobaba la mas
  // vieja de la cola, que en local puede ser de otra ejecucion, y esta vendedora se quedaba
  // sin sello: el sintoma era «Empezar» deshabilitado en /publicar.
  const titular = `Ana Maria ${quien} ${Date.now()}`;
  await registrar(page, correo);

  await page.goto(RUTA_VERIFICACION);
  await page.getByRole('button', { name: 'Empezar' }).click();

  await page.getByLabel('Tipo de documento').selectOption('CC');
  await page.getByLabel('Número del documento').fill(cedulaNueva());
  await page.getByLabel('Nombre completo, como aparece en el documento').fill(titular);
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
  await page.getByLabel('Nombre del titular').fill(titular);
  await page.getByRole('button', { name: 'Guardar la cuenta' }).click();
  await expect(page.getByText('Guardamos tu cuenta.')).toBeVisible();

  await page.getByRole('button', { name: 'Enviar para revisión' }).click();
  await expect(page.getByText('Estamos revisando tu solicitud.')).toBeVisible();

  // La aprueba quien modera, que es el único camino que hay.
  await entrarComoModeradora(page);
  await page.goto('/moderacion/verificaciones');
  await page.getByRole('link').filter({ hasText: titular }).first().click();
  await page.getByRole('button', { name: 'Aprobar' }).click();
  await page.getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.getByText('Verificación aprobada')).toBeVisible();

  await salirSiHaySesion(page);
  await ingresar(page, correo);

  // Se comprueba el sello antes de seguir: sin verificacion, la pantalla lo dice en vez
  // de ofrecer el formulario (RN-011).
  await page.goto(RUTA_PUBLICAR);
  await expect(page.getByText('Verifícate como vendedor para poder publicar.')).toHaveCount(0);

  return correo;
}

/** Un borrador completo, con sus ocho tomas, enviado a revisión. */
async function publicarYEnviarARevision(page: Page, titulo: string): Promise<void> {
  await page.goto(RUTA_PUBLICAR);

  // La categoria se elige antes de crear el borrador, y no es un capricho de navegacion:
  // de ella dependen las condiciones admisibles, los sistemas de talla y que medidas se
  // piden. Hasta elegirla, «Empezar» esta deshabilitado.
  await page.getByLabel('Categoría').selectOption({ label: 'Camisas y blusas' });
  await page.getByRole('button', { name: 'Empezar' }).click();

  // Si la creacion falla, la pantalla lo dice en un `role="alert"`. Se mira primero para
  // que el fallo diga el motivo y no un tiempo de espera agotado buscando el formulario.
  const fallo = page.locator('.publicar__error');
  if (await fallo.isVisible().catch(() => false)) {
    throw new Error(`No se pudo crear el borrador: ${await fallo.textContent()}`);
  }

  await expect(page.getByLabel('Título')).toBeVisible();
  await page.getByLabel('Título').fill(titulo);
  await page.getByLabel('Descripción').fill('Usada dos veces, sin manchas ni descosidos.');
  await page.getByLabel('Marca').fill('Zara');
  await page.getByRole('radio', { name: 'Como nuevo' }).check();
  await page.getByLabel('Sistema de talla').selectOption({ label: 'Letra (XS a XXL)' });
  await page.getByLabel('Valor de la talla').fill('M');

  // Las medidas del grupo que declara la categoría. Sin ellas el envío se rechaza con
  // CATALOG_MEASUREMENTS_INCOMPLETE (RN-021), y van acotadas a su grupo porque «Largo»
  // es también una de las tres dimensiones de la caja.
  const medidas = page.getByRole('group', { name: 'Medidas' });
  for (const [medida, valor] of [
    ['Pecho', '52'],
    ['Hombros', '41'],
    ['Manga', '60'],
    ['Largo', '70'],
  ]) {
    await medidas.getByLabel(medida).fill(valor);
  }

  await page.getByLabel('Color').selectOption({ label: 'Beige' });
  await page.getByLabel('Precio').fill('185000');

  // El envío entero: el peso y las tres dimensiones son un grupo y media caja no es una
  // caja. Faltaba, y era una de las dos razones por las que esto no llegaba a enviarse.
  const envio = page.getByRole('group', { name: 'Envío' });
  await envio.getByLabel('Peso en gramos').fill('600');
  await envio.getByLabel('Largo').fill('30');
  await envio.getByLabel('Ancho').fill('20');
  await envio.getByLabel('Alto').fill('10');

  // El guardado es automático y sale 1,5 s después de dejar de escribir. Hay que verlo
  // aterrizar antes de subir nada: una subida y un guardado en vuelo a la vez escriben
  // sobre la misma publicación, y el bloqueo optimista del criterio 34 tumba a uno de
  // los dos. Cuando el que cae es el guardado, el envío a revisión se rechaza después
  // con `CATALOG_LISTING_INCOMPLETE` y el motivo real queda tres pantallas atrás.
  //
  // Se espera la respuesta que ya trae el envío —lo último que se escribe— y no el
  // cartel de «Guardado», que puede seguir puesto de un guardado anterior.
  await page.waitForResponse(async (respuesta) => {
    if (respuesta.request().method() !== 'PATCH' || !respuesta.url().includes('/listings/')) {
      return false;
    }
    const cuerpo = (await respuesta.json().catch(() => null)) as {
      product?: { shipping?: unknown };
    } | null;
    return cuerpo?.product?.shipping != null;
  });

  // Las ocho tomas. Por el campo de archivo y no por la cámara: la cámara es de HU-003 y
  // aquí lo que se prueba es el ciclo de moderación, no la captura.
  for (let posicion = 0; posicion < 8; posicion++) {
    await page.locator(`#toma-${posicion}`).setInputFiles(TOMA());
    await expect(page.locator(`#toma-${posicion}`)).toHaveCount(0);
  }

  await page.getByRole('button', { name: 'Enviar a revisión' }).click();

  // Se comprueba el estado y no un cartel de confirmación: `listing.submit.sent`
  // —«Enviada a revisión»— está en el archivo de textos pero ninguna plantilla lo usa,
  // así que la prueba esperaba algo que la pantalla no pinta. Lo que sí se ve es que la
  // publicación pasó a revisión: la acción de enviar deja su sitio a la de retirar.
  await expect(page.getByRole('button', { name: 'Retirar de revisión' })).toBeVisible();
}

/** Abre en la cola la fila de un título concreto. La cola es compartida entre pruebas. */
async function abrirEnLaCola(page: Page, titulo: string): Promise<void> {
  await page.goto(RUTA_COLA);
  await expect(page.getByRole('heading', { name: 'Publicaciones pendientes' })).toBeVisible();
  await page.getByRole('link').filter({ hasText: titulo }).first().click();
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
   * El camino principal: aprobar cierra el ciclo que HU-007 dejaba abierto.
   *
   * <p>Va en una sola prueba porque cada paso necesita el estado del anterior, y trocearlo
   * obligaría a fabricar ese estado llamando a la API.
   */
  test('aprobar deja la publicacion publicada', async ({ page }) => {
    const titulo = `Camisa aprobada ${Date.now()}`;

    await dejarUnaVendedoraVerificada(page, 'publica');
    await publicarYEnviarARevision(page, titulo);

    await entrarComoModeradora(page);
    await abrirEnLaCola(page, titulo);

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
    await publicarYEnviarARevision(page, titulo);

    await entrarComoModeradora(page);
    await abrirEnLaCola(page, titulo);

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

    await page.getByRole('button', { name: 'Corregir y volver a enviar' }).click();
    await expect(page.getByRole('button', { name: 'Enviar a revisión' })).toBeVisible();
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
    await publicarYEnviarARevision(page, titulo);

    // Quien modera abre el detalle en su propia pestaña y lo deja ahí.
    const otra = await browser.newPage();
    try {
      await entrarComoModeradora(otra);
      await abrirEnLaCola(otra, titulo);

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
