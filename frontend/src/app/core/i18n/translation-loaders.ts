import { inject, Injectable, TransferState } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Translation, TranslocoLoader } from '@jsverse/transloco';
import type { Observable } from 'rxjs';
import { of } from 'rxjs';

import { translationStateKey } from './translation-state';

/**
 * En el navegador: primero el estado que dejo el servidor, y solo si no esta
 * ahi se pide por HTTP. El caso habitual, la primera carga, no gasta ninguna
 * peticion; cambiar de idioma en caliente si baja el archivo del otro idioma,
 * que es exactamente cuando hace falta.
 *
 * <p>Aqui no se importa ningun JSON a proposito: el que hace falta ya viene en
 * el estado transferido, y el otro se descarga solo si se cambia de idioma.
 * Incrustarlos seria mandar dos veces lo mismo. El del servidor, que si los
 * lleva dentro, vive en `bundled-translation-loader.ts`.
 */
@Injectable({ providedIn: 'root' })
export class HttpTranslationLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);
  private readonly transferState = inject(TransferState);

  getTranslation(language: string): Observable<Translation> {
    const transferred = this.transferState.get(translationStateKey(language), null);
    if (transferred !== null) {
      return of(transferred);
    }
    return this.http.get<Translation>(`/i18n/${language}.json`);
  }
}
