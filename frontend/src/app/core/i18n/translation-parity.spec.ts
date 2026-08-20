import { describe, expect, it } from 'vitest';

import en from '../../../i18n/en.json';
import es from '../../../i18n/es.json';

/**
 * Los dos arboles de traduccion tienen que tener exactamente las mismas claves.
 *
 * <p>Sin esta prueba, una clave que se agrega solo en espanol no rompe nada
 * visible: Transloco devuelve la clave misma, y en pantalla aparece
 * `home.hero.note` en vez del texto. Se descubre cuando alguien navega en
 * ingles, que es justo lo que casi nadie hace al revisar un cambio.
 *
 * <p>Cubre el criterio 19 de HU-004 y el 4 de HU-005, y protege a todo el sitio,
 * no solo a la portada.
 */
describe('paridad de traducciones', () => {
  const claves = (arbol: unknown, prefijo = ''): string[] => {
    if (typeof arbol !== 'object' || arbol === null) {
      return [prefijo];
    }
    return Object.entries(arbol).flatMap(([nombre, valor]) =>
      claves(valor, prefijo === '' ? nombre : `${prefijo}.${nombre}`),
    );
  };

  const enEspanol = claves(es).sort();
  const enIngles = claves(en).sort();

  it('no hay ninguna clave en espanol que falte en ingles', () => {
    expect(enEspanol.filter((clave) => !enIngles.includes(clave))).toEqual([]);
  });

  it('no hay ninguna clave en ingles que falte en espanol', () => {
    expect(enIngles.filter((clave) => !enEspanol.includes(clave))).toEqual([]);
  });

  // Una clave que existe pero apunta a una cadena vacia deja el hueco igual que
  // si faltara, y ademas pasa la comprobacion de arriba.
  it('ningun texto queda vacio en ninguno de los dos idiomas', () => {
    const vacias = (arbol: unknown, prefijo = ''): string[] => {
      if (typeof arbol === 'string') {
        return arbol.trim() === '' ? [prefijo] : [];
      }
      if (typeof arbol !== 'object' || arbol === null) {
        return [];
      }
      return Object.entries(arbol).flatMap(([nombre, valor]) =>
        vacias(valor, prefijo === '' ? nombre : `${prefijo}.${nombre}`),
      );
    };

    expect(vacias(es)).toEqual([]);
    expect(vacias(en)).toEqual([]);
  });

  /**
   * Las claves pueden coincidir y los marcadores no. Borrar `{{comision}}` de la
   * version inglesa deja la pagina anunciando "commission" sin cifra: la clave
   * existe, el texto no esta vacio, y las tres comprobaciones de arriba pasan.
   * Es el mismo agujero que la paridad viene a tapar, un nivel mas abajo.
   */
  it('cada clave usa los mismos marcadores en los dos idiomas', () => {
    const marcadores = (arbol: unknown, prefijo = ''): [string, string[]][] => {
      if (typeof arbol === 'string') {
        const encontrados = [...arbol.matchAll(/\{\{\s*([\w.]+)\s*\}\}/g)]
          .map((coincidencia) => coincidencia[1] ?? '')
          .sort();
        return encontrados.length > 0 ? [[prefijo, encontrados]] : [];
      }
      if (typeof arbol !== 'object' || arbol === null) {
        return [];
      }
      return Object.entries(arbol).flatMap(([nombre, valor]) =>
        marcadores(valor, prefijo === '' ? nombre : `${prefijo}.${nombre}`),
      );
    };

    const deIngles = new Map(marcadores(en));
    const discrepancias = marcadores(es)
      .filter(([clave, propios]) => (deIngles.get(clave) ?? []).join(',') !== propios.join(','))
      .map(([clave, propios]) => `${clave}: es=[${propios}] en=[${deIngles.get(clave) ?? []}]`);

    // La lista tiene que estar viendo algo: hay claves con marcadores en el
    // arbol. Si sale vacia es que la expresion se rompio.
    expect(marcadores(es).length).toBeGreaterThan(0);
    expect(discrepancias).toEqual([]);
  });
});
