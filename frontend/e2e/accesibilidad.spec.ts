import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from '../src/app/core/routes/content-routes';
import { DOCUMENTOS_LEGALES, RUTAS_LEGALES } from '../src/app/core/routes/legal-routes';
import { THEME_COOKIE } from '../src/app/core/theme/theme';

/**
 * Auditoria automatizada de accesibilidad sobre las paginas publicas, en modo
 * claro y en modo oscuro. Cierra la ultima linea pendiente de HU-004 y HU-005.
 *
 * <p>Lo que hace axe y no hacen las pruebas escritas a mano de
 * `contenido.spec.ts` es encontrar lo que nadie penso: una etiqueta que se
 * perdio al refactorizar, un `aria-labelledby` que apunta a un identificador que
 * ya no existe, un contraste que se rompio al ajustar un token del modo oscuro.
 *
 * <p>Lo que **no** hace, y por eso esta suite en verde no significa que el sitio
 * sea accesible: no sabe si el orden de lectura tiene sentido, si el texto de un
 * enlace significa algo fuera de su contexto, ni si la pagina se puede usar de
 * verdad con un lector de pantalla. Eso se revisa a mano. Ver ADR-0016.
 *
 * <p>Se audita el DOM ya hidratado y no el HTML servido: ese ya tiene sus propias
 * pruebas, que son las de ssr.spec.ts, y lo que puede romper la accesibilidad
 * (un atributo que escribe un componente, un estado que cambia al abrir el menu)
 * solo existe despues de hidratar.
 *
 * <p>El idioma se fija como en el resto de las pruebas de navegador: Chromium
 * arranca en ingles y aqui se leen titulares en espanol.
 */
test.use({ locale: 'es-CO' });

/**
 * WCAG 2.2 nivel AA, que es el objetivo del proyecto, con los niveles anteriores
 * incluidos porque cada version se apoya en la anterior.
 *
 * <p>Sin `best-practice`: son recomendaciones de Deque, no criterios de la norma,
 * y mezclarlas obligaria a discutir cual se acata cada vez que falle una.
 */
const ETIQUETAS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

/**
 * Todas las paginas que alguien puede abrir sin haber entrado.
 *
 * <p>Las direcciones de las informativas y de las legales salen de sus constantes
 * y no escritas a mano: agregar una pagina la mete sola en la auditoria. Ese es
 * el modo de fallo que preocupa, porque una pagina que no esta en esta lista no
 * se audita y nadie se entera.
 *
 * <p>Faltan a proposito `/verificar-correo`, `/restablecer-contrasena`,
 * `/confirmar-correo-nuevo` y `/mi-cuenta`: las cuatro necesitan un token o una
 * sesion, y en esta suite no hay backend al que pedirlos (playwright.config.ts
 * apunta la API al puerto de descarte). Auditarlas aqui seria auditar su estado
 * de error, que no es lo que se quiere demostrar.
 */
const RUTAS_PUBLICAS: readonly { readonly ruta: string; readonly nombre: string }[] = [
  { ruta: '/', nombre: 'portada' },
  ...PAGINAS_DE_CONTENIDO.map((id) => ({ ruta: RUTAS_CONTENIDO[id], nombre: id })),
  ...DOCUMENTOS_LEGALES.map((id) => ({ ruta: RUTAS_LEGALES[id], nombre: `legal ${id}` })),
  { ruta: '/registro', nombre: 'registro' },
  { ruta: '/ingresar', nombre: 'ingreso' },
  { ruta: '/recuperar-contrasena', nombre: 'recuperacion' },
  // Las dos de publicar. **Lo que se audita aqui es su rama sin backend**: sin API,
  // el arbol de categorias falla y el listado propio se queda sin sesion, asi que lo
  // que axe recorre es el encabezado, el estado de carga y el mensaje de error. Es
  // menos que el formulario entero y sigue siendo mas que nada: son ramas que una
  // persona ve de verdad, y hasta que FEATURE_PUBLISHING se encienda no hay forma de
  // llegar al formulario en una suite sin backend.
  { ruta: '/publicar', nombre: 'publicar' },
  { ruta: '/mis-publicaciones', nombre: 'mis publicaciones' },
  // El comodin. Es una pagina como cualquier otra y se llega a ella por error,
  // que es justo cuando conviene que no este rota.
  { ruta: '/esta-ruta-no-existe', nombre: 'no encontrada' },
];

/** Los dos modos, con el valor que espera la cookie que lee el servidor. */
const MODOS = [
  { modo: 'claro', cookie: 'light', atributo: 'claro' },
  { modo: 'oscuro', cookie: 'dark', atributo: 'oscuro' },
] as const;

/**
 * Deja la pagina lista para auditar: fija el tema antes de navegar, para que el
 * servidor ya pinte el modo correcto, y espera al titular.
 *
 * <p>La comprobacion del atributo no es decorativa. Sin ella, una cookie que
 * dejara de aplicarse convertiria la mitad oscura de esta suite en una copia de
 * la clara, y seguiria en verde: el modo oscuro dejaria de auditarse sin que
 * fallara nada.
 */
async function abrirEn(page: Page, ruta: string, modo: (typeof MODOS)[number]): Promise<void> {
  await page
    .context()
    .addCookies([{ name: THEME_COOKIE, value: modo.cookie, domain: 'localhost', path: '/' }]);

  await page.goto(ruta);

  await expect(page.locator('html')).toHaveAttribute('data-tema', modo.atributo);
  await expect(page.locator('h1')).toBeVisible();
}

/**
 * El informe que se lee cuando falla. Playwright imprime el mensaje de la
 * asercion, y una lista de objetos serializados no dice donde esta el problema:
 * lo que sirve es la regla y el selector del nodo que la incumple.
 */
function informe(violaciones: Awaited<ReturnType<AxeBuilder['analyze']>>['violations']): string {
  return violaciones
    .map((violacion) => {
      const nodos = violacion.nodes.map((nodo) => `      ${nodo.target.join(' ')}`).join('\n');
      return `  ${violacion.id} [${violacion.impact ?? 'sin impacto'}]: ${violacion.help}\n${nodos}`;
    })
    .join('\n');
}

for (const modo of MODOS) {
  test.describe(`accesibilidad en modo ${modo.modo}`, () => {
    for (const { ruta, nombre } of RUTAS_PUBLICAS) {
      test(`${nombre} (${ruta}) no incumple ningun criterio WCAG 2.2 AA`, async ({ page }) => {
        await abrirEn(page, ruta, modo);

        // Ninguna regla desactivada. Una violacion se corrige en el codigo; si
        // alguna regla de verdad no aplicara a un caso, se excluye aqui y con el
        // motivo escrito al lado, nunca en la configuracion global (ADR-0016).
        const { violations } = await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG).analyze();

        expect(violations, `\n${informe(violations)}`).toEqual([]);
      });
    }
  });
}
