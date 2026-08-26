import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import type { Category, Color, Condition, Listing, Money, Shipping, Size } from '../domain/listing';

/**
 * Lo que se manda al crear o editar. Es de esta capa y no sale de ella: la plantilla ve
 * modelos de dominio, nunca un DTO (frontend/CLAUDE.md).
 *
 * <p>Todo opcional menos la categoría, igual que en el servidor: el criterio 5 dice que
 * un borrador se guarda a medias.
 */
export interface DatosDelProducto {
  readonly categoryId: string;
  readonly title?: string | null;
  readonly description?: string | null;
  readonly brand?: string | null;
  readonly condition?: Condition | null;
  readonly size?: Size | null;
  readonly measurements?: Readonly<Record<string, number>> | null;
  readonly color?: Color | null;
  readonly price?: Money | null;
  readonly shipping?: Shipping | null;
  readonly isSealed?: boolean | null;
  readonly warrantyMonths?: number | null;
}

/** Una página de publicaciones propias. */
interface SellerListingsPageDto {
  readonly items: readonly Listing[];
  readonly page: number;
  readonly size: number;
}

/**
 * Adaptador HTTP de la publicación de producto. HU-007.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p>Las tomas viajan como `multipart` y no en base64 dentro de un JSON: en base64 ocupan
 * un tercio más y obligan a tener cada imagen en memoria dos veces.
 *
 * <p><strong>La forma del JSON y la del dominio coinciden campo a campo</strong>, así que
 * aquí no hay mapeo que escribir. Se declara igual el tipo de vuelta como modelo de
 * dominio y no como DTO: el día que la API cambie un nombre, el mapeo entra aquí y no en
 * las cinco pantallas que lo usan.
 */
@Injectable({ providedIn: 'root' })
export class ListingApi {
  private readonly http = inject(HttpClient);

  /** El árbol activo, por familias. Público: no hace falta sesión para pedirlo. */
  async categorias(): Promise<readonly Category[]> {
    return firstValueFrom(this.http.get<Category[]>('categories'));
  }

  async crear(datos: DatosDelProducto): Promise<Listing> {
    return firstValueFrom(this.http.post<Listing>('listings', datos));
  }

  /** Responde 404 si no existe o si no es de quien pregunta (criterio 33). */
  async una(id: string): Promise<Listing> {
    return firstValueFrom(this.http.get<Listing>(`listings/${id}`));
  }

  /** Guarda los datos del producto. Sobre una viva la devuelve a revisión (RN-062). */
  async editar(id: string, datos: DatosDelProducto): Promise<Listing> {
    return firstValueFrom(this.http.patch<Listing>(`listings/${id}`, datos));
  }

  /** Solo el precio: sigue visible y no pasa por moderación (criterio 28). */
  async cambiarPrecio(id: string, precio: Money): Promise<Listing> {
    return firstValueFrom(this.http.patch<Listing>(`listings/${id}/price`, { price: precio }));
  }

  /** Solo el peso y la caja. Tampoco pasa por moderación. */
  async cambiarEnvio(id: string, envio: Shipping): Promise<Listing> {
    return firstValueFrom(this.http.patch<Listing>(`listings/${id}/shipping`, envio));
  }

  /**
   * Sube una toma en su posición.
   *
   * <p>{@code fromGallery} va en verdadero **siempre, y por ahora**: el asistente de
   * captura es HU-003 y no existe, así que la galería es la única vía. Declararlo así es
   * lo honesto —el criterio 18 dice que lo declara el cliente— y solo suma una marca de
   * atención; nunca quita una validación. El día que exista la captura, este valor pasa a
   * depender de por dónde entró la imagen.
   */
  async subirToma(id: string, posicion: number, imagen: Blob): Promise<Listing> {
    const cuerpo = new FormData();
    // Se nombra para que el servidor lo reciba como archivo y no como texto. El nombre no
    // decide nada: el tipo se detecta por los bytes de cabecera (ADR-0018).
    cuerpo.append('archivo', imagen, 'toma');

    return firstValueFrom(
      this.http.post<Listing>(`listings/${id}/images`, cuerpo, {
        params: parametrosDeImagen(posicion, 'SELLER_SHOT'),
      }),
    );
  }

  /** RN-066: solo se admite en tecnología declarada sellada. */
  async subirReferencia(id: string, posicion: number, imagen: Blob): Promise<Listing> {
    const cuerpo = new FormData();
    cuerpo.append('archivo', imagen, 'referencia');

    return firstValueFrom(
      this.http.post<Listing>(`listings/${id}/images`, cuerpo, {
        params: parametrosDeImagen(posicion, 'REFERENCE'),
      }),
    );
  }

  async borrarImagen(id: string, imagenId: string): Promise<Listing> {
    return firstValueFrom(this.http.delete<Listing>(`listings/${id}/images/${imagenId}`));
  }

  async enviarARevision(id: string): Promise<Listing> {
    return firstValueFrom(this.http.post<Listing>(`listings/${id}/submission`, {}));
  }

  async retirarDeRevision(id: string): Promise<Listing> {
    return firstValueFrom(this.http.delete<Listing>(`listings/${id}/submission`));
  }

  /**
   * Retoma una rechazada y la devuelve a borrador (criterio 23).
   *
   * <p>Es un `DELETE` del rechazo y no un `POST` de nada: el vendedor quita lo que hay,
   * no crea nada. Sin esta ruta, a quien le rechacen por las fotos no le queda forma de
   * reenviar, porque rechazada no pasa a revisión directamente.
   */
  async retomar(id: string): Promise<Listing> {
    return firstValueFrom(this.http.delete<Listing>(`listings/${id}/rejection`));
  }

  async pausar(id: string): Promise<Listing> {
    return firstValueFrom(this.http.post<Listing>(`listings/${id}/pause`, {}));
  }

  async reanudar(id: string): Promise<Listing> {
    return firstValueFrom(this.http.delete<Listing>(`listings/${id}/pause`));
  }

  /** Archivar es para siempre: la publicación no vuelve y sus fotos se borran. */
  async archivar(id: string): Promise<Listing> {
    return firstValueFrom(this.http.post<Listing>(`listings/${id}/archival`, {}));
  }

  /** Lo suyo, lo más reciente primero. Por página: es un listado acotado. */
  async mias(pagina = 0, tamano = 20): Promise<readonly Listing[]> {
    const respuesta = await firstValueFrom(
      this.http.get<SellerListingsPageDto>('users/me/listings', {
        params: new HttpParams().set('page', pagina).set('size', tamano),
      }),
    );
    return respuesta.items;
  }
}

/**
 * Los parámetros de una subida.
 *
 * <p>Van como {@code HttpParams} y no pegados a la cadena de la ruta: así el
 * interceptor y las pruebas ven una URL limpia, y el escapado lo hace Angular.
 *
 * <p>{@code fromGallery} va en verdadero **siempre, y por ahora**, por lo que explica
 * {@code subirToma}.
 */
function parametrosDeImagen(posicion: number, clase: 'SELLER_SHOT' | 'REFERENCE'): HttpParams {
  return new HttpParams().set('position', posicion).set('kind', clase).set('fromGallery', true);
}
