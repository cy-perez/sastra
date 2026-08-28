import { Injectable } from '@angular/core';

/**
 * La cámara del dispositivo y el congelado de fotogramas.
 *
 * <p>Está aparte del componente por una razón práctica: `getUserMedia` y `canvas` no
 * existen en jsdom, así que un componente que los llamara directamente no se podría
 * probar. Con el acceso aquí, el componente se prueba con un doble de esta clase y esta
 * clase queda sin probar, que es lo correcto: lo que hay dentro es una llamada al
 * navegador, no una decisión nuestra.
 *
 * <p>Vive en `shared` desde HU-003 y antes vivía dentro de la verificación de vendedor.
 * Lo mueve la regla de que **una funcionalidad no importa de otra** (frontend/CLAUDE.md):
 * la usan la selfie y el documento de HU-002, y el asistente de ocho tomas de HU-003, que
 * están en dos `features` distintas. Lo que **no** subió con ella es la medida de nitidez:
 * esa es una regla de la verificación de identidad —una cédula borrosa no se puede leer— y
 * ningún criterio de HU-003 la pide, así que se quedó en `seller-verification`, que es
 * quien la usa. Aquí solo está el acceso al aparato.
 *
 * <p>Nada de esto se ejecuta durante el renderizado en servidor. Se llama solo desde un
 * gesto de la persona, y {@link soportada} comprueba el terreno antes de tocar nada
 * (frontend/CLAUDE.md prohíbe que el código de servidor toque `navigator`).
 */
@Injectable({ providedIn: 'root' })
export class CameraService {
  /**
   * Calidad del JPEG que sale de la cámara.
   *
   * <p>Alta a propósito, y en los dos usos por motivos distintos: en HU-002 se va a mirar
   * una cédula, no una miniatura; en HU-003 este fotograma todavía tiene que pasar por el
   * recorte a 3:4, y comprimir fuerte antes de recortar sería tirar detalle que el recorte
   * aún no ha decidido si sobra. El apretón hasta los 500 KB del criterio 9 lo da el
   * normalizador al final, sobre la imagen ya recortada.
   */
  static readonly CALIDAD = 0.92;

  /**
   * Si este entorno tiene cámara accesible.
   *
   * <p>Se comprueba `navigator` en lugar de suponerlo: esto se importa también en el
   * paquete que renderiza el servidor, donde no existe.
   */
  soportada(): boolean {
    return (
      typeof navigator !== 'undefined' &&
      navigator.mediaDevices !== undefined &&
      typeof navigator.mediaDevices.getUserMedia === 'function'
    );
  }

  /**
   * Pide la cámara. Lanza si la persona la deniega, y ese fallo se muestra tal cual: el
   * caso borde de HU-002 pide explicar cómo habilitarla, no reintentar en bucle.
   *
   * @param frontal la de la cara para la selfie; la de atrás para el documento y para las
   *     tomas de producto, que es la que enfoca de cerca en casi cualquier teléfono
   */
  async abrir(frontal: boolean): Promise<MediaStream> {
    return navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: frontal ? 'user' : 'environment',
        // Se pide alto, no se exige: con `exact` un teléfono modesto falla en lugar de
        // dar lo que puede, y una cédula ilegible por resolución la rechaza el moderador
        // igual que una borrosa. En HU-003 el que decide si da la talla es el mínimo de
        // RN-019, medido sobre el recorte y no sobre lo que la cámara prometa.
        width: { ideal: 1920 },
        height: { ideal: 1080 },
      },
      audio: false,
    });
  }

  /** Apaga la cámara. Sin esto, el indicador del dispositivo se queda encendido. */
  cerrar(flujo: MediaStream | null): void {
    flujo?.getTracks().forEach((pista) => pista.stop());
  }

  /**
   * Congela el fotograma que se está viendo, a la resolución que dé la cámara.
   *
   * <p>Devuelve un JPEG y no píxeles sueltos porque es lo que sus dos usos necesitan y
   * porque los iguala: la verificación lo sube tal cual, el asistente de captura lo pasa
   * por el normalizador, y **una imagen elegida desde la galería es también un `Blob`**.
   * Con eso la galería y la cámara entran por el mismo sitio, que es lo que pide el
   * criterio 8 de HU-003 —«el mismo recorte forzado»— sin tener dos caminos que mantener.
   */
  async capturar(video: HTMLVideoElement): Promise<Blob> {
    const ancho = video.videoWidth;
    const alto = video.videoHeight;

    if (ancho === 0 || alto === 0) {
      throw new Error('La cámara todavía no entrega imagen');
    }

    // `OffscreenCanvas` y no un `<canvas>` creado en el documento: el lienzo es un apaño
    // para codificar un fotograma, nunca se pinta, y colgarlo del documento para tirarlo
    // acto seguido es tocar el DOM sin necesitarlo —lo que rompe el renderizado en
    // servidor y lo que el hook de convenciones señala—. Es además el mismo lienzo que
    // usa el normalizador dentro del worker, así que el proyecto tiene una sola forma de
    // dibujar una foto y no dos.
    const lienzo = new OffscreenCanvas(ancho, alto);
    const contexto = lienzo.getContext('2d');

    if (contexto === null) {
      throw new Error('El navegador no dio contexto de dibujo');
    }

    contexto.drawImage(video, 0, 0, ancho, alto);

    return lienzo.convertToBlob({ type: 'image/jpeg', quality: CameraService.CALIDAD });
  }
}
