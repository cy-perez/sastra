import { Injectable } from '@angular/core';

/**
 * Una toma congelada que todavía no llegó al servidor.
 *
 * <p>Se guarda ya normalizada —recortada a 3:4 y por debajo de 500 KB—: es lo que se va a
 * subir, y volver a normalizarla al retomar sería repetir el trabajo caro.
 */
export interface TomaGuardada {
  readonly posicion: number;
  readonly imagen: Blob;
}

/**
 * El avance del asistente, guardado en el dispositivo. HU-003 criterio 7.
 *
 * <p>«Cerrar el navegador por accidente no obliga a empezar de nuevo». Lo que se guarda son
 * las tomas que ya se congelaron y aún no se subieron; en cuanto una sube, el servidor pasa
 * a ser la fuente y aquí se borra.
 *
 * <p>**En IndexedDB y no en el almacén de clave y valor del navegador** (ADR-0027): ocho
 * JPEG de hasta 500 KB son cuatro megabytes, aquel da unos cinco en total y **almacena
 * texto**, así que codificarlos crecería un tercio más y no cabrían. IndexedDB guarda
 * `Blob` tal cual.
 *
 * <p>Todo método aguanta que el almacén no exista o falle. Una ventana privada, un
 * navegador con el almacenamiento bloqueado o el disco lleno hacen que esto no funcione, y
 * ninguna de las tres puede impedir publicar: sin borrador, el asistente pide las ocho
 * tomas de nuevo, que es exactamente como estaba antes de HU-003. **Un fallo aquí no
 * interrumpe la captura.**
 */
@Injectable({ providedIn: 'root' })
export class CaptureDraftStore {
  private static readonly BASE = 'sendik-captura';
  private static readonly ALMACEN = 'tomas';
  private static readonly VERSION = 1;

  /** Si este entorno puede guardar. En el renderizado en servidor, no. */
  soportado(): boolean {
    return typeof indexedDB !== 'undefined';
  }

  /** Guarda una toma del borrador, o no hace nada si el almacén falla. */
  async guardar(publicacionId: string, toma: TomaGuardada): Promise<void> {
    await this.conElAlmacen('readwrite', (almacen) => {
      almacen.put({ clave: clave(publicacionId, toma.posicion), imagen: toma.imagen });
    });
  }

  /** Olvida una toma: subió, o la persona la descartó. */
  async olvidar(publicacionId: string, posicion: number): Promise<void> {
    await this.conElAlmacen('readwrite', (almacen) => {
      almacen.delete(clave(publicacionId, posicion));
    });
  }

  /**
   * Lo que quedó guardado de una publicación.
   *
   * <p>Devuelve vacío cuando no hay nada **y también cuando el almacén falla**. Para quien
   * llama son la misma situación: no hay borrador que retomar.
   */
  async recuperar(publicacionId: string): Promise<readonly TomaGuardada[]> {
    const guardadas: TomaGuardada[] = [];
    const prefijo = `${publicacionId}:`;

    await this.conElAlmacen('readonly', (almacen) => {
      const cursor = almacen.openCursor();

      cursor.onsuccess = () => {
        const puntero = cursor.result;
        if (puntero === null) {
          return;
        }

        const fila = puntero.value as { clave: string; imagen: Blob };
        if (fila.clave.startsWith(prefijo)) {
          guardadas.push({ posicion: posicionDe(fila.clave), imagen: fila.imagen });
        }
        puntero.continue();
      };
    });

    return guardadas.sort((una, otra) => una.posicion - otra.posicion);
  }

  /** Tira el borrador entero. Se llama al terminar la secuencia. */
  async limpiar(publicacionId: string): Promise<void> {
    const guardadas = await this.recuperar(publicacionId);

    for (const toma of guardadas) {
      await this.olvidar(publicacionId, toma.posicion);
    }
  }

  /**
   * Abre la base, hace el trabajo y espera a que la transacción cierre.
   *
   * <p>Se espera a la transacción y no a cada petición: es lo que garantiza que al volver
   * de aquí lo escrito está en disco. Sin eso, guardar la última toma y cerrar la pestaña
   * acto seguido perdería justo la que se acababa de tomar, que es el caso que el criterio
   * 7 existe para cubrir.
   */
  private async conElAlmacen(
    modo: IDBTransactionMode,
    trabajo: (almacen: IDBObjectStore) => void,
  ): Promise<void> {
    if (!this.soportado()) {
      return;
    }

    try {
      const base = await this.abrir();

      try {
        await new Promise<void>((listo, fallo) => {
          const transaccion = base.transaction(CaptureDraftStore.ALMACEN, modo);

          transaccion.oncomplete = () => listo();
          transaccion.onerror = () => fallo(transaccion.error);
          transaccion.onabort = () => fallo(transaccion.error);

          trabajo(transaccion.objectStore(CaptureDraftStore.ALMACEN));
        });
      } finally {
        base.close();
      }
    } catch {
      // Almacenamiento bloqueado, ventana privada o disco lleno. Se sigue sin borrador:
      // no poder guardar no puede impedir publicar.
    }
  }

  private abrir(): Promise<IDBDatabase> {
    return new Promise((listo, fallo) => {
      const peticion = indexedDB.open(CaptureDraftStore.BASE, CaptureDraftStore.VERSION);

      peticion.onupgradeneeded = () => {
        const base = peticion.result;
        if (!base.objectStoreNames.contains(CaptureDraftStore.ALMACEN)) {
          base.createObjectStore(CaptureDraftStore.ALMACEN, { keyPath: 'clave' });
        }
      };

      peticion.onsuccess = () => listo(peticion.result);
      peticion.onerror = () => fallo(peticion.error);
      peticion.onblocked = () => fallo(new Error('La base quedó bloqueada por otra pestaña'));
    });
  }
}

/**
 * La clave de una toma.
 *
 * <p>Lleva la publicación dentro porque el borrador es por publicación: quien tiene dos a
 * medias no puede ver las tomas de una aparecer en la otra.
 */
function clave(publicacionId: string, posicion: number): string {
  return `${publicacionId}:${posicion}`;
}

function posicionDe(clave: string): number {
  return Number(clave.slice(clave.lastIndexOf(':') + 1));
}
