import { describe, expect, it } from 'vitest';

import en from '../../../i18n/en.json';
import es from '../../../i18n/es.json';

/**
 * Guardas sobre el texto del sitio informativo (HU-005).
 *
 * <p>No comprueban que la redaccion sea buena, sino que no diga cosas que el
 * proyecto ya decidio no decir. Existen porque el riesgo real no es escribirlo
 * mal hoy: es la correccion bienintencionada de dentro de seis meses, cuando
 * nadie recuerde por que esa palabra no estaba y le parezca que falta.
 *
 * <p>Se ejecutan sobre los dos idiomas. La web en ingles se sirve en la misma
 * direccion y en Colombia lo que se anuncia es exigible, sin importar el idioma
 * en que se anuncie.
 */
describe('texto de las paginas informativas', () => {
  /**
   * La portada entra aqui aunque sea de HU-004: dice las mismas promesas y con
   * las mismas reglas detras, y una guarda que no la cubre deja fuera justo la
   * pagina que mas gente lee. De hecho el fallo de "paid on delivery" estaba
   * ahi, no en estas cuatro.
   */
  const RAMAS = ['home', 'howItWorks', 'about', 'faq', 'contact', 'meta', 'layout'] as const;

  const textos = (arbol: unknown, prefijo = ''): [string, string][] => {
    if (typeof arbol === 'string') {
      return [[prefijo, arbol]];
    }
    if (typeof arbol !== 'object' || arbol === null) {
      return [];
    }
    return Object.entries(arbol).flatMap(([nombre, valor]) =>
      textos(valor, prefijo === '' ? nombre : `${prefijo}.${nombre}`),
    );
  };

  const delSitio = (arbol: Record<string, unknown>) =>
    RAMAS.flatMap((rama) => textos(arbol[rama], rama));

  const TODOS = [...delSitio(es), ...delSitio(en)];

  const incumplen = (patron: RegExp) =>
    TODOS.filter(([, texto]) => patron.test(texto)).map(([clave]) => clave);

  /**
   * Criterio 17. El plazo de desembolso al vendedor **no esta decidido**: se
   * define en Fase 3 (docs/producto/alcance.md). Cualquier cifra de dias escrita
   * aqui seria una promesa inventada.
   *
   * <p>La ventana de reclamo si existe (RN-051), pero su numero sale de
   * configuracion y llega por interpolacion, asi que tampoco puede aparecer
   * escrito. Por eso el patron no distingue: ninguna cifra de dias, ninguna.
   */
  it('no escribe ningun plazo en cifras', () => {
    // Hasta dos palabras entre la cifra y la unidad, y la forma con guion. Sin
    // eso el patron solo veia el espanol —donde el sustantivo va pegado— y
    // dejaba pasar "within 3 business days" o "a 30-day window", que es
    // justamente como se escribiria el error en ingles.
    expect(incumplen(/\b\d+\s*-?\s*(?:\w+\s+){0,2}(d[ií]as?|days?|horas?|hours?)\b/i)).toEqual([]);
  });

  /**
   * Los numeros escritos con letra se cuelan por debajo del patron de arriba.
   * Los diez y quince dias habiles de la Ley 1581 son la excepcion legitima: no
   * los decide Sastra, los fija la ley, y el titular tiene derecho a saberlos.
   */
  it('solo enuncia con letra los plazos que fija la ley de datos', () => {
    const conLetra = incumplen(
      /\b(un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|treinta|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|thirty)\s+(?:\w+\s+){0,2}(d[ií]as?|days?)\b/i,
    );

    // El patron tiene que estar viendo algo: los plazos de la Ley 1581 estan
    // escritos con letra en los dos idiomas. Si esta lista sale vacia es que la
    // expresion se rompio, no que el texto este limpio, y `every` sobre un
    // arreglo vacio es verdadero.
    expect(conLetra.length).toBeGreaterThan(0);
    expect(conLetra.filter((clave) => !clave.startsWith('contact.rights'))).toEqual([]);
    // Los dos arboles, no solo el espanol: "ten business days" no lo veia el
    // patron anterior, asi que el ingles estaba sin guarda.
    expect(conLetra.length).toBeGreaterThanOrEqual(2);
  });

  /**
   * Las palabras que el glosario prohibe. Las cuatro primeras describen figuras
   * financieras que Sastra no ejerce y tienen lectura regulatoria en Colombia
   * (RN-031); las demas contradicen la promesa del producto, que es seguridad y
   * no precio.
   */
  it('no usa ninguna palabra prohibida por el glosario', () => {
    /*
     * Una fila del glosario por entrada, con sus variantes morfologicas y su
     * equivalente en ingles. El patron anterior solo veia la forma exacta
     * ("garantía" si, "garantizamos" no) y de sus diez alternativas apenas
     * cuatro podian aparecer en ingles, asi que en.json estaba practicamente
     * sin guarda.
     *
     * Con \p{L} y no \w: "garantías" lleva tilde, y \w no la cubre, asi que
     * `garant\w+` se paraba en la "t" y dejaba pasar justo el plural.
     */
    const prohibidas: [string, RegExp][] = [
      ['escrow, custodia, fideicomiso', /escrow|custodi\p{L}+|fideicomiso|safekeep\w+/iu],
      ['garantía', /garant\p{L}+|guarantee\w*|warrant(y|ies)\b/iu],
      // Solo el sustantivo: "un seguro", "el seguro". El adjetivo es legitimo y
      // esta en la portada a proposito ("Por que es seguro"), que es la promesa
      // del producto.
      ['seguro', /\b(un|una|el|la|los|las|tu|tus|su|sus|nuestro|nuestra)\s+seguros?\b|\binsur\w+/i],
      [
        'compra protegida',
        /compra\s+protegida|protecci[óo]n\s+al\s+comprador|te\s+protegemos|buyer\s+protection|protected\s+purchase/i,
      ],
      // "saldo" y "balance" se quedan fuera a proposito: faq.seller.payout.a los
      // usa negados, para decir que el desembolso va a la cuenta bancaria y no a
      // un saldo dentro de la plataforma. Prohibirlos borraria esa aclaracion.
      ['plata, billete, billetera', /\bplata\b|\bbillete(s|ra)?\b|\bwallet\b/i],
      ['ganga, barato', /\bganga\b|\bbarat\p{L}*|\bbargain\w*|\bcheap\w*|\bliquidaci[óo]n\b/iu],
    ];

    for (const [fila, patron] of prohibidas) {
      expect(incumplen(patron), fila).toEqual([]);
    }
  });

  /**
   * La frase que nunca puede escribirse. Sastra no guarda el dinero: lo recauda
   * y lo retiene la pasarela (RN-031, RN-033). Es la misma guarda que HU-004
   * pide para la portada, extendida al sitio informativo.
   */
  it('no dice que Sastra guarde el dinero', () => {
    expect(incumplen(/sastra\s+(guarda|retiene|custodia|holds|keeps)/i)).toEqual([]);
  });

  /**
   * RN-032: el pago contraentrega no esta habilitado. En ingles "paid on
   * delivery" significa exactamente eso, asi que la frase solo puede aparecer
   * negada. Es el error que ya esta en la portada.
   */
  it('no sugiere pago contraentrega', () => {
    const sugiere = TODOS.filter(
      ([, texto]) =>
        /\b(paid|pay|payment)\s+on\s+delivery\b/i.test(texto) && !/\bnot\b/i.test(texto),
    ).map(([clave]) => clave);

    expect(sugiere).toEqual([]);
  });

  /**
   * RN-038: la cotizacion de envio se muestra siempre rotulada como aproximada.
   * Donde se hable de lo que cuesta el envio, tiene que aparecer la palabra.
   */
  it('rotula la cotizacion de envio como aproximada', () => {
    // Solo los cuerpos: un titulo de tres palabras no lleva la advertencia, y
    // exigirsela obligaria a escribir "Pagas producto mas envio aproximado",
    // que no es lo que se paga.
    const sobreEnvio = TODOS.filter(
      ([clave]) => /shippingCost|steps\.pay/.test(clave) && /\.(body|a)$/.test(clave),
    );

    expect(sobreEnvio.length).toBeGreaterThan(0);
    for (const [clave, texto] of sobreEnvio) {
      expect(texto, clave).toMatch(/aproximad|approximate/i);
    }
  });

  /**
   * Criterio 16. La respuesta a "y si me llega algo distinto" tiene que explicar
   * cuatro hechos **y en este orden**: el pago sigue retenido porque el
   * comprador no ha confirmado (RN-034), la ventana para reportar (RN-051), que
   * el reintegro sale de ese dinero retenido y no depende de que el vendedor
   * colabore (RN-054), y que confirmar la entrega cierra la ventana.
   *
   * <p>El orden es parte del criterio y no un capricho de redaccion: quien
   * escribe preocupado por su dinero necesita leer primero que no se ha ido a
   * ninguna parte. Se comprueba por posicion, no por presencia.
   */
  it('la respuesta sobre producto no conforme da los cuatro hechos en orden', () => {
    const HECHOS: Record<'es' | 'en', RegExp[]> = {
      es: [
        /pago sigue retenido/i,
        /\{\{dias\}\}\s+d[ií]as h[áa]biles/i,
        /reintegro sale de ese dinero[^.]*no depende de que el vendedor colabore/i,
        /confirmar la entrega cierra la ventana/i,
      ],
      en: [
        /payment stays held/i,
        /\{\{dias\}\}\s+business days/i,
        /refund comes out of that money[^.]*does not depend on the seller cooperating/i,
        /confirming delivery closes the window/i,
      ],
    };

    for (const [idioma, patrones] of Object.entries(HECHOS) as ['es' | 'en', RegExp[]][]) {
      const texto = (idioma === 'es' ? es : en).faq.buyer.claim.a;
      const posiciones = patrones.map((patron) => texto.search(patron));

      expect(posiciones, `${idioma}: falta alguno de los cuatro hechos`).not.toContain(-1);
      expect(posiciones, `${idioma}: los cuatro hechos no van en el orden del criterio 16`).toEqual(
        [...posiciones].sort((uno, otro) => uno - otro),
      );
    }
  });
});
