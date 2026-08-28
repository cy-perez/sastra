import { Injectable } from '@angular/core';

/** Una lectura del acelerómetro, ya en los dos ejes que le importan al nivel. */
export interface Inclinacion {
  /** Cabeceo. 0 es el aparato tumbado boca arriba, 90 es de pie. */
  readonly beta: number;
  /** Alabeo. 0 es sin ladear. */
  readonly gamma: number;
}

/**
 * En iOS los sensores exigen permiso explícito y el constructor trae este método; en el
 * resto de navegadores no existe. No está en la biblioteca de tipos del DOM porque no es
 * estándar, así que se declara lo justo para poder preguntar por él.
 */
type ConstructorConPermiso = typeof DeviceOrientationEvent & {
  requestPermission?: () => Promise<'granted' | 'denied' | 'default'>;
};

/**
 * El acelerómetro del aparato. HU-003 criterios 3 y 4.
 *
 * <p>Aparte del componente por lo mismo que {@link CameraService}: `DeviceOrientationEvent`
 * no existe en jsdom, así que un componente que lo escuchara directamente no se podría
 * probar. Aquí queda el acceso al aparato, sin decisiones; qué significa una lectura lo
 * decide `shared/domain/tilt.ts`, que se prueba entero y sin navegador.
 *
 * <p>**Todo lo que toca el navegador pasa antes por {@link soportada}**, que es la
 * comprobación de plataforma que frontend/CLAUDE.md exige: esto se importa también en el
 * paquete que renderiza el servidor, donde no hay sensores ni nada a lo que suscribirse.
 */
@Injectable({ providedIn: 'root' })
export class OrientationService {
  /** Si este entorno tiene sensor al que suscribirse. */
  soportada(): boolean {
    return typeof DeviceOrientationEvent !== 'undefined' && typeof addEventListener === 'function';
  }

  /**
   * Si hay que pedir permiso antes de escuchar. Hoy, iOS y solo iOS.
   *
   * <p>Se pregunta por el método y no por el navegador. Detectar «si es iOS» por la cadena
   * del agente de usuario es lo que envejece mal: el día que otro navegador adopte el
   * mismo permiso, preguntar por la capacidad ya funciona y olfatear la cadena no.
   */
  necesitaPermiso(): boolean {
    return (
      this.soportada() &&
      typeof (DeviceOrientationEvent as ConstructorConPermiso).requestPermission === 'function'
    );
  }

  /**
   * Pide el permiso de sensores. **Hay que llamarlo desde un gesto de la persona**: iOS
   * descarta la solicitud que no venga de uno, y lo hace en silencio.
   *
   * <p>Devuelve si se concedió. Que se niegue no es un fallo: el criterio 4 dice que el
   * asistente sigue sin nivel y avisa de que la calidad puede variar, y que **nunca se
   * bloquea la publicación por esto**. Por eso un rechazo devuelve `false` y no lanza.
   */
  async pedirPermiso(): Promise<boolean> {
    if (!this.necesitaPermiso()) {
      return this.soportada();
    }

    try {
      const solicitar = (DeviceOrientationEvent as ConstructorConPermiso).requestPermission;
      return (await solicitar?.()) === 'granted';
    } catch {
      // iOS lanza si la solicitud no salió de un gesto. Se trata como una negativa: el
      // asistente sigue sin nivel, que es lo que el criterio 4 pide para ese caso.
      return false;
    }
  }

  /**
   * Escucha la inclinación hasta que se llame a lo que devuelve.
   *
   * <p>Devolver la baja en lugar de ofrecer un `dejarDeEscuchar()` aparte es lo que
   * impide olvidarla: quien se suscribe se lleva en la mano la forma de soltarlo, y el
   * componente solo tiene que guardarlo para su `DestroyRef`.
   *
   * <p>Una lectura sin los dos ejes se descarta. El sensor los da nulos mientras se
   * calibra, y pasarlos como ceros haría que el nivel dijera «tumbado boca arriba» durante
   * el primer segundo, deshabilitando el obturador sin motivo.
   */
  escuchar(alLeer: (inclinacion: Inclinacion) => void): () => void {
    if (!this.soportada()) {
      return () => undefined;
    }

    const escucha = (evento: DeviceOrientationEvent): void => {
      if (evento.beta === null || evento.gamma === null) {
        return;
      }
      alLeer({ beta: evento.beta, gamma: evento.gamma });
    };

    addEventListener('deviceorientation', escucha);

    return () => removeEventListener('deviceorientation', escucha);
  }
}
