import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { ThemeService } from '../../../core/theme/theme.service';

@Component({
  selector: 'sastra-site-header',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './site-header.html',
  styleUrl: './site-header.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SiteHeader {
  private readonly theme = inject(ThemeService);
  private readonly language = inject(LanguageService);

  protected readonly currentTheme = this.theme.current;
  protected readonly currentLocale = this.language.current;
  protected readonly locales = this.language.available;

  /** La etiqueta anuncia a donde lleva el boton, no en que estado esta. */
  protected readonly themeLabelKey = computed(() =>
    this.currentTheme() === 'dark' ? 'layout.theme.toLight' : 'layout.theme.toDark',
  );

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  protected onLanguageChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.language.change(select.value);
  }
}
