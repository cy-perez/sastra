import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { precioFormateado, TOMAS_DE_LA_SECUENCIA } from '../../../shared/domain/listing';
import { SpinViewer, type FotogramaDelVisor } from '../../../shared/ui/viewer/spin-viewer';
import { CatalogStore } from '../application/catalog.store';
import type { PublicListing } from '../domain/public-listing';
import { portada, tieneImagenDeReferencia } from '../domain/public-listing';

/**
 * La ficha de producto. HU-009, criterios 11 a 17 y 21.
 *
 * <p>Pública y sin sesión: si está aquí es porque está publicada (RN-068).
 *
 * <p><strong>Lo que no está y no es un olvido: la garantía del fabricante.</strong>
 * RN-067 la declara y el campo llega en la respuesta, pero cómo se enuncia sin usar la
 * palabra Respaldo ni parecerse a ella está aplazado a la tanda de los documentos legales
 * por decisión del 26 de agosto de 2026, y `textos-web.md` dice con esas palabras que
 * bloquea esta pantalla. Escribir aquí una frase provisional sería inventar copia legal.
 *
 * <p>El visor 360 tampoco: es HU-003 y va detrás de `FEATURE_SPIN_VIEWER`. Cuando llegue
 * sustituye al carrusel sin tocar esta historia.
 */
@Component({
  selector: 'sendik-product-page',
  standalone: true,
  imports: [RouterLink, SpinViewer, TranslocoPipe],
  templateUrl: './product-page.html',
  styleUrl: './product-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductPage {
  private readonly store = inject(CatalogStore);
  private readonly idioma = inject(TranslocoService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  private readonly parametros = toSignal(inject(ActivatedRoute).paramMap);

  protected readonly id = computed(() => this.parametros()?.get('id') ?? null);

  protected readonly publicacion = computed<PublicListing | null>(
    () => this.store.publicacion.data() ?? null,
  );

  protected readonly cargando = computed(() => this.store.publicacion.isPending());

  /**
   * Criterio 13: no encontrada y no publicada responden igual.
   *
   * <p>La pantalla no distingue porque la API no distingue, y no distingue a propósito:
   * decir «esto existía» ya es decir algo.
   */
  protected readonly noEncontrada = computed(() => this.store.publicacion.isError());

  protected readonly tomas = computed(() => this.ordenadas());

  /**
   * La secuencia del visor 360: solo las tomas del vendedor, en orden de giro.
   *
   * <p>**Las imágenes de referencia quedan fuera** (RN-066). Son del fabricante y no del
   * producto que se recibe, así que meterlas en el giro haría que la prenda cambiara de
   * aspecto a mitad de vuelta.
   */
  protected readonly secuencia = computed<readonly FotogramaDelVisor[]>(() => {
    const publicacion = this.publicacion();
    if (publicacion === null) {
      return [];
    }

    return publicacion.images
      .filter((imagen) => imagen.kind === 'SELLER_SHOT')
      .slice()
      .sort((una, otra) => una.position - otra.position)
      .map((imagen) => ({ url: imagen.url, grados: this.gradosDe(imagen.position) }));
  });

  /**
   * Si se ofrece el visor giratorio.
   *
   * <p>Es el caso borde de la historia: «Publicación antigua con menos de ocho tomas: el
   * visor no se ofrece y se muestra solo el carrusel». Se exige la secuencia completa y no
   * el mínimo de cuatro del propio visor: con cuatro se puede girar sin que salte, pero
   * ocho es lo que RN-017 llama secuencia, y media vuelta enseñada como si fuera entera
   * engañaría sobre lo que se está viendo.
   */
  protected readonly conVisor = computed(() => this.secuencia().length === TOMAS_DE_LA_SECUENCIA);

  /**
   * Si hay alguna imagen que no tomó el vendedor (RN-066).
   *
   * <p>Decide si se pinta la explicación de debajo del carrusel. Se calcula aquí y no
   * mirando la última toma en la plantilla: eso ataba la explicación al orden en que se
   * pintan, y el orden es una decisión de esta pantalla que puede cambiar.
   */
  protected readonly hayReferencia = computed(() => {
    const publicacion = this.publicacion();
    return publicacion !== null && tieneImagenDeReferencia(publicacion);
  });

  protected readonly precio = computed(() => {
    const valor = this.publicacion()?.product.price ?? null;
    return valor === null ? null : precioFormateado(valor, this.idioma.getActiveLang());
  });

  /**
   * Las medidas declaradas, como pares para pintar.
   *
   * <p>Se ordenan por su clave para que dos publicaciones de la misma categoría enseñen
   * las medidas en el mismo orden: el objeto que llega no garantiza ninguno, y una ficha
   * que las lista distinto cada vez es más difícil de comparar con otra.
   */
  protected readonly medidas = computed(() => {
    const valores = this.publicacion()?.product.measurements ?? {};
    return Object.entries(valores)
      .map(([clase, valor]) => ({ clase, valor }))
      .sort((una, otra) => una.clase.localeCompare(otra.clase));
  });

  protected readonly vendedor = computed(() => this.store.vendedor.data() ?? null);

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => this.store.abrirFicha(id));
    });

    // El vendedor se pide en cuanto se sabe de quién es, no antes: su identificador
    // viene dentro de la publicación.
    effect(() => {
      const dueno = this.publicacion()?.sellerId ?? null;
      untracked(() => this.store.abrirPerfil(dueno));
    });

    // El título y la descripción salen del producto, así que no pueden declararse en la
    // ruta como los demás: no se conocen hasta que la respuesta llega. La estrategia de
    // título ya puso los genéricos; esto los sustituye por los de esta publicación.
    //
    // Depende del idioma activo a propósito: sin eso, cambiar de idioma sin navegar
    // dejaría el título en el anterior.
    effect(() => {
      const publicacion = this.publicacion();
      const idioma = this.idioma.getActiveLang();

      if (publicacion === null) {
        return;
      }

      untracked(() => this.rotular(publicacion, idioma));
    });
  }

  protected gradosDe(posicion: number): number {
    return posicion * 45;
  }

  protected volverAlCatalogo(): string {
    return '/catalogo';
  }

  /**
   * La frontal primero y el resto en su orden de secuencia.
   *
   * <p>Las de referencia van al final: son del fabricante y no del producto que se
   * recibe (RN-066), así que no compiten con las reales por la primera posición.
   */
  private ordenadas() {
    const publicacion = this.publicacion();
    if (publicacion === null) {
      return [];
    }

    const frontal = portada(publicacion);
    const tomas = publicacion.images.filter((imagen) => imagen.kind === 'SELLER_SHOT');
    const referencias = publicacion.images.filter((imagen) => imagen.kind === 'REFERENCE');

    const ordenadas = [...tomas].sort((una, otra) => una.position - otra.position);
    const conFrontalDelante =
      frontal === null || frontal.kind !== 'SELLER_SHOT'
        ? ordenadas
        : [frontal, ...ordenadas.filter((imagen) => imagen.id !== frontal.id)];

    return [...conFrontalDelante, ...referencias];
  }

  private rotular(publicacion: PublicListing, idioma: string): void {
    const titulo = publicacion.product.title ?? '';
    const condicion = publicacion.product.condition;

    const rotulo = this.idioma.translate('meta.product.title', { titulo });
    this.title.setTitle(rotulo);
    this.meta.updateTag({ property: 'og:title', content: rotulo });

    const descripcion = this.idioma.translate('meta.product.description', {
      titulo,
      condicion: condicion === null ? '' : this.idioma.translate(`listing.condition.${condicion}`),
    });
    this.meta.updateTag({ name: 'description', content: descripcion });
    this.meta.updateTag({ property: 'og:description', content: descripcion });
    this.meta.updateTag({ property: 'og:locale', content: idioma });

    // La imagen que ve quien comparte el enlace. Es la frontal: es la que representa el
    // producto y la que RN-016 garantiza que existe.
    const frontal = portada(publicacion);
    if (frontal !== null) {
      this.meta.updateTag({ property: 'og:image', content: frontal.url });
    }
  }
}
