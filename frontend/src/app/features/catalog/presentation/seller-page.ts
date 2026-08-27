import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { CatalogStore } from '../application/catalog.store';
import { ProductCard } from './product-card';

/**
 * El perfil público de un vendedor. HU-009, criterios 18 a 21.
 *
 * <p><strong>Aquí no aparece ningún dato personal más allá del nombre y la foto</strong>, y
 * no porque esta pantalla se acuerde de no pintarlos: la respuesta no los trae. El backend
 * responde `SellerProfileResponse`, que tiene tres campos y ninguno donde quepa un correo.
 *
 * <p>Sin reseñas: son Fase 3. El perfil dice quién es y qué vende, no qué tal le fue a
 * nadie.
 */
@Component({
  selector: 'sendik-seller-page',
  standalone: true,
  imports: [ProductCard, RouterLink, TranslocoPipe],
  templateUrl: './seller-page.html',
  styleUrl: './seller-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerPage {
  private readonly store = inject(CatalogStore);
  private readonly idioma = inject(TranslocoService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  private readonly parametros = toSignal(inject(ActivatedRoute).paramMap);

  protected readonly id = computed(() => this.parametros()?.get('id') ?? null);

  protected readonly vendedor = computed(() => this.store.vendedor.data() ?? null);
  protected readonly cargando = computed(() => this.store.vendedor.isPending());

  /** Criterio 19: no existe, no es de nadie y cuenta cerrada responden lo mismo. */
  protected readonly noEncontrado = computed(() => this.store.vendedor.isError());

  protected readonly publicaciones = this.store.publicacionesDelVendedor;
  protected readonly cargandoPublicaciones = computed(() => this.store.deVendedor.isPending());

  /** Criterio 20: sin nada publicado se dice, y no es un error. */
  protected readonly vacio = computed(
    () => !this.cargandoPublicaciones() && this.publicaciones().length === 0,
  );

  protected readonly hayMas = computed(() => this.store.deVendedor.hasNextPage());

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.store.abrirPerfil(id));
    });

    effect(() => {
      const quien = this.vendedor();
      const idioma = this.idioma.getActiveLang();

      if (quien === null) {
        return;
      }

      untracked(() => this.rotular(quien.name, idioma));
    });
  }

  protected verMas(): void {
    this.store.siguienteTramoDelVendedor();
  }

  private rotular(nombre: string, idioma: string): void {
    const rotulo = this.idioma.translate('meta.sellerProfile.title', { nombre });
    this.title.setTitle(rotulo);
    this.meta.updateTag({ property: 'og:title', content: rotulo });

    const descripcion = this.idioma.translate('meta.sellerProfile.description', { nombre });
    this.meta.updateTag({ name: 'description', content: descripcion });
    this.meta.updateTag({ property: 'og:description', content: descripcion });
    this.meta.updateTag({ property: 'og:locale', content: idioma });
  }
}
