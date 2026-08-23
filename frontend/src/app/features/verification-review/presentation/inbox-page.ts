import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ReviewStore } from '../application/review.store';
import { hayDiscrepanciaDeTitular } from '../domain/pending-verification';

/**
 * La bandeja del moderador: lo que espera revisión, lo más viejo primero. HU-006.
 *
 * <p>Pantalla interna, no pública. No lleva el acento ocre, que está reservado a la
 * acción principal de las pantallas de cara al comprador.
 *
 * <p>No decide nada: desde aquí se entra al detalle, y es allí donde se aprueba o se
 * rechaza. Poner los botones en la lista invitaría a decidir sin haber mirado las
 * imágenes, que es exactamente lo que esta pantalla existe para que no pase.
 */
@Component({
  selector: 'sastra-inbox-page',
  imports: [TranslocoPipe, RouterLink, DatePipe],
  templateUrl: './inbox-page.html',
  styleUrl: './review.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPage {
  private readonly store = inject(ReviewStore);

  protected readonly consulta = this.store.inbox;
  protected readonly pendientes = this.store.pendientes;

  /** Criterio 7: se señala también en la lista, para saber qué mirar antes de abrir. */
  protected readonly discrepa = hayDiscrepanciaDeTitular;

  protected reintentar(): void {
    void this.consulta.refetch();
  }
}
