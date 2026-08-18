import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  ViewEncapsulation,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import type { LegalContent } from '../application/legal-content.resolver';
import { esBorrador } from '../domain/legal-document';

/**
 * Cualquiera de los tres documentos legales del sitio.
 *
 * <p>Un solo componente y no tres casi iguales: lo unico que cambia entre ellos
 * es el titulo y el texto, y tres copias de la misma pagina se separan sola en
 * cuanto alguien toque una.
 *
 * <p>El texto llega ya resuelto desde la ruta, asi que viaja en el HTML del
 * servidor. La version se muestra en pantalla a proposito: es lo que permite
 * comprobar, meses despues, que el texto que alguien acepto es este.
 */
/**
 * <p><strong>Unico componente del proyecto sin encapsulacion de estilos.</strong>
 * El texto entra por {@code innerHTML} y ese HTML no recibe el atributo que
 * Angular usa para acotar los estilos, asi que las reglas normales no lo
 * alcanzarian y el documento saldria sin maquetar. La alternativa era
 * {@code ::ng-deep}, que esta obsoleto. Como contrapartida, todas las reglas de
 * legal-page.css cuelgan de .documento-legal y ninguna se escribe suelta.
 */
@Component({
  selector: 'sastra-legal-page',
  imports: [TranslocoPipe],
  templateUrl: './legal-page.html',
  styleUrl: './legal-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class LegalPage {
  /** Lo entrega el resolutor de la ruta a traves de withComponentInputBinding. */
  readonly contenido = input.required<LegalContent>();

  protected readonly documento = computed(() => this.contenido().documento);

  protected readonly html = computed(() => this.contenido().html);

  /** Clave del titulo: auth.legal.terms.title y sus hermanas. */
  protected readonly tituloKey = computed(() => `legal.${this.documento().id}.title`);

  /**
   * Un borrador enlazado desde una casilla de consentimiento es peor que no
   * tener pagina: la persona creeria haber leido algo que no la obliga a nada, y
   * el sitio tendria guardada la evidencia de que lo acepto. Se dice en voz alta.
   */
  protected readonly esUnBorrador = computed(() => esBorrador(this.documento().version));
}
