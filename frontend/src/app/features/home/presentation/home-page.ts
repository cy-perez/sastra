import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Marcador de posicion. La portada real de la Fase 1, con la propuesta de valor
 * y las tres tarjetas de confianza, se construye en su propia historia:
 * ver docs/producto/alcance.md.
 */
@Component({
  selector: 'sastra-home-page',
  imports: [TranslocoPipe],
  templateUrl: './home-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {}
