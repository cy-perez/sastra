/**
 * El nivel del asistente de captura. HU-003 criterios 3 y 4.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md). Quien lee el sensor es
 * `shared/infrastructure/orientation.service.ts`; aquí solo se decide qué significa lo
 * leído.
 *
 * <p>Existe porque las ocho tomas tienen que salir desde la misma altura y con la misma
 * inclinación: si cada una se dispara con el teléfono torcido de otra forma, el giro del
 * visor cabecea y se ve peor que un carrusel, que es exactamente lo que la historia dice
 * que hay que evitar.
 */

/** Criterio 3: pasado esto en cualquier eje, el obturador se deshabilita. */
export const INCLINACION_MAXIMA_GRADOS = 5;

/**
 * El teléfono en vertical, que es como se fotografía una prenda colgada.
 *
 * <p>`DeviceOrientationEvent` da `beta` en 0 con el aparato tumbado boca arriba y en 90
 * con él de pie. La referencia es 90 y no 0 porque el asistente rodea el producto en ocho
 * pasos: eso es un producto de pie y una cámara a su altura, no una foto cenital.
 */
export const BETA_DE_REFERENCIA = 90;

/** Lo que se desvía cada eje de su referencia, en grados y siempre positivo. */
export interface Desviacion {
  /** Cabeceo: el teléfono se va hacia adelante o hacia atrás. */
  readonly frontal: number;
  /** Alabeo: el teléfono se inclina hacia un lado. */
  readonly lateral: number;
}

export function desviacion(beta: number, gamma: number): Desviacion {
  return { frontal: Math.abs(beta - BETA_DE_REFERENCIA), lateral: Math.abs(gamma) };
}

/**
 * Si el aparato está lo bastante nivelado para disparar.
 *
 * <p><strong>Sin lectura se responde que sí.</strong> El criterio 4 es explícito: en iOS
 * los sensores exigen un permiso que se puede negar, y si se niega el asistente sigue sin
 * nivel y avisa de que la calidad puede variar; **nunca se bloquea la publicación por
 * esto**. Un `null` es esa situación —permiso negado, o un aparato sin acelerómetro— y
 * tratarlo como «no nivelado» dejaría el obturador muerto para siempre y sin forma de
 * salir. El aviso lo da la pantalla, que es la que sabe por qué no hay lectura.
 */
export function estaNivelado(beta: number | null, gamma: number | null): boolean {
  if (beta === null || gamma === null) {
    return true;
  }

  const { frontal, lateral } = desviacion(beta, gamma);

  return frontal <= INCLINACION_MAXIMA_GRADOS && lateral <= INCLINACION_MAXIMA_GRADOS;
}
