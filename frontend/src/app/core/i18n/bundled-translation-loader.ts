import { inject, Injectable, TransferState } from '@angular/core';
import type { Translation, TranslocoLoader } from '@jsverse/transloco';
import type { Observable } from 'rxjs';
import { of } from 'rxjs';

import es from '../../../i18n/es.json';
import en from '../../../i18n/en.json';
import { translationStateKey } from './translation-state';

/**
 * En el servidor las traducciones van incrustadas en el paquete. Se evita asi
 * que el renderizado dependa de una peticion HTTP contra si mismo, que es fragil
 * y ademas obligaria a conocer la URL publica del sitio antes de poder pintar la
 * primera pagina.
 *
 * <p>Vive en su propio archivo y no junto a {@link HttpTranslationLoader} por lo
 * unico que hay aqui y alli no: los dos `import` de JSON. Compartiendo archivo,
 * los dos arboles de traduccion entraban tambien en el paquete del navegador
 * —donde no se usan, porque alli llegan por estado transferido o por HTTP— y se
 * descargaban dos veces. Con el sitio informativo de HU-005 eso ya eran 56 kB de
 * mas en la carga inicial, por encima del presupuesto declarado en angular.json.
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
