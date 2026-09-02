import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';

/** Cuánto vive una intención antes de descartarse sola. */
const VIGENCIA_MS = 10 * 60 * 1000;

const CLAVE = 'sendik.favorito.pendiente';

interface IntencionGuardada {
  readonly listingId: string;
  readonly cuando: number;
}

/**
 * La intención de marcar un favorito que quedó pendiente del ingreso. HU-011, criterios 8,
 * 9 y 10. ADR-0029.
 *
 * <p>Quien no tiene sesión ve el control igual (criterio 7); al pulsarlo se le lleva a
 * entrar, y al volver el favorito ya tiene que estar guardado sin que vuelva a pulsar.
 * Entre las dos cosas hay una recarga: el token de acceso vive en memoria y se pierde
 * (ADR-0003), así que la intención tiene que sobrevivir fuera de ella.
 *
 * <p><strong>`sessionStorage` y no `localStorage`</strong>, que es lo que el proyecto usa
 * para el tema y el idioma. Aquellas son preferencias que deben durar entre visitas; esta
 * es una intención que debe morir con la pestaña. Con `localStorage`, quien abandona el
 * ingreso y vuelve tres días después se encontraría marcando algo que ya no recuerda haber
 * querido.
 *
 * <p><strong>Y no va en la URL.</strong> A dónde volver sí —es navegación, y tiene que
 * funcionar con el botón de atrás—; qué gesto había pendiente no, porque un enlace que
 * hace que alguien marque algo al entrar es una acción ejecutada desde fuera. El porqué
 * completo está en ADR-0029.
 *
 * <p><strong>Todo acceso es tolerante a fallo.</strong> En una pestaña de incógnito o con
 * el almacenamiento bloqueado, `sessionStorage` lanza al tocarlo. El coste de perder una
 * intención es que la persona pulse otra vez; el de una excepción sin capturar es que la
 * ficha no se pinte.
 *
 * <p>En el servidor de renderizado no existe, y por eso cada método comprueba la
 * plataforma antes de tocarlo.
 */
@Injectable({ providedIn: 'root' })
export class FavoriteIntent {
  private readonly enElNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  /** Deja anotado que, al volver con sesión, hay que guardar esta publicación. */
  recordar(listingId: string): void {
    this.escribir({ listingId, cuando: Date.now() });
  }

  /**
   * Devuelve la intención pendiente para esta publicación **y la borra**.
   *
   * <p>Se borra siempre y antes de usarla, no después de que la petición salga bien: si se
   * borrara al terminar, un fallo de red dejaría la intención puesta y el favorito
   * reaparecería la próxima vez que alguien entrara desde ese navegador. Es exactamente lo
   * que la historia advierte.
   *
   * <p>Solo la devuelve si es de esta misma publicación. Una intención sobre otra ficha no
   * se consume aquí: se queda hasta que se visite la suya, se descarte o venza.
   */
  consumir(listingId: string): boolean {
    const pendiente = this.leer();

    if (pendiente === null) {
      return false;
    }
    if (pendiente.listingId !== listingId) {
      return false;
    }

    this.descartar();
    return true;
  }

  /**
   * Borra lo que hubiera.
   *
   * <p>Lo llama la ficha cuando la sesión resuelve **anónima**, que es lo que significa
   * volver atrás sin haber entrado: el criterio 9 pide que entonces no quede nada. También
   * se llama al consumirla.
   */
  descartar(): void {
    if (!this.enElNavegador) {
      return;
    }
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      // Sin almacenamiento no había nada que borrar.
    }
  }

  private leer(): IntencionGuardada | null {
    if (!this.enElNavegador) {
      return null;
    }

    try {
      const guardado = sessionStorage.getItem(CLAVE);
      if (guardado === null) {
        return null;
      }

      const intencion = JSON.parse(guardado) as IntencionGuardada;

      // Vencida: se descarta sola. Sin esto, una pestaña abierta toda la mañana
      // guardaría al entrar algo que se pulsó horas antes.
      if (typeof intencion.cuando !== 'number' || Date.now() - intencion.cuando > VIGENCIA_MS) {
        this.descartar();
        return null;
      }

      return typeof intencion.listingId === 'string' ? intencion : null;
    } catch {
      // Un valor corrupto no es una intención. Se quita para no volver a tropezar.
      this.descartar();
      return null;
    }
  }

  private escribir(intencion: IntencionGuardada): void {
    if (!this.enElNavegador) {
      return;
    }
    try {
      sessionStorage.setItem(CLAVE, JSON.stringify(intencion));
    } catch {
      // Sin almacenamiento la intención se pierde y la persona pulsa otra vez. Es peor
      // que funcione a medias en silencio que perderla: nada queda a medio guardar.
    }
  }
}
