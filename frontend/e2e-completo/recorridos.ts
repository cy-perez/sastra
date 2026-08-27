import { expect, type Page } from '@playwright/test';

import { MODERADORA } from '../playwright.completo.config';
import { tomarUnaFoto } from './camara';
import { esperarEnlace, enlacesVistos, rutaRelativa } from './correo-de-consola';
import { pngDe } from './png';

/**
 * Los recorridos que las suites comparten.
 *
 * <p>Nacieron dentro de `moderacion-de-publicaciones.spec.ts` porque fue la primera que los
 * necesitó. Se sacaron aquí al llegar HU-009: el catálogo público necesita exactamente lo
 * mismo —una vendedora verificada, una publicación enviada y un moderador que la apruebe—
 * para tener algo que enseñar, y copiar ciento cincuenta líneas de recorrido en una segunda
 * suite es garantizar que un día las dos hagan cosas distintas.
 *
 * <p><strong>Son recorridos, no utilidades.</strong> Cada uno deja el sistema en un estado
 * que una prueba puede dar por cierto: «hay una vendedora con el sello», «hay una
 * publicación esperando revisión». Por eso pasan siempre por la interfaz y nunca llaman a
 * la API: una suite que se salta la pantalla para llegar antes deja de probar la pantalla.
 */

export const CONTRASENA = 'una-contrasena-larga-de-verdad';
export const RUTA_PUBLICAR = '/publicar';
export const RUTA_MIS_PUBLICACIONES = '/mis-publicaciones';
export const RUTA_VERIFICACION = '/verificacion-de-vendedor';

/** El mínimo de RN-019. Una más pequeña se rechaza y la prueba no llegaría a nada. */
export const TOMA = () => ({ name: 'toma.png', mimeType: 'image/png', buffer: pngDe(900, 1200) });

export const NOMBRE_MODERADORA = 'Quien Modera';

export function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

export function cedulaNueva(): string {
  return String(Date.now()).slice(-9) + String(Math.floor(Math.random() * 900) + 100);
}

export async function registrar(page: Page, correo: string, nombre = 'Ana María'): Promise<void> {
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
export async function ingresar(page: Page, correo: string): Promise<void> {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(CONTRASENA);

  await Promise.all([
    page.waitForResponse((respuesta) => respuesta.url().includes('/auth/login')),
    page.getByRole('button', { name: 'Entrar' }).click(),
  ]);
}

export async function salirSiHaySesion(page: Page): Promise<void> {
  await page.goto('/');

  const salir = page.getByRole('button', { name: 'Salir' });
  const entrar = page.getByRole('link', { name: 'Entrar' });

  // **Se espera a que la cabecera diga algo antes de preguntar.** Al cargar la página, la
  // sesión se recupera con la cookie de refresco, y hasta que esa vuelta termina no hay ni
  // «Salir» ni «Entrar». Preguntando antes, una sesión abierta se lee como «no hay
  // ninguna» y el recorrido sigue con ella puesta: es lo que dejaba a quien moderaba
  // dentro cuando la prueba siguiente esperaba a un visitante anónimo.
  await expect(salir.or(entrar)).toBeVisible();

  if (await salir.isVisible()) {
    await salir.click();
    await expect(entrar).toBeVisible();
  }
}

/**
 * Entra con la cuenta que modera, creándola si hace falta.
 *
 * <p>El correo es fijo y el backend se reutiliza entre ejecuciones en local, así que la
 * cuenta puede existir ya. El rol lo concede `SECURITY_BOOTSTRAP_MODERATORS`, y funciona
 * porque se otorga también al registrarse.
 */
export async function entrarComoModeradora(page: Page): Promise<void> {
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
export async function dejarUnaVendedoraVerificada(page: Page, quien: string): Promise<string> {
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
export async function publicarYEnviarARevision(page: Page, titulo: string): Promise<void> {
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
  // Como tuplas y no como `string[][]`: sin el tipo, al desestructurar TypeScript da
  // `string | undefined` y `getByLabel` no lo acepta.
  const declaradas: readonly (readonly [string, string])[] = [
    ['Pecho', '52'],
    ['Hombros', '41'],
    ['Manga', '60'],
    ['Largo', '70'],
  ];
  for (const [medida, valor] of declaradas) {
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

/**
 * Deja una publicación **visible en el catálogo**, con su vendedora verificada.
 *
 * <p>Es el recorrido completo de la fase 2 en una llamada: alguien se verifica, publica,
 * un moderador aprueba y la publicación pasa a `PUBLISHED`. Lo necesita HU-009, que no
 * tiene otra forma de conseguir algo que enseñar: RN-068 dice que en el catálogo solo se
 * ve lo aprobado, y aprobarlo por la API sería saltarse la mitad de lo que esta suite
 * existe para probar.
 *
 * <p>Cierra la sesión al terminar: quien llama va a mirar el catálogo, y todo el sentido
 * de esa comprobación es que se ve igual sin cuenta.
 *
 * @returns el correo de la vendedora, para quien necesite volver a entrar como ella
 */
export async function publicarYAprobar(page: Page, titulo: string, quien: string): Promise<string> {
  const correo = await dejarUnaVendedoraVerificada(page, quien);
  await publicarYEnviarARevision(page, titulo);

  await entrarComoModeradora(page);
  await page.goto('/moderacion/publicaciones');
  await expect(page.getByRole('heading', { name: 'Publicaciones pendientes' })).toBeVisible();
  await page.getByRole('link').filter({ hasText: titulo }).first().click();
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  await page.getByRole('button', { name: 'Aprobar' }).click();
  await page.getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.getByText('Publicación aprobada')).toBeVisible();

  await salirSiHaySesion(page);

  return correo;
}
