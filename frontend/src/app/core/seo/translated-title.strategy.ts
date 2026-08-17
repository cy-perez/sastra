import { DestroyRef, inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import {
  TitleStrategy,
  type ActivatedRouteSnapshot,
  type RouterStateSnapshot,
} from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

/**
 * El titulo y la descripcion de cada ruta son claves de Transloco, no texto.
 * Se resuelven aqui, durante el renderizado en servidor, para que el HTML llegue
 * al buscador y a la vista previa de WhatsApp ya traducido: de ese trafico vive
 * el marketplace (ADR-0006).
 *
 * La ruta declara `title` con la clave y, opcionalmente, `data.descriptionKey`.
 */
@Injectable()
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly transloco = inject(TranslocoService);

  private titleKey: string | null = null;
  private descriptionKey: string | null = null;

  constructor() {
    super();
    // Al cambiar de idioma sin navegar, el titulo y la descripcion quedarian en
    // el idioma anterior. Se vuelven a aplicar con las mismas claves.
    this.transloco.langChanges$
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.apply());
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.titleKey = this.buildTitle(snapshot) ?? null;
    this.descriptionKey = readDescriptionKey(snapshot.root);
    this.apply();
  }

  private apply(): void {
    if (this.titleKey !== null) {
      const title = this.transloco.translate(this.titleKey);
      this.title.setTitle(title);
      this.meta.updateTag({ property: 'og:title', content: title });
    }

    if (this.descriptionKey !== null) {
      const description = this.transloco.translate(this.descriptionKey);
      this.meta.updateTag({ name: 'description', content: description });
      this.meta.updateTag({ property: 'og:description', content: description });
    }

    this.meta.updateTag({ property: 'og:locale', content: this.transloco.getActiveLang() });
  }
}

/** La ruta mas profunda manda: es la que sabe de que trata la pagina. */
function readDescriptionKey(root: ActivatedRouteSnapshot): string | null {
  let deepest = root;
  while (deepest.firstChild !== null) {
    deepest = deepest.firstChild;
  }

  const key: unknown = deepest.data['descriptionKey'];
  return typeof key === 'string' ? key : null;
}
