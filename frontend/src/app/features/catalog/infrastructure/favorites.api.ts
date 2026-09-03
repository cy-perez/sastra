import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import type { CatalogPage } from '../domain/public-listing';

/**
 * El estado del control de favorito para una publicación. HU-011, criterios 1 y 5.
 *
 * <p>`eligible` es lo que permite no ofrecer el control sobre la publicación propia sin
 * que el navegador sepa quién es el dueño: la sesión que guarda no lleva el identificador
 * de la cuenta. La regla vive en el servidor (RN-072) y esto es lo que responde.
 */
export interface FavoriteState {
  readonly favorite: boolean;
  readonly eligible: boolean;
}

/**
 * Adaptador HTTP de los favoritos. HU-011.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p><strong>Al contrario que `CatalogApi`, aquí todas las llamadas necesitan token.</strong>
 * Lo pone el interceptor de sesión; lo que importa es que ninguna de estas rutas responde
 * nada sin él, porque la lista es privada (RN-070) y no hay forma de nombrar la de otra
 * persona: el identificador sale del token, nunca de la ruta.
 *
 * <p>El cursor viaja opaco y no se interpreta. Se recibe, se guarda y se devuelve.
 *
 * <p><strong>El identificador se codifica al meterlo en la ruta.</strong> Hoy llega
 * siempre de la respuesta del servidor y no de un parámetro de dirección, así que no hay
 * nada que escapar; se hace igual porque el día que alguien le pase un valor que venga de
 * la barra de direcciones —que el enrutador entrega ya descodificado, así que un `%2F`
 * llega como barra— estos tres métodos serían la forma de emitir escrituras con el token
 * de quien mira contra rutas que nadie eligió.
 */
@Injectable({ providedIn: 'root' })
export class FavoritesApi {
  private readonly http = inject(HttpClient);

  /**
   * Marca. Idempotente: repetirlo no crea un segundo favorito ni falla (criterio 4).
   *
   * <p>`PUT` y no `POST`, que es lo que el servidor expone y lo que corresponde a una
   * operación que se puede repetir sin cambiar el resultado.
   */
  async marcar(listingId: string): Promise<void> {
    await firstValueFrom(
      this.http.put<void>(`users/me/favorites/${encodeURIComponent(listingId)}`, {}),
    );
  }

  /** Quita. Idempotente también: quitar lo que no está responde 204. */
  async quitar(listingId: string): Promise<void> {
    await firstValueFrom(
      this.http.delete<void>(`users/me/favorites/${encodeURIComponent(listingId)}`),
    );
  }

  /**
   * Si esa publicación está guardada, y si se puede guardar.
   *
   * <p>Lectura puntual y no un filtro sobre la lista: con trescientos favoritos, abrir
   * una ficha descargaría trescientas publicaciones para mirar una.
   */
  async estado(listingId: string): Promise<FavoriteState> {
    return firstValueFrom(
      this.http.get<FavoriteState>(`users/me/favorites/${encodeURIComponent(listingId)}`),
    );
  }

  /** La lista propia, paginada por cursor igual que el catálogo. */
  async lista(cursor?: string | null, limite = 24): Promise<CatalogPage> {
    let parametros = new HttpParams().set('limit', String(limite));
    if (cursor) {
      parametros = parametros.set('cursor', cursor);
    }

    return firstValueFrom(this.http.get<CatalogPage>('users/me/favorites', { params: parametros }));
  }
}
