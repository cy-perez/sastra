import { Injectable, type OnDestroy } from '@angular/core';

import type { MotivoDeRechazo } from '../domain/photo-crop';
import type { PeticionDeNormalizacion, RespuestaDeNormalizacion } from './photo-normalizer.worker';

/** El rechazo del normalizador, con el motivo que la pantalla traduce. */
export class ImagenNoNormalizable extends Error {
  constructor(readonly motivo: MotivoDeRechazo) {
    super(motivo);
  }
}

/**
 * Recorta y comprime una foto antes de subirla. HU-003 criterios 5, 8 y 9.
 *
 * <p>Envuelve el worker y le da al llamador una promesa por imagen, que es lo que un
 * componente sabe esperar. Por aquí pasan **las dos entradas**: el fotograma de la cámara
 * del asistente y el archivo elegido desde la galería. Es lo que pide el criterio 8 —«el
 * mismo recorte forzado»— y de paso deja de mandar al servidor archivos que iba a rechazar
 * por proporción, que era lo que pasaba hasta hoy.
 *
 * <p>El worker se crea **perezosamente, en la primera normalización**. Así no existe
 * durante el renderizado en servidor, donde `Worker` no está definido, ni en las pantallas
 * que nunca suben una foto.
 */
@Injectable({ providedIn: 'root' })
export class PhotoNormalizer implements OnDestroy {
  private worker: Worker | null = null;
  private siguienteId = 0;

  /**
   * Lo que está en vuelo, por identificador.
   *
   * <p>Hace falta porque un worker responde con un mensaje suelto y no con la respuesta a
   * una llamada: sin el identificador no hay forma de saber a cuál de las ocho tomas
   * corresponde lo que acaba de llegar. Que el asistente normalice de una en una no
   * cambia nada: la rejilla puede lanzar varias, y una cola implícita que se confunda de
   * toma pondría una foto en la posición de otra.
   */
  private readonly enVuelo = new Map<
    number,
    { resolver: (imagen: Blob) => void; rechazar: (fallo: ImagenNoNormalizable) => void }
  >();

  /**
   * Si este entorno puede normalizar.
   *
   * <p>Se comprueba en lugar de suponerlo: esto se importa también en el paquete que
   * renderiza el servidor, donde no hay `Worker`.
   */
  soportado(): boolean {
    return typeof Worker !== 'undefined';
  }

  /**
   * Devuelve la imagen recortada a 3:4, a 900 x 1200 y por debajo de 500 KB.
   *
   * <p>Rechaza con {@link ImagenNoNormalizable} cuando la foto no da la resolución mínima
   * de RN-019, que es el caso que la pantalla tiene que explicar.
   */
  async normalizar(imagen: Blob): Promise<Blob> {
    // No es que la imagen no se pueda leer: es que este navegador no puede prepararla. El
    // texto que ve la persona no puede culpar a su foto de algo que no es suyo.
    if (!this.soportado()) {
      throw new ImagenNoNormalizable('SIN_SOPORTE');
    }

    const id = this.siguienteId++;
    const peticion: PeticionDeNormalizacion = { id, imagen };

    return new Promise<Blob>((resolver, rechazar) => {
      this.enVuelo.set(id, { resolver, rechazar });
      this.abrir().postMessage(peticion);
    });
  }

  ngOnDestroy(): void {
    this.worker?.terminate();
    this.worker = null;
  }

  private abrir(): Worker {
    if (this.worker !== null) {
      return this.worker;
    }

    // `new URL(..., import.meta.url)` y no una ruta suelta: es la forma que el empaquetador
    // reconoce para incluir el worker en la compilación y darle su propia dirección.
    const worker = new Worker(new URL('./photo-normalizer.worker', import.meta.url), {
      type: 'module',
    });

    worker.addEventListener('message', (evento: MessageEvent<RespuestaDeNormalizacion>) => {
      const respuesta = evento.data;
      const pendiente = this.enVuelo.get(respuesta.id);

      if (pendiente === undefined) {
        return;
      }
      this.enVuelo.delete(respuesta.id);

      if ('error' in respuesta) {
        pendiente.rechazar(new ImagenNoNormalizable(respuesta.error));
        return;
      }
      pendiente.resolver(respuesta.imagen);
    });

    // Si el worker se cae, lo que está en vuelo no va a contestar nunca. Sin esto, la
    // pantalla se queda con la barra de progreso girando y sin nada que explicar.
    worker.addEventListener('error', () => {
      for (const pendiente of this.enVuelo.values()) {
        pendiente.rechazar(new ImagenNoNormalizable('IMAGEN_ILEGIBLE'));
      }
      this.enVuelo.clear();
    });

    this.worker = worker;
    return worker;
  }
}
