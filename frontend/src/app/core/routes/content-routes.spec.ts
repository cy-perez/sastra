import { describe, expect, it } from 'vitest';

import en from '../../../i18n/en.json';
import es from '../../../i18n/es.json';
import { routes } from '../../app.routes';
import { serverRoutes } from '../../app.routes.server';
import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from './content-routes';

/**
 * Las cuatro paginas informativas de HU-005, criterios 1, 3 y 25.
 *
 * <p>Se prueba la tabla de rutas y no cada componente porque lo que puede
 * romperse aqui es el cableado: una direccion escrita a mano en un enlace, una
 * ruta que se olvida de declarar su descripcion, o una que no entra en la lista
 * del servidor y se sirve con 404 aunque se pinte entera.
 */
describe('rutas de las paginas informativas', () => {
  const rutaDe = (path: string) => routes.find((ruta) => ruta.path === path);

  it('declara las cuatro paginas', () => {
    expect(PAGINAS_DE_CONTENIDO).toHaveLength(4);
    expect(new Set(PAGINAS_DE_CONTENIDO).size).toBe(4);
  });

  it('todas las direcciones empiezan por barra y van en espanol', () => {
    for (const id of PAGINAS_DE_CONTENIDO) {
      expect(RUTAS_CONTENIDO[id]).toMatch(/^\/[a-z-]+$/);
    }
  });

  it('cada pagina tiene su ruta en la tabla del router', () => {
    for (const id of PAGINAS_DE_CONTENIDO) {
      expect(rutaDe(RUTAS_CONTENIDO[id].slice(1))).toBeDefined();
    }
  });

  // Criterio 3: cada una declara su titulo y su descripcion como claves de
  // Transloco, que es lo que TranslatedTitleStrategy resuelve en el servidor.
  it('cada ruta declara titulo y descripcion como claves', () => {
    for (const id of PAGINAS_DE_CONTENIDO) {
      const ruta = rutaDe(RUTAS_CONTENIDO[id].slice(1));

      expect(ruta?.title).toBe(`meta.${id}.title`);
      expect(ruta?.data?.['descriptionKey']).toBe(`meta.${id}.description`);
    }
  });

  /**
   * La comprobacion de arriba usa la misma formula que app.routes.ts, asi que
   * por si sola compara la produccion consigo misma. Lo que de verdad puede
   * romperse es que la clave este declarada y **no exista** en los arboles: el
   * buscador indexaria literalmente "meta.faq.title" como titulo de la pagina.
   */
  it('las claves de titulo y descripcion existen en los dos idiomas', () => {
    const texto = (arbol: Record<string, unknown>, clave: string) =>
      clave.split('.').reduce<unknown>((nodo, parte) => {
        return typeof nodo === 'object' && nodo !== null
          ? (nodo as Record<string, unknown>)[parte]
          : undefined;
      }, arbol);

    for (const id of PAGINAS_DE_CONTENIDO) {
      for (const arbol of [es, en]) {
        for (const sufijo of ['title', 'description']) {
          const valor = texto(arbol, `meta.${id}.${sufijo}`);

          expect(typeof valor, `meta.${id}.${sufijo}`).toBe('string');
          expect((valor as string).trim().length, `meta.${id}.${sufijo}`).toBeGreaterThan(0);
        }
      }
    }
  });

  it('cada pagina se carga de forma diferida', () => {
    for (const id of PAGINAS_DE_CONTENIDO) {
      expect(rutaDe(RUTAS_CONTENIDO[id].slice(1))?.loadComponent).toBeTypeOf('function');
    }
  });

  /**
   * Lo que le paso a /ingresar y esta escrito en app.routes.server.ts: una ruta
   * que falta en esta lista cae en el comodin y se sirve con estado 404 aunque
   * la pagina se pinte entera. El visitante no nota nada y el buscador la
   * descarta, que es justo lo contrario de para lo que existen estas paginas.
   */
  it('ninguna cae en el comodin del servidor', () => {
    const declaradas = new Set(serverRoutes.map((ruta) => ruta.path));

    for (const id of PAGINAS_DE_CONTENIDO) {
      expect(declaradas.has(RUTAS_CONTENIDO[id].slice(1))).toBe(true);
    }
  });

  // Criterio 25. No se puede enlazar lo que no existe, y desde aqui no se puede
  // ni nombrar: catalogo, publicacion y busqueda son de Fase 2 y 3.
  it('ninguna direccion apunta a catalogo, publicacion ni busqueda', () => {
    const prohibidas = /catalogo|productos|publicar|buscar|carrito/;

    for (const id of PAGINAS_DE_CONTENIDO) {
      expect(RUTAS_CONTENIDO[id]).not.toMatch(prohibidas);
    }
  });

  it('las cuatro cargan un componente distinto', async () => {
    const componentes = await Promise.all(
      PAGINAS_DE_CONTENIDO.map((id) => {
        const cargar = rutaDe(RUTAS_CONTENIDO[id].slice(1))?.loadComponent;
        return cargar?.() as Promise<unknown>;
      }),
    );

    expect(new Set(componentes).size).toBe(4);
  });
});
