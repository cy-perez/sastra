/**
 * El giro del visor 360: de cuánto se arrastró a qué fotograma se ve. HU-003 criterio 12.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md). Es la «lógica de mapeo de desplazamiento a índice de fotograma»
 * que la historia pide probar aparte, y está aparte justamente por eso: dentro del
 * componente habría que montar un gesto para comprobar una resta.
 */

/**
 * Cuánto hay que arrastrar para dar una vuelta entera, en anchos del visor.
 *
 * <p>Uno: cruzar el visor de lado a lado gira el producto una vuelta completa. Con menos,
 * un gesto normal se pasa de largo y el producto da varios giros; con más, no se llega a
 * ver la espalda sin soltar y volver a arrastrar. Al ser relativo al ancho, el gesto se
 * siente igual en un teléfono estrecho y en una pantalla grande.
 */
const VUELTAS_POR_ANCHO = 1;

/**
 * El fotograma que toca ver.
 *
 * <p>El sentido es el del criterio 12: **arrastrar a la derecha avanza el índice**. Las
 * posiciones crecen conforme el producto enseña su lado derecho —la 2 son 90 grados, que
 * es la lateral derecha (RN-017)—, así que empujar hacia la derecha trae ese lado al
 * frente y el giro acompaña al dedo. El signo está aquí y en ningún otro sitio: si algún
 * día se mide que la gente espera lo contrario, se cambia en esta línea y no en el
 * componente.
 *
 * @param desplazamiento píxeles recorridos desde que empezó el gesto, positivo a la derecha
 * @param ancho ancho del visor en píxeles
 * @param fotogramas cuántas tomas tiene la secuencia
 * @param indiceInicial el fotograma que se veía al empezar el gesto
 */
export function indiceDesde(
  desplazamiento: number,
  ancho: number,
  fotogramas: number,
  indiceInicial: number,
): number {
  if (fotogramas <= 0) {
    throw new Error('Una secuencia sin fotogramas no se puede girar');
  }

  // Un visor de ancho cero es un visor que aún no se ha medido. Pasa en el primer
  // fotograma tras hidratar, antes de que el diseño se asiente, y dividir por él daría
  // un infinito que acabaría en `NaN`. No girar es la respuesta correcta: todavía no hay
  // gesto que interpretar.
  if (ancho <= 0) {
    return normalizar(indiceInicial, fotogramas);
  }

  const pixelesPorFotograma = ancho / (fotogramas * VUELTAS_POR_ANCHO);
  const pasos = Math.round(desplazamiento / pixelesPorFotograma);

  return normalizar(indiceInicial + pasos, fotogramas);
}

/**
 * El índice dentro de la secuencia, girando en redondo.
 *
 * <p>Con el resto de JavaScript no basta: `-1 % 8` es `-1` y no `7`, así que arrastrar a
 * la izquierda desde el frontal se saldría del arreglo. Sumar el módulo antes de volver a
 * aplicarlo es lo que cierra el círculo, que es lo que un visor 360 tiene que hacer.
 */
export function normalizar(indice: number, fotogramas: number): number {
  return ((indice % fotogramas) + fotogramas) % fotogramas;
}
