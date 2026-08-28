import { Injectable } from '@angular/core';

import { aGrises, estaNitida } from '../domain/blur';

/**
 * Mide si una foto está lo bastante nítida. Criterio 2 de HU-002.
 *
 * <p>Está aquí y no en `shared` a propósito. La regla que aplica es de la verificación de
 * identidad —una cédula borrosa no se puede leer, y rechazarla en el cliente le ahorra a
 * la persona esperar una subida para que se la rechacen— y **ningún criterio de HU-003 la
 * pide**: el asistente de captura de producto se guarda de otra cosa, del temblor y la
 * inclinación, con el nivel de los 5 grados. Cuando HU-003 subió `CameraService` a
 * `shared`, esto se quedó donde se usa.
 *
 * <p>Es un servicio y no una función suelta por lo mismo que la cámara: decodificar una
 * imagen y leer sus píxeles no existe en jsdom, así que el componente se prueba con un
 * doble de esta clase. La decisión que sí es nuestra —dónde está el umbral y cómo se
 * calcula la varianza del laplaciano— vive en `domain/blur.ts`, que se prueba entera y
 * sin navegador.
 */
@Injectable({ providedIn: 'root' })
export class SharpnessService {
  /**
   * Ancho al que se reduce el fotograma para medir.
   *
   * <p>**La nitidez se mide sobre una copia pequeña** porque la varianza del laplaciano
   * depende de la resolución: medida sobre el original, el mismo umbral significaría cosas
   * distintas en un teléfono de gama alta y en uno modesto, y el umbral dejaría de querer
   * decir nada.
   */
  private static readonly ANCHO_DE_MEDIDA = 480;

  /**
   * Si la foto pasa el umbral.
   *
   * <p>Recibe el JPEG ya codificado y lo vuelve a decodificar, que es un paso más que
   * antes, cuando la cámara devolvía los píxeles medidos de camino. Se paga a cambio de
   * que la cámara sirva a las dos funcionalidades sin arrastrar a HU-003 un cálculo que no
   * usa. Y compra algo: por aquí pasa también lo que el criterio 3 no ofrece pero tampoco
   * puede impedir, porque mide sobre la imagen y no sobre su procedencia.
   */
  async estaNitida(imagen: Blob): Promise<boolean> {
    const original = await createImageBitmap(imagen);

    try {
      const ancho = Math.min(SharpnessService.ANCHO_DE_MEDIDA, original.width);
      const alto = Math.max(3, Math.round((original.height / original.width) * ancho));

      const lienzo = new OffscreenCanvas(ancho, alto);
      const contexto = lienzo.getContext('2d');

      if (contexto === null) {
        throw new Error('El navegador no dio contexto de dibujo');
      }

      contexto.drawImage(original, 0, 0, ancho, alto);
      const datos = contexto.getImageData(0, 0, ancho, alto);

      return estaNitida(aGrises(datos.data, ancho, alto));
    } finally {
      // Un `ImageBitmap` retiene su mapa de bits hasta que se cierra. Sin esto, cada foto
      // repetida deja una copia entera del fotograma en memoria.
      original.close();
    }
  }
}
