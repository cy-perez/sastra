/**
 * Detección de desenfoque en el cliente. Criterio 2 de HU-002: «una imagen borrosa se
 * rechaza en el cliente antes de subirla».
 *
 * TypeScript puro sobre un arreglo de píxeles: sin `canvas`, sin Angular y sin DOM, para
 * que se pruebe con datos sintéticos y sin navegador (frontend/CLAUDE.md).
 */

/** Una imagen en escala de grises, tal como sale de leer un fotograma. */
export interface ImagenEnGrises {
  readonly ancho: number;
  readonly alto: number;
  /** Un byte por píxel, en filas de arriba abajo. Longitud `ancho * alto`. */
  readonly pixeles: Uint8Array;
}

/**
 * Umbral por omisión de la varianza del laplaciano.
 *
 * **Es un valor de arranque, no una regla de negocio.** Nadie ha decidido cuánto
 * desenfoque es demasiado para la foto de una cédula, igual que nadie decidió un mínimo
 * de píxeles para el avatar. Este número separa una foto claramente movida de una
 * legible en las pruebas, y lo que decide de verdad si el documento se lee es el
 * moderador: para eso existe el motivo de rechazo `ILLEGIBLE_PHOTOS`.
 *
 * Si se afina, la decisión va a `reglas-negocio.md` y no aquí.
 */
export const UMBRAL_DE_NITIDEZ = 60;

/**
 * Cuánto detalle tiene la imagen, medido como varianza del laplaciano.
 *
 * El laplaciano es la segunda derivada: mide cambios de cambio, o sea bordes. Una foto
 * nítida tiene bordes marcados y por tanto valores dispersos —varianza alta—; una movida
 * los tiene difusos y sus valores se agrupan cerca de cero.
 *
 * Se recorre solo el interior porque el laplaciano necesita los cuatro vecinos, y el
 * borde de la imagen no los tiene. Con imágenes de menos de tres píxeles de lado no hay
 * interior y no hay nada que medir: devuelve cero, que se lee como «no se puede afirmar
 * que sea nítida».
 */
export function varianzaDelLaplaciano(imagen: ImagenEnGrises): number {
  const { ancho, alto, pixeles } = imagen;

  if (ancho < 3 || alto < 3 || pixeles.length < ancho * alto) {
    return 0;
  }

  let suma = 0;
  let sumaDeCuadrados = 0;
  let cuenta = 0;

  for (let y = 1; y < alto - 1; y++) {
    for (let x = 1; x < ancho - 1; x++) {
      const centro = y * ancho + x;

      const laplaciano =
        4 * (pixeles[centro] ?? 0) -
        (pixeles[centro - 1] ?? 0) -
        (pixeles[centro + 1] ?? 0) -
        (pixeles[centro - ancho] ?? 0) -
        (pixeles[centro + ancho] ?? 0);

      suma += laplaciano;
      sumaDeCuadrados += laplaciano * laplaciano;
      cuenta++;
    }
  }

  if (cuenta === 0) {
    return 0;
  }

  const media = suma / cuenta;
  return sumaDeCuadrados / cuenta - media * media;
}

/** Si la imagen pasa el umbral de nitidez. */
export function estaNitida(imagen: ImagenEnGrises, umbral: number = UMBRAL_DE_NITIDEZ): boolean {
  return varianzaDelLaplaciano(imagen) >= umbral;
}

/**
 * Convierte los cuatro canales de un fotograma a un byte por píxel.
 *
 * Los coeficientes son los de luminancia percibida: el ojo humano ve el verde mucho más
 * que el azul, así que promediar los tres canales a partes iguales daría un gris que no
 * se parece al brillo que la persona vio. Importa aquí porque la nitidez se mide sobre
 * ese brillo.
 */
export function aGrises(
  rgba: Uint8Array | Uint8ClampedArray,
  ancho: number,
  alto: number,
): ImagenEnGrises {
  const pixeles = new Uint8Array(ancho * alto);

  for (let i = 0; i < pixeles.length; i++) {
    const canal = i * 4;
    pixeles[i] = Math.round(
      0.299 * (rgba[canal] ?? 0) + 0.587 * (rgba[canal + 1] ?? 0) + 0.114 * (rgba[canal + 2] ?? 0),
    );
  }

  return { ancho, alto, pixeles };
}
