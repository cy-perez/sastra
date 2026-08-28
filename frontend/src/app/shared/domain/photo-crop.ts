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

/** Criterio 9: ninguna toma sale del dispositivo por encima de 500 KB. */
export const TOPE_DE_BYTES = 500 * 1024;

/**
 * Calidades de JPEG que se prueban, de mejor a peor, hasta bajar del tope.
 *
 * <p>A 900 x 1200 la primera basta casi siempre —un JPEG así ronda los 200 KB—, así que la
 * escalera rara vez pasa del primer peldaño. Las otras existen para la foto con mucho
 * detalle fino, que es donde el tope se alcanza.
 */
export const CALIDADES: readonly number[] = [0.85, 0.75, 0.65, 0.55];

/**
 * Baja la calidad hasta que la imagen cabe en el tope.
 *
 * <p>Está aquí y no dentro del worker porque **el tope es una regla de producto**, no un
 * detalle del lienzo: el criterio 9 dice el número y dice que no admite matices. Quien
 * dibuja los píxeles se inyecta, así que esto se prueba sin navegador, igual que
 * {@link cumpleMinimo}.
 *
 * <p>Si ni la calidad más baja cabe, **rechaza**. Devolver la imagen igualmente sería
 * incumplir el criterio. A 900 x 1200 y calidad 0,55 no se ha visto ocurrir —el tamaño de
 * salida es fijo y pequeño—, pero un camino que incumple un criterio no se deja escrito
 * porque se crea que no se va a recorrer.
 *
 * @param codificar entrega la imagen a una calidad dada
 */
export async function comprimirBajoTope(
  codificar: (calidad: number) => Promise<Blob>,
  calidades: readonly number[] = CALIDADES,
  tope: number = TOPE_DE_BYTES,
): Promise<Blob | null> {
  for (const calidad of calidades) {
    const candidata = await codificar(calidad);

    if (candidata.size <= tope) {
      return candidata;
    }
  }
  return null;
}

/**
 * Por qué una foto no se pudo preparar para subir.
 *
 * <p>Vive aquí y no en el worker que la produce: `RESOLUCION_INSUFICIENTE` **es RN-019**,
 * la misma regla que {@link cumpleMinimo} decide justo arriba. Que la pantalla tuviera que
 * preguntarle a una clase de infraestructura por qué se rechazó una foto era una
 * dependencia hacia afuera para leer una regla que ya estaba aquí.
 *
 * <p>Un código y no un texto: el texto visible es de Transloco y se decide en la pantalla,
 * nunca en el dominio (CLAUDE.md).
 */
export type MotivoDeRechazo =
  /** El recorte 3:4 no llega a 900 x 1200 (RN-019). Es el que de verdad se ve. */
  | 'RESOLUCION_INSUFICIENTE'
  /** Ni a la calidad más baja cabe en 500 KB (criterio 9). */
  | 'NO_SE_PUDO_COMPRIMIR'
  /** El archivo no se pudo decodificar: no era una imagen, o venía corrupto. */
  | 'IMAGEN_ILEGIBLE'
  /** Este navegador no puede preparar la foto. No es culpa de la imagen. */
  | 'SIN_SOPORTE';
