import { computed, DOCUMENT, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { TranslocoService } from '@jsverse/transloco';

import { APP_CONFIG } from '../config/app-config';
import { buildLocaleCookie } from './locale';

/**
 * Cambio de idioma en caliente. La eleccion se guarda en cookie, no en
 * localStorage, porque quien la necesita primero es el servidor: en la
 * siguiente visita el HTML ya sale traducido sin esperar al JavaScript.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private readonly config = inject(APP_CONFIG);
  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly active = signal(this.transloco.getActiveLang());

  readonly current = this.active.asReadonly();
  readonly available = computed(() => this.config.availableLocales);

  change(locale: string): void {
    if (!this.config.availableLocales.includes(locale) || locale === this.active()) {
      return;
    }

    this.transloco.setActiveLang(locale);
    this.document.documentElement.lang = locale;
    this.active.set(locale);

    if (this.isBrowser) {
      this.document.cookie = buildLocaleCookie(locale);
    }
  }
}
