import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { expect, test } from '@playwright/test';

import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from '../src/app/core/routes/content-routes';
import { DOCUMENTOS_LEGALES, RUTAS_LEGALES } from '../src/app/core/routes/legal-routes';

/**
 * Toda ruta que la aplicacion declara tiene que servirse con estado 200.
 *
 * <p>Existe por un fallo que ninguna prueba veia: `/verificacion-de-vendedor`
 * estaba en `app.routes.ts` y **no** en `app.routes.server.ts`, asi que caia en el
 * comodin y se servia con **404 pintando la pagina entera**. Abriendola no se nota
 * nada; hay que mirar el estado. Es exactamente lo que el propio
 * `app.routes.server.ts` documenta que le habia pasado antes a `/ingresar`, y le
 * volvio a pasar a la siguiente ruta que alguien agrego.
 *
 * <p>Un comentario que avisa del error no lo impide: lo impide una prueba. Esta
 * recorre las rutas **leyendo el codigo**, no una lista escrita a mano, porque una
 * lista a mano es otra cosa mas que hay que acordarse de actualizar y el fallo que
 * se persigue es justamente ese olvido.
 *
 * <p>No se importa `app.routes.ts`: arrastra Transloco, que necesita el compilador
 * JIT y no esta disponible aqui. Se lee como texto, que para sacar las direcciones
 * sobra.
 */
test.use({ locale: 'es-CO' });

const rutaDelArchivo = (relativa: string): string => join(__dirname, '..', 'src', 'app', relativa);

/**
 * Un valor de ejemplo para cada ruta con parametro.
 *
 * <p>`:id` no es una direccion, es una plantilla, y pedirla tal cual no demuestra nada.
 * Pero saltarsela deja esa ruta sin comprobar, que es justo el fallo que esta suite
 * existe para impedir: paso con `moderacion/verificaciones/:id`, declarada y sin medir,
 * el mismo dia que se escribio este archivo. Ruta nueva con parametro, entrada nueva
 * aqui; si falta, la prueba de mas abajo lo dice.
 */
const EJEMPLOS: Readonly<Record<string, string>> = {
  ':id': '00000000-0000-7000-8000-000000000000',
};

/**
 * Las direcciones literales que declara la tabla de rutas.
 *
 * <p>Fuera el comodin y la raiz, que se comprueba en portada.spec.ts. Las que llevan
 * parametro entran con su valor de ejemplo.
 */
const rutasDeclaradas = (): string[] => {
  const codigo = readFileSync(rutaDelArchivo('app.routes.ts'), 'utf8');
  const literales = [...codigo.matchAll(/path:\s*'([^']*)'/g)].map((c) => c[1]);

  return literales.filter((ruta) => ruta !== '' && ruta !== '**').map((ruta) => concretar(ruta));
};

/** Sustituye cada segmento con parametro por su valor de ejemplo. */
const concretar = (ruta: string): string =>
  ruta
    .split('/')
    .map((segmento) => (segmento.startsWith(':') ? (EJEMPLOS[segmento] ?? segmento) : segmento))
    .join('/');

/**
 * Las dos familias que la tabla no escribe como literal: las genera a partir de las
 * constantes de `core/routes`, asi que de ahi salen tambien aqui.
 */
const rutasGeneradas = (): string[] => [
  ...PAGINAS_DE_CONTENIDO.map((id) => RUTAS_CONTENIDO[id].slice(1)),
  ...DOCUMENTOS_LEGALES.map((id) => RUTAS_LEGALES[id].slice(1)),
];

const TODAS = [...new Set([...rutasDeclaradas(), ...rutasGeneradas()])].sort();

test.describe('rutas declaradas', () => {
  // Si la extraccion se rompiera y devolviera una lista vacia, el `for` de abajo no
  // generaria ninguna prueba y la suite pasaria sin comprobar nada.
  test('se encontraron rutas que comprobar', () => {
    expect(TODAS.length).toBeGreaterThan(10);
    expect(TODAS).toContain('verificacion-de-vendedor');
  });

  /**
   * Ninguna ruta puede quedarse sin medir por no tener valor de ejemplo. Sin esto, un
   * `:algo` nuevo se pediria tal cual, respondaria lo que respondiera, y nadie lo notaria.
   */
  test('toda ruta con parametro tiene un valor de ejemplo', () => {
    expect(TODAS.filter((ruta) => ruta.includes(':'))).toEqual([]);
  });

  for (const ruta of TODAS) {
    test(`/${ruta} se sirve con estado 200`, async ({ request }) => {
      const respuesta = await request.get(`/${ruta}`);

      expect(respuesta.status(), `/${ruta} no esta en app.routes.server.ts`).toBe(200);
    });
  }
});
