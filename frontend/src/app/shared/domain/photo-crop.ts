/**
 * El recorte a 3:4 de toda foto de producto. RN-018 y RN-019, HU-003 criterio 5.
 *
 * TypeScript puro: sin Angular y sin `rxjs`, para que se pruebe sin TestBed
 * (frontend/CLAUDE.md). Aquí está solo la aritmética; quien la aplica sobre píxeles es
 * `shared/infrastructure/photo-normalizer.worker.ts`, que es donde vive el lienzo.
 *
 * <p>Vive en `shared` porque lo usan las dos entradas de imagen y una funcionalidad no
 * importa de otra: la cámara del asistente y la carga desde galería pasan por el mismo
 * recorte, que es lo que pide el criterio 8 —«el mismo recorte forzado»—.
 *
 * <p><strong>Esto no valida, genera.</strong> Que la imagen subida cumpla la proporción y
 * el mínimo lo comprueba el servidor sobre los bytes que recibe, y esa es la respuesta que
 * manda (ADR-0018). Lo de aquí existe para que lo que se envíe ya cumpla, no para dar una
 * segunda opinión. La única excepción es {@link cumpleMinimo}, que sí es una puerta: RN-019
 * dice que por debajo de 900 x 1200 «el formulario no deja continuar», y esa negativa hay
 * que darla antes de gastar una subida.
 */

/** La proporción del catálogo, ancho entre alto. De ella depende que la rejilla cuadre. */
export const RELACION_FOTO = 3 / 4;

/** RN-019. Es el mínimo y también el tamaño de salida: ver {@link cumpleMinimo}. */
export const ANCHO_MINIMO = 900;
export const ALTO_MINIMO = 1200;

/** Un rectángulo en píxeles de la imagen de origen. */
export interface Rectangulo {
  readonly x: number;
  readonly y: number;
  readonly ancho: number;
  readonly alto: number;
}

/**
 * El rectángulo 3:4 más grande que cabe en la imagen de origen, centrado.
 *
 * <p>Centrado y no ajustado al contenido: reconocer dónde está la prenda es justo lo que
 * la historia deja fuera del alcance. La silueta del asistente existe para que el producto
 * ya venga centrado cuando se dispara, y así el centro geométrico y el del producto son el
 * mismo (criterio 2).
 *
 * <p>Se redondea hacia abajo para que el rectángulo nunca se salga del origen por un píxel
 * de redondeo, que es un error que el lienzo paga con un borde transparente.
 */
export function rectanguloDeRecorte(ancho: number, alto: number): Rectangulo {
  if (ancho <= 0 || alto <= 0) {
    throw new Error('La imagen de origen no tiene tamaño');
  }

  const sobraAncho = ancho / alto > RELACION_FOTO;

  const anchoRecorte = sobraAncho ? Math.floor(alto * RELACION_FOTO) : ancho;
  const altoRecorte = sobraAncho ? alto : Math.floor(ancho / RELACION_FOTO);

  return {
    x: Math.floor((ancho - anchoRecorte) / 2),
    y: Math.floor((alto - altoRecorte) / 2),
    ancho: anchoRecorte,
    alto: altoRecorte,
  };
}

/**
 * Si el recorte da para los 900 x 1200 de RN-019.
 *
 * <p>Se mide sobre el recorte y no sobre el origen: una foto de 4000 x 4000 tiene de sobra
 * y aun así su recorte 3:4 es de 3000 x 4000, mientras que una de 1000 x 1000 se queda en
 * 750 x 1000 y **no** cumple, aunque los dos lados del original pasaran el mínimo.
 *
 * <p>Cuando cumple, la salida se reduce a 900 x 1200 exactos. **Nunca se amplía**: estirar
 * una imagen pequeña la haría pasar el mínimo sin tener el detalle que el mínimo existe
 * para exigir, y el moderador acabaría rechazando por ilegible lo que el formulario dejó
 * pasar. Por eso el mínimo es también el tamaño de salida y no hay una constante aparte.
 */
export function cumpleMinimo(recorte: Rectangulo): boolean {
  return recorte.ancho >= ANCHO_MINIMO && recorte.alto >= ALTO_MINIMO;
}
