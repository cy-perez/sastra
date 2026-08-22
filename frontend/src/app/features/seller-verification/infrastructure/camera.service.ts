import { Injectable } from '@angular/core';

import { aGrises, type ImagenEnGrises } from '../domain/blur';

/** Un fotograma congelado: lo que se sube y lo que se mide. */
export interface Fotograma {
  /** Lo que se manda al servidor. JPEG: es una foto, no un gráfico. */
  readonly imagen: Blob;
  /** El mismo fotograma reducido y en grises, para medir la nitidez. */
  readonly grises: ImagenEnGrises;
}

/**
 * La cámara y el congelado de fotogramas. Criterios 2 y 3 de HU-002.
 *
 * <p>Está aparte del componente por una razón práctica: `getUserMedia` y `canvas` no
 * existen en jsdom, así que un componente que los llamara directamente no se podría
 * probar. Con el acceso aquí, el componente se prueba con un doble de esta clase y esta
 * clase queda sin probar, que es lo correcto: lo que hay dentro es una llamada al
 * navegador, no una decisión nuestra.
 *
 * <p>Nada de esto se ejecuta durante el renderizado en servidor. Se llama solo desde un
 * gesto de la persona, y {@link soportada} comprueba el terreno antes de tocar nada
 * (frontend/CLAUDE.md prohíbe que el código de servidor toque `navigator`).
 */
@Injectable({ providedIn: 'root' })
export class CameraService {
  /** Ancho al que se reduce el fotograma para medir la nitidez. */
  private static readonly ANCHO_DE_MEDIDA = 480;

  /** Calidad del JPEG. Alta: se va a mirar una cédula, no una miniatura. */
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
   * @param frontal la de la cara para la selfie; la de atrás para el documento, que es
   *     la que enfoca de cerca en casi cualquier teléfono
   */
  async abrir(frontal: boolean): Promise<MediaStream> {
    return navigator.mediaDevices.getUserMedia({
      video: {
        facingMode: frontal ? 'user' : 'environment',
        // Se pide alto, no se exige: con `exact` un teléfono modesto falla en lugar de
        // dar lo que puede, y una cédula ilegible por resolución la rechaza el moderador
        // igual que una borrosa.
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
   * Congela el fotograma que se está viendo.
   *
   * <p>Se dibuja dos veces a propósito: una a tamaño completo, que es lo que se sube, y
   * otra reducida, que es lo que se mide. **La nitidez se mide sobre la copia pequeña**
   * porque la varianza del laplaciano depende de la resolución: medida sobre el original,
   * el mismo umbral significaría cosas distintas en un teléfono de gama alta y en uno
   * modesto, y el umbral dejaría de querer decir nada.
   */
  async capturar(video: HTMLVideoElement): Promise<Fotograma> {
    const ancho = video.videoWidth;
    const alto = video.videoHeight;

    if (ancho === 0 || alto === 0) {
      throw new Error('La cámara todavía no entrega imagen');
    }

    const completo = document.createElement('canvas');
    completo.width = ancho;
    completo.height = alto;
    contextoDe(completo).drawImage(video, 0, 0, ancho, alto);

    const anchoReducido = Math.min(CameraService.ANCHO_DE_MEDIDA, ancho);
    const altoReducido = Math.max(3, Math.round((alto / ancho) * anchoReducido));

    const reducido = document.createElement('canvas');
    reducido.width = anchoReducido;
    reducido.height = altoReducido;
    contextoDe(reducido).drawImage(video, 0, 0, anchoReducido, altoReducido);

    const datos = contextoDe(reducido).getImageData(0, 0, anchoReducido, altoReducido);

    return {
      imagen: await comoJpeg(completo),
      grises: aGrises(datos.data, anchoReducido, altoReducido),
    };
  }
}

function contextoDe(lienzo: HTMLCanvasElement): CanvasRenderingContext2D {
  const contexto = lienzo.getContext('2d');
  if (contexto === null) {
    throw new Error('El navegador no dio contexto de dibujo');
  }
  return contexto;
}

function comoJpeg(lienzo: HTMLCanvasElement): Promise<Blob> {
  return new Promise((entregar, fallar) => {
    lienzo.toBlob(
      (blob) =>
        blob === null ? fallar(new Error('No se pudo codificar la imagen')) : entregar(blob),
      'image/jpeg',
      CameraService.CALIDAD,
    );
  });
}
