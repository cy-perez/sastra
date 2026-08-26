import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ListingStore } from '../application/listing.store';
import { precioFormateado, tomasDelVendedor, type Listing } from '../domain/listing';

/**
 * Las publicaciones propias del vendedor. HU-007.
 *
 * <p>Es la pantalla desde la que se llega a los borradores, y por eso pinta el estado de
 * cada una con su explicación: quien vuelve al cabo de unos días no se acuerda de si la
 * envió o la dejó a medias.
 *
 * <p>Las acciones que no exigen abrir la publicación —pausar, reactivar y archivar— se
 * ofrecen desde aquí. <strong>Archivar pide confirmación</strong>, y es la única de toda
 * la historia que lo hace: es la única acción del vendedor que no se puede deshacer.
 */
@Component({
  selector: 'sendik-my-listings-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './my-listings-page.html',
  styleUrl: './my-listings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyListingsPage {
  private readonly store = inject(ListingStore);

  protected readonly consulta = this.store.mine;
  protected readonly pausa = this.store.pause;
  protected readonly reanudacion = this.store.resume;
  protected readonly archivo = this.store.archive;

  /** Cuál está esperando confirmación de archivo. Nunca hay dos a la vez. */
  protected readonly porArchivar = signal<string | null>(null);

  protected readonly publicaciones = computed<readonly Listing[]>(() => this.consulta.data() ?? []);

  protected readonly vacio = computed(
    () => this.consulta.isSuccess() && this.publicaciones().length === 0,
  );

  private readonly idiomas = inject(TranslocoService);

  /** El precio ya formateado, en la configuración regional activa. */
  protected precioDe(publicacion: Listing): string | null {
    const precio = publicacion.product.price;
    return precio === null ? null : precioFormateado(precio, this.idiomas.getActiveLang());
  }

  protected portadaDe(publicacion: Listing): string | null {
    return tomasDelVendedor(publicacion)[0]?.url ?? null;
  }

  protected pausar(id: string): void {
    this.pausa.mutate(id);
  }

  protected reanudar(id: string): void {
    this.reanudacion.mutate(id);
  }

  protected pedirConfirmacion(id: string): void {
    this.porArchivar.set(id);
  }

  protected cancelarArchivo(): void {
    this.porArchivar.set(null);
  }

  protected archivar(id: string): void {
    this.archivo.mutate(id, { onSettled: () => this.porArchivar.set(null) });
  }

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }
}
