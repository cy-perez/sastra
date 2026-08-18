import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { type LegalDocument, rutaDelTexto } from '../domain/legal-document';

/**
 * Trae el texto de un documento legal.
 *
 * <p>Es un archivo del propio sitio y no una llamada a la API: el texto no
 * depende de quien lo pida ni cambia entre peticiones, y servirlo como activo
 * estatico permite publicarlo sin desplegar el backend.
 *
 * <p>Se pide con {@code responseType: 'text'} a proposito: si se dejara el
 * predeterminado, Angular intentaria interpretarlo como JSON y fallaria en el
 * primer caracter.
 */
@Injectable({ providedIn: 'root' })
export class LegalContentApi {
  private readonly http = inject(HttpClient);

  async texto(documento: LegalDocument): Promise<string> {
    return firstValueFrom(this.http.get(rutaDelTexto(documento), { responseType: 'text' }));
  }
}
