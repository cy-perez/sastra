import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Lee del registro del backend los enlaces que en produccion irian por correo.
 *
 * <p>Con `MAIL_PROVIDER=console` el adaptador de consola imprime el enlace entero
 * con el token en claro (ADR-0012). Es la unica forma de completar el flujo de
 * verificacion sin tocar el codigo de produccion: los tokens se guardan hasheados
 * (SHA-256), asi que ni consultando la base se puede reconstruir el valor que
 * viaja en el enlace. Eso es exactamente lo que se quiere de un token, y por eso
 * la prueba tiene que leerlo por donde lo leeria una persona.
 *
 * <p>Se espera con reintentos porque el envio es en diferido: `AsyncMailSender`
 * devuelve el control antes de que el transporte haya escrito nada, que es
 * justamente lo que hace que el registro no dependa del proveedor de correo.
 */
/**
 * `__dirname` y no `import.meta.url`: Playwright transpila los `.ts` de las
 * pruebas a modulos de CommonJS, porque `frontend/package.json` no declara
 * `"type": "module"`. Con `import.meta` la suite ni siquiera carga. El lanzador
 * `arrancar-backend.mjs` si puede usarlo, porque lo ejecuta node directamente.
 */
const ARCHIVO_DE_REGISTRO = join(__dirname, '.registro-backend.log');

const ESPERA_TOTAL_MS = 15_000;
const CADA_MS = 250;

function registro(): string {
  try {
    return readFileSync(ARCHIVO_DE_REGISTRO, 'utf8');
  } catch {
    // Todavia no existe: el backend arranca y escribe a su ritmo.
    return '';
  }
}

async function esperar(ms: number): Promise<void> {
  await new Promise((sigue) => setTimeout(sigue, ms));
}

/**
 * Todos los enlaces de una ruta que hay ahora mismo en el registro, en orden de
 * aparicion.
 */
function enlacesDe(ruta: string): string[] {
  const patron = new RegExp(`https?://\\S*${ruta}\\?token=\\S+`, 'g');
  return [...registro().matchAll(patron)].map(([enlace]) => enlace.trim());
}

/**
 * El siguiente enlace de esa ruta que aparezca despues de los que ya se habian
 * visto.
 *
 * <p>Se cuenta desde `yaVistos` y no se toma el ultimo, porque varias pruebas
 * corren contra el mismo registro acumulado: tomar el ultimo funcionaria hasta
 * que dos pruebas pidieran el mismo tipo de correo, y entonces una usaria el
 * token de la otra y el fallo no diria nada.
 */
export async function esperarEnlace(ruta: string, yaVistos = 0): Promise<string> {
  const limite = Date.now() + ESPERA_TOTAL_MS;

  while (Date.now() < limite) {
    const enlaces = enlacesDe(ruta);
    if (enlaces.length > yaVistos) {
      return enlaces[yaVistos];
    }
    await esperar(CADA_MS);
  }

  throw new Error(
    `No aparecio ningun enlace de ${ruta} en el registro del backend tras ${ESPERA_TOTAL_MS / 1000}s.\n` +
      `Se esperaba el numero ${yaVistos + 1}. Revisa ${ARCHIVO_DE_REGISTRO}.\n` +
      'Si esta vacio, el backend no arranco: comprueba que PostgreSQL este levantado.',
  );
}

/** Cuantos enlaces de esa ruta hay ya, para pedir el siguiente y no uno viejo. */
export function enlacesVistos(ruta: string): number {
  return enlacesDe(ruta).length;
}

/**
 * La ruta del token dentro de la direccion, tal como la sirve el frontend. Se
 * navega con la ruta relativa y no con la direccion absoluta del registro para
 * que la prueba use el `baseURL` de Playwright.
 */
export function rutaRelativa(enlace: string): string {
  const direccion = new URL(enlace);
  return direccion.pathname + direccion.search;
}
