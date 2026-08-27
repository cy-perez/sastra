import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChildren,
} from '@angular/core';
import { NgOptimizedImage } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ListingStore } from '../application/listing.store';
import { precioFormateado, tomasDelVendedor, type Listing } from '../../../shared/domain/listing';

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
  imports: [NgOptimizedImage, RouterLink, TranslocoPipe],
  templateUrl: './my-listings-page.html',
  styleUrl: './my-listings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyListingsPage {
  private readonly store = inject(ListingStore);
  private readonly inyector = inject(Injector);

  protected readonly consulta = this.store.mine;
  protected readonly pausa = this.store.pause;
  protected readonly reanudacion = this.store.resume;
  protected readonly archivo = this.store.archive;

  /** Cuál está esperando confirmación de archivo. Nunca hay dos a la vez. */
  protected readonly porArchivar = signal<string | null>(null);

  /**
   * Los botones de archivar y el de confirmar, en el orden de las filas.
   *
   * <p>Por {@code viewChildren} y no buscando en el documento: así el foco no puede
   * acabar en un elemento de otra parte de la página, y no hace falta tocar
   * {@code document}, que en el servidor no existe.
   *
   * <p>Del de confirmar solo hay uno a la vez —{@code porArchivar} guarda una sola
   * publicación—, así que el primero es el que hay. Los de archivar salen en el mismo
   * orden que {@code publicaciones()}, y por eso se puede volver al de la fila correcta
   * sin preguntarle nada al DOM.
   */
  private readonly botonesDeArchivar = viewChildren<ElementRef<HTMLButtonElement>>('archivarBoton');

  private readonly botonDeConfirmar = viewChildren<ElementRef<HTMLButtonElement>>('confirmarBoton');

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

  /** Pausar y reanudar solo tienen sentido sobre algo que se ve o se veia. */
  protected admitePausa(publicacion: Listing): boolean {
    return publicacion.status === 'PUBLISHED' || publicacion.status === 'PAUSED';
  }

  protected alternarPausa(publicacion: Listing): void {
    if (publicacion.status === 'PAUSED') {
      this.reanudacion.mutate(publicacion.id);
    } else {
      this.pausa.mutate(publicacion.id);
    }
  }

  /**
   * Abre la confirmación y lleva el foco a ella.
   *
   * <p>Es lo más parecido a un diálogo de toda la historia: el botón que la abre se
   * destruye al abrirla, así que sin mover el foco se quedaría en el body y quien navega
   * con teclado no sabría que ha pasado nada.
   */
  protected pedirConfirmacion(id: string): void {
    this.porArchivar.set(id);
    this.enfocar(() => this.botonDeConfirmar()[0]);
  }

  /** Al cancelar, el foco vuelve a donde estaba: al botón de archivar de esa fila. */
  protected cancelarArchivo(): void {
    const cancelada = this.porArchivar();
    this.porArchivar.set(null);

    const fila = this.publicaciones().findIndex((publicacion) => publicacion.id === cancelada);
    this.enfocar(() => (fila === -1 ? undefined : this.botonesDeArchivar()[fila]));
  }

  protected archivar(id: string): void {
    this.archivo.mutate(id, { onSettled: () => this.porArchivar.set(null) });
  }

  /**
   * Lleva el foco a donde diga la función, después de pintar.
   *
   * <p>Se resuelve tras pintar porque antes el elemento no existe: los dos casos cambian
   * de rama del {@code @if} justo al pulsarlos.
   */
  private enfocar(donde: () => ElementRef<HTMLButtonElement> | undefined): void {
    afterNextRender(() => donde()?.nativeElement.focus(), { injector: this.inyector });
  }

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }
}
