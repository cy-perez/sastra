import { inject, Injectable } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';

import { ListingApi } from '../infrastructure/listing.api';
import { queryKeys } from './query-keys';

/**
 * El árbol de categorías.
 *
 * <p><strong>Aparte de {@link ListingStore} y no dentro.</strong> Las consultas de
 * TanStack se crean al inyectar el servicio, así que tener el árbol en el mismo store
 * hacía que «mis publicaciones» pidiera las categorías sin necesitarlas: una petición por
 * pantalla, por nada. Lo pide quien lo usa, que es el formulario.
 *
 * <p>Con tiempo de frescura largo: el árbol lo cambia una migración, no el uso.
 *
 * <p>No exige sesión: la ruta es pública, y así el árbol ya está en caché cuando el
 * formulario se pinta.
 */
@Injectable({ providedIn: 'root' })
export class CategoriesStore {
  private readonly api = inject(ListingApi);

  readonly categories = injectQuery(() => ({
    queryKey: queryKeys.categories,
    queryFn: () => this.api.categorias(),
    staleTime: 60 * 60 * 1000,
  }));
}
