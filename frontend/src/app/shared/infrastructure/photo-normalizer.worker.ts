import { ALTO_MINIMO, ANCHO_MINIMO, cumpleMinimo, rectanguloDeRecorte } from '../domain/photo-crop';

/**
 * El recorte y la compresión de una foto de producto, fuera del hilo principal.
 * HU-003 criterios 5, 8 y 9, y el caso borde del dispositivo de gama baja.
 *
 * <p>En un worker porque la historia lo pide y por lo que la historia teme: recortar y
 * recodificar una foto de doce megapíxeles bloquea el hilo principal casi un segundo en un
 * teléfono modesto, y hacerlo ocho veces seguidas es un asistente que parece colgado justo
 * mientras la persona lo está usando. Aquí ese trabajo no congela la interfaz.
 *
 * <p>No decide nada: la aritmética del recorte y el mínimo de RN-019 viven en
 * `shared/domain/photo-crop.ts`, que se prueba sin navegador. Esto solo la aplica sobre
 * píxeles. Y **no valida** lo que el servidor va a comprobar de todos modos sobre los
 * bytes que reciba (ADR-0018): genera la imagen que se envía.
 */

/** Lo que entra: una imagen de cualquier procedencia, con el número que la identifica. */
export interface PeticionDeNormalizacion {
  readonly id: number;
  readonly imagen: Blob;
}

/** Lo que sale: la imagen lista, o el motivo por el que no se pudo. */
export type RespuestaDeNormalizacion =
  | { readonly id: number; readonly imagen: Blob }
  | { readonly id: number; readonly error: MotivoDeRechazo };

/**
 * Por qué no se pudo normalizar.
 *
 * <p>Un código y no un texto: el texto visible es de Transloco y se decide en la pantalla,
 * nunca aquí (CLAUDE.md).
 */
export type MotivoDeRechazo =
  /** El recorte 3:4 no llega a 900 x 1200 (RN-019). Es el que de verdad se ve. */
  | 'RESOLUCION_INSUFICIENTE'
  /** Ni a la calidad más baja cabe en 500 KB. Ver {@link comprimir}. */
  | 'NO_SE_PUDO_COMPRIMIR'
  /** El archivo no se pudo decodificar: no era una imagen, o venía corrupto. */
  | 'IMAGEN_ILEGIBLE';

/**
 * Lo que este worker usa de su ámbito global.
 *
 * <p>Declarado a mano en lugar de traer la biblioteca `webworker` con una referencia de
 * tipos: este archivo se compila con el mismo `tsconfig.app.json` que el resto, que ya
 * trae la del DOM, y cargar las dos deja `postMessage` y `addEventListener` con dos
 * declaraciones distintas cada uno. Nombrar aquí lo que se usa —dos métodos— cuesta menos
 * que un segundo `tsconfig` y además deja escrito cuál es la superficie del worker.
 */
interface AmbitoDelWorker {
  postMessage(mensaje: RespuestaDeNormalizacion): void;
  addEventListener(
    tipo: 'message',
    escucha: (evento: MessageEvent<PeticionDeNormalizacion>) => void,
  ): void;
}

const ambito = self as unknown as AmbitoDelWorker;

/** Un rechazo con su motivo, para que el catch de arriba no tenga que adivinarlo. */
class ErrorDeNormalizacion extends Error {
  constructor(readonly motivo: MotivoDeRechazo) {
    super(motivo);
  }
}

/**
 * Calidades que se prueban, de mejor a peor, hasta bajar del tope.
 *
 * <p>A 900 x 1200 la primera basta casi siempre —un JPEG así ronda los 200 KB—, así que
 * el bucle rara vez da más de una vuelta. Las otras existen para la foto con mucho detalle
 * fino, que es donde el tope se alcanza.
 */
const CALIDADES = [0.85, 0.75, 0.65, 0.55] as const;

/** Criterio 9: ninguna toma sale del dispositivo por encima de 500 KB. */
const TOPE_DE_BYTES = 500 * 1024;

ambito.addEventListener('message', (evento) => {
  const { id, imagen } = evento.data;

  normalizar(imagen).then(
    (normalizada) => ambito.postMessage({ id, imagen: normalizada }),
    (fallo: unknown) =>
      ambito.postMessage({
        id,
        error: fallo instanceof ErrorDeNormalizacion ? fallo.motivo : 'IMAGEN_ILEGIBLE',
      }),
  );
});

/**
 * Recorta a 3:4, reduce a 900 x 1200 y comprime por debajo del tope.
 *
 * <p>El recorte y la reducción son un solo `drawImage`: dibujar el rectángulo de origen
 * directamente sobre el lienzo de destino evita un lienzo intermedio del tamaño de la
 * foto original, que en un teléfono modesto es justo la memoria que no sobra.
 */
async function normalizar(imagen: Blob): Promise<Blob> {
  const original = await createImageBitmap(imagen).catch(() => {
    throw new ErrorDeNormalizacion('IMAGEN_ILEGIBLE');
  });

  try {
    const recorte = rectanguloDeRecorte(original.width, original.height);

    // RN-019. Se comprueba antes de dibujar: si no da la talla, no se gasta el trabajo.
    if (!cumpleMinimo(recorte)) {
      throw new ErrorDeNormalizacion('RESOLUCION_INSUFICIENTE');
    }

    const lienzo = new OffscreenCanvas(ANCHO_MINIMO, ALTO_MINIMO);
    const contexto = lienzo.getContext('2d');

    if (contexto === null) {
      throw new ErrorDeNormalizacion('IMAGEN_ILEGIBLE');
    }

    // Sin esto, reducir de 3000 px a 900 con el remuestreo rápido deja los bordes
    // dentados, que en una foto de ropa se ve como tejido sucio.
    contexto.imageSmoothingEnabled = true;
    contexto.imageSmoothingQuality = 'high';

    contexto.drawImage(
      original,
      recorte.x,
      recorte.y,
      recorte.ancho,
      recorte.alto,
      0,
      0,
      ANCHO_MINIMO,
      ALTO_MINIMO,
    );

    return await comprimir(lienzo);
  } finally {
    // Un `ImageBitmap` retiene su mapa de bits hasta que se cierra. Sin esto, ocho tomas
    // dejan ocho fotos enteras en memoria dentro del worker.
    original.close();
  }
}

/**
 * Baja la calidad hasta caber en el tope.
 *
 * <p>Si ni la más baja cabe, **rechaza**. Devolver la imagen igualmente sería incumplir el
 * criterio 9, que no admite matices: ninguna toma sale del dispositivo por encima de 500
 * KB. A 900 x 1200 y calidad 0,55 no se ha visto ocurrir —el tamaño de salida es fijo y
 * pequeño—, pero un camino que incumple un criterio no se deja escrito porque se crea que
 * no se va a recorrer.
 */
async function comprimir(lienzo: OffscreenCanvas): Promise<Blob> {
  for (const calidad of CALIDADES) {
    const candidata = await lienzo.convertToBlob({ type: 'image/jpeg', quality: calidad });

    if (candidata.size <= TOPE_DE_BYTES) {
      return candidata;
    }
  }

  throw new ErrorDeNormalizacion('NO_SE_PUDO_COMPRIMIR');
}
