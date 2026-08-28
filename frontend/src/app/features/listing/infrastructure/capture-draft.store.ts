import { Injectable } from '@angular/core';

/**
 * Una toma congelada que todavía no llegó al servidor.
 *
 * <p>Se guarda **tal como sale de la cámara**, antes del recorte. El recorte ocurre dentro
 * de la subida, que es el mismo camino para la cámara y para la galería; adelantarlo aquí
 * obligaría a recortar dos veces (ADR-0027).
 */
export interface TomaGuardada {
  readonly posicion: number;
  readonly imagen: Blob;
}

/** Lo que de verdad se escribe: la toma, su clave y cuándo se guardó. */
interface FilaGuardada {
  readonly clave: string;
  readonly imagen: Blob;
  /** Milisegundos desde la época. Es lo único que permite barrer por antigüedad. */
  readonly guardadaEn: number;
}

/**
 * Cuánto vive un borrador sin tocarse.
 *
 * <p>Siete días. Una captura es una sesión de minutos, así que una semana es holgado para
 * lo que el criterio 7 promete —retomar lo que se estaba haciendo— y corto para que nada
 * se quede indefinidamente. Sin este barrido, una publicación abandonada dejaría sus
 * megabytes en el dispositivo hasta que el navegador reclamara la cuota.
 */
const VIDA_DEL_BORRADOR_MS = 7 * 24 * 60 * 60 * 1000;

/**
 * El avance del asistente, guardado en el dispositivo. HU-003 criterio 7.
 *
 * <p>«Cerrar el navegador por accidente no obliga a empezar de nuevo». Lo que se guarda son
 * las tomas que ya se congelaron y aún no se subieron; en cuanto una sube, el servidor pasa
 * a ser la fuente y aquí se borra.
 *
 * <p>**En IndexedDB y no en el almacén de clave y valor del navegador** (ADR-0027): ocho
 * fotogramas de cámara son varios megabytes, aquel da unos cinco en total y **almacena
 * texto**, así que codificarlos crecería un tercio más y no cabrían. IndexedDB guarda
 * `Blob` tal cual, y sin bloquear el hilo principal.
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
      almacen.put({
        clave: clave(publicacionId, toma.posicion),
        imagen: toma.imagen,
        guardadaEn: Date.now(),
      } satisfies FilaGuardada);
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

    // Se barre de paso, en la misma transacción: es el único momento en que este almacén
    // se abre, así que es el único momento en que se puede limpiar lo de otras
    // publicaciones sin abrirlo aparte.
    await this.conElAlmacen('readwrite', (almacen) => {
      const cursor = almacen.openCursor();

      cursor.onsuccess = () => {
        const puntero = cursor.result;
        if (puntero === null) {
          return;
        }

        const fila = puntero.value as FilaGuardada;

        if (Date.now() - (fila.guardadaEn ?? 0) > VIDA_DEL_BORRADOR_MS) {
          puntero.delete();
        } else if (fila.clave.startsWith(prefijo)) {
          guardadas.push({ posicion: posicionDe(fila.clave), imagen: fila.imagen });
        }
        puntero.continue();
      };
    });

    return guardadas.sort((una, otra) => una.posicion - otra.posicion);
  }

  /**
   * Tira el borrador entero de una publicación.
   *
   * <p>Se llama al salir del asistente: lo que no llegó a subirse en esa sesión ya no se
   * va a subir, y dejarlo ahí son megabytes por publicación abandonada.
   */
  async limpiar(publicacionId: string): Promise<void> {
    const prefijo = `${publicacionId}:`;

    await this.conElAlmacen('readwrite', (almacen) => {
      const cursor = almacen.openCursor();

      cursor.onsuccess = () => {
        const puntero = cursor.result;
        if (puntero === null) {
          return;
        }
        if ((puntero.value as FilaGuardada).clave.startsWith(prefijo)) {
          puntero.delete();
        }
        puntero.continue();
      };
    });
  }

  /**
   * Borra todo lo guardado, de cualquier publicación.
   *
   * <p>Es lo que se llama al cerrar sesión. El borrador vive en el origen y no en la
   * sesión, así que sin esto las fotos de quien acaba de salir siguen ahí, legibles desde
   * las herramientas del navegador, para quien entre después en el mismo equipo.
   */
  async borrarTodo(): Promise<void> {
    await this.conElAlmacen('readwrite', (almacen) => {
      almacen.clear();
    });
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
