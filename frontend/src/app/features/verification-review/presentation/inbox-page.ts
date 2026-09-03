import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ReviewStore } from '../application/review.store';
import { hayDiscrepanciaDeTitular } from '../domain/pending-verification';

/**
 * La bandeja del moderador: lo que espera revisión, lo más viejo primero. HU-006.
 *
 * <p>Pantalla interna, no pública. No lleva el acento bronce, que está reservado
 * a lo verificado, ni compite con la acción principal de las pantallas de cara
 * al comprador.
 *
 * <p>No decide nada: desde aquí se entra al detalle, y es allí donde se aprueba o se
 * rechaza. Poner los botones en la lista invitaría a decidir sin haber mirado las
 * imágenes, que es exactamente lo que esta pantalla existe para que no pase.
 */
@Component({
  selector: 'sendik-inbox-page',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './inbox-page.html',
  styleUrl: './review.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPage {
  private readonly store = inject(ReviewStore);
  private readonly idioma = inject(TranslocoService);

  protected readonly consulta = this.store.inbox;
  protected readonly pendientes = this.store.pendientes;

  protected readonly pagina = this.store.pagina;
  protected readonly hayMas = this.store.hayMas;
  protected readonly hayAnterior = this.store.hayAnterior;

  /** Constante y no un literal en la plantilla, que se recrearía en cada ciclo. */
  protected readonly filasDelEsqueleto = [1, 2, 3];

  /** Criterio 7: se señala también en la lista, para saber qué mirar antes de abrir. */
  protected readonly discrepa = hayDiscrepanciaDeTitular;

  /**
   * Criterio 8: lo que acaba de hacerse, dicho al volver.
   *
   * <p>Viaja en el estado de la navegación y no en la dirección: es un mensaje de una
   * sola vez, y en la dirección sobreviviría a un refresco y a que alguien la comparta.
   *
   * <p>Se lee en el constructor porque `getCurrentNavigation()` solo existe durante la
   * navegación; un rato después ya devuelve `null`.
   */
  protected readonly confirmacion = signal<string | null>(null);

  constructor() {
    const estado = inject(Router).getCurrentNavigation()?.extras.state;
    const decision = estado?.['decision'];

    if (decision === 'approved' || decision === 'rejected') {
      this.confirmacion.set(`verificationReview.decision.${decision}`);
    }
  }

  /** Con la configuración regional activa, no con el `date` de Angular, que cae en inglés. */
  protected desdeCuando(iso: string): string {
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso));
  }

  protected reintentar(): void {
    void this.consulta.refetch();
  }

  /**
   * Las dos siguen siendo llamables en el borde, y no es un descuido.
   *
   * <p>Los botones se marcan con `aria-disabled` y no con `disabled`, así que en la última
   * página «Siguiente» todavía recibe el clic. Quien acota el movimiento es el estado:
   * pulsar ahí no hace nada. Comprobarlo también aquí sería repetir la misma regla en dos
   * sitios que pueden dejar de estar de acuerdo.
   */
  protected siguiente(): void {
    this.store.paginaSiguiente();
  }

  protected anterior(): void {
    this.store.paginaAnterior();
  }
}
