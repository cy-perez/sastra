import { DestroyRef, DOCUMENT, inject, Injectable, REQUEST } from '@angular/core';
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
 *
 * <p>Tambien pone la direccion canonica, y aqui porque es lo mismo que el titulo: algo que
 * toda pagina tiene, que solo importa de cara al buscador y que se decide al navegar. Sin
 * ella, la misma pagina alcanzada con una cadena de consulta distinta —una campana, un
 * `utm_source`— se indexa como varias, y el buscador reparte entre ellas la relevancia que
 * deberia ir a una sola.
 */
@Injectable()
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly transloco = inject(TranslocoService);
  private readonly documento = inject(DOCUMENT);

  /**
   * La peticion que se esta sirviendo. Solo existe en el servidor.
   *
   * <p>De ella sale el origen del canonico. En el navegador se usa `location`, que alli si
   * existe; en el servidor no hay `location` y usarlo tumbaria el renderizado.
   */
  private readonly peticion = inject(REQUEST, { optional: true });

  private titleKey: string | null = null;
  private descriptionKey: string | null = null;
  private ruta: string | null = null;

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
    this.ruta = snapshot.url;
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

    this.aplicarCanonico();
  }

  /**
   * Una sola direccion por pagina, sin cadena de consulta.
   *
   * <p>Se recorta a proposito: `?utm_source=…` y `?pagina=2` llevan al mismo contenido, y
   * el canonico existe justamente para decir cual de todas es la buena.
   *
   * <p>El enlace se reutiliza en vez de agregar otro. Angular no borra el anterior al
   * navegar, asi que crear uno por navegacion dejaria la pagina con varios canonicos, que
   * para un buscador es lo mismo que no tener ninguno.
   */
  private aplicarCanonico(): void {
    if (this.ruta === null) {
      return;
    }

    const origen = this.origen();
    if (origen === null) {
      return;
    }

    const sinConsulta = this.ruta.split('?')[0];

    // Falsy y no `=== null`: el documento del servidor no es el del navegador y su
    // `querySelector` devuelve `undefined` cuando no encuentra nada. Con la comparacion
    // estricta, el renderizado moria con «Cannot read properties of undefined».
    let enlace = this.documento.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!enlace) {
      enlace = this.documento.createElement('link');
      enlace.setAttribute('rel', 'canonical');
      this.documento.head.appendChild(enlace);
    }

    enlace.setAttribute('href', origen + sinConsulta);
  }

  /**
   * El origen del sitio: del navegador si lo hay, y de la peticion si se esta renderizando
   * en el servidor.
   *
   * <p>No sale de configuracion, y es deliberado: una variable con el dominio seria una
   * cosa mas que olvidar al desplegar, y el dia que se olvidara el canonico apuntaria al
   * sitio equivocado en silencio. La peticion siempre sabe por donde entro quien pregunta.
   */
  private origen(): string | null {
    const ventana = this.documento.defaultView;
    if (ventana?.location?.origin) {
      return ventana.location.origin;
    }

    if (this.peticion === null) {
      return null;
    }

    try {
      return new URL(this.peticion.url).origin;
    } catch {
      return null;
    }
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
