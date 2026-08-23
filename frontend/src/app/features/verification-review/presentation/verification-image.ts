import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { ReviewStore } from '../application/review.store';
import type { VerificationImage as CualImagen } from '../domain/pending-verification';

/**
 * Una de las tres imágenes de una solicitud, que **solo se pide al abrirla**. HU-006.
 *
 * <p>Ese «al abrirla» es la razón de que este componente exista en vez de un `<img>`.
 * Cada lectura deja una fila en la bitácora con el actor y el motivo (RN-046,
 * criterio 6): si la ficha pidiera las tres al cargarse, quedarían registradas tres
 * lecturas que nadie hizo, y la bitácora dejaría de contar lo que pasó.
 *
 * <p>La imagen llega como bytes y no como dirección, tampoco firmada. Un enlace que
 * funciona por sí solo no puede registrar quién lo usó (ADR-0018), y por eso hay que
 * construir aquí la URL de objeto —y revocarla al destruir, o cada solicitud revisada
 * deja unos cuantos megas retenidos en el navegador—.
 */
@Component({
  selector: 'sastra-verification-image',
  imports: [TranslocoPipe],
  templateUrl: './verification-image.html',
  styleUrl: './review.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerificationImage {
  readonly solicitud = input.required<string>();
  readonly cual = input.required<CualImagen>();
  readonly etiqueta = input.required<string>();

  private readonly store = inject(ReviewStore);

  protected readonly url = signal<string | null>(null);
  protected readonly cargando = signal(false);
  protected readonly fallo = signal(false);

  constructor() {
    // La URL de objeto no se libera sola: sin esto, revisar veinte solicitudes deja
    // veinte imágenes retenidas en memoria hasta recargar la página.
    inject(DestroyRef).onDestroy(() => this.liberar());
  }

  protected async abrir(): Promise<void> {
    if (this.url() !== null || this.cargando()) {
      return;
    }

    this.cargando.set(true);
    this.fallo.set(false);

    try {
      const bytes = await this.store.imagen(this.solicitud(), this.cual());
      this.url.set(URL.createObjectURL(bytes));
    } catch {
      // Caso borde de la historia: el archivo puede faltar por un fallo de despliegue.
      // Se dice, y la solicitud sigue siendo decidible: rechazar por fotos ilegibles es
      // una respuesta válida.
      this.fallo.set(true);
    } finally {
      this.cargando.set(false);
    }
  }

  private liberar(): void {
    const actual = this.url();
    if (actual !== null) {
      URL.revokeObjectURL(actual);
    }
  }
}
