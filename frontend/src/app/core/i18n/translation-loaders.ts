import { inject, Injectable, TransferState } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import type { Translation, TranslocoLoader } from '@jsverse/transloco';
import type { Observable } from 'rxjs';
import { of } from 'rxjs';

import es from '../../../i18n/es.json';
import en from '../../../i18n/en.json';
import { translationStateKey } from './translation-state';

/**
 * En el navegador: primero el estado que dejo el servidor, y solo si no esta
 * ahi se pide por HTTP. El caso habitual, la primera carga, no gasta ninguna
 * peticion; cambiar de idioma en caliente si baja el archivo del otro idioma,
 * que es exactamente cuando hace falta.
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

/**
 * En el servidor van incrustadas en el paquete. Se evita asi que el renderizado
 * dependa de una peticion HTTP contra si mismo, que es fragil y ademas obligaria
 * a conocer la URL publica del sitio antes de poder pintar la primera pagina.
 */
@Injectable()
export class BundledTranslationLoader implements TranslocoLoader {
  private readonly transferState = inject(TransferState);

  private readonly translations: Readonly<Record<string, Translation>> = {
    es: es as Translation,
    en: en as Translation,
  };

  getTranslation(language: string): Observable<Translation> {
    const translation = this.translations[language];
    if (translation === undefined) {
      throw new Error(
        `No hay traducciones incrustadas para "${language}". Agrega src/i18n/${language}.json ` +
          'y registralo en BundledTranslationLoader.',
      );
    }
    this.transferState.set(translationStateKey(language), translation);
    return of(translation);
  }
}
