import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import type { Category, Listing, ListingRejectionReason } from '../../../shared/domain/listing';
import type { PendingListingsPage } from '../domain/pending-listing';

/** Lo que se manda al rechazar. Es de esta capa y no sale de ella. */
interface RejectListingRequestDto {
  readonly reason: ListingRejectionReason;
  readonly note: string | null;
}

/**
 * Adaptador HTTP de la bandeja de moderación de publicaciones. HU-008.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p><strong>La cola cuelga de `moderation` y el resto de `listings`</strong>, y no es un
 * descuido. `GET /listings` está reservado al catálogo público, paginado por cursor;
 * ésta es una lista administrativa acotada, que va por página y tamaño. Las tres
 * decisiones y el detalle siguen donde los dejó HU-007, porque actúan sobre una
 * publicación concreta.
 *
 * <p>De las tres decisiones aquí se usan dos: bajar una publicación ya visible
 * (`removal`) queda fuera de esta historia, porque actúa sobre algo publicado y la
 * bandeja solo devuelve lo pendiente.
 */
@Injectable({ providedIn: 'root' })
export class ListingReviewApi {
  private readonly http = inject(HttpClient);

  /** La cola. El servidor acota el tamaño a 50 y ordena por espera. */
  async pendientes(pagina = 0, tamano = 20): Promise<PendingListingsPage> {
    const parametros = new HttpParams().set('page', pagina).set('size', tamano);

    return firstValueFrom(
      this.http.get<PendingListingsPage>('moderation/listings', { params: parametros }),
    );
  }

  /**
   * La publicación completa, para el detalle.
   *
   * <p>Es el mismo endpoint que usa el vendedor y el que usará el catálogo: responde una
   * forma u otra según quién pregunte, y a un moderador le da la completa —con las ocho
   * tomas, las medidas y las marcas de atención—. No hace falta uno propio.
   */
  async una(id: string): Promise<Listing> {
    return firstValueFrom(this.http.get<Listing>(`listings/${id}`));
  }

  /**
   * El árbol de categorías, para poner nombre al identificador que trae el producto.
   *
   * <p>Es el mismo endpoint público que usa el formulario del vendedor. Se pide aparte y
   * no se añade al detalle porque el árbol es el mismo para todas las publicaciones y se
   * cachea una vez, mientras que la publicación cambia en cada fila.
   */
  async categorias(): Promise<readonly Category[]> {
    return firstValueFrom(this.http.get<Category[]>('categories'));
  }

  async aprobar(id: string): Promise<void> {
    await firstValueFrom(this.http.post(`listings/${id}/approval`, {}));
  }

  async rechazar(id: string, motivo: ListingRejectionReason, nota: string | null): Promise<void> {
    const cuerpo: RejectListingRequestDto = { reason: motivo, note: nota };

    await firstValueFrom(this.http.post(`listings/${id}/rejection`, cuerpo));
  }
}
