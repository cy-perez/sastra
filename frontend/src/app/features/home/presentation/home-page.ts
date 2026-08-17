import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Marcador de posicion. La portada real de la Fase 1, con la propuesta de valor
 * y las tres tarjetas de confianza, se construye en su propia historia:
 * ver docs/producto/alcance.md.
 *
 * <p><strong>Es la unica pantalla sin acento ocre, y de momento se acepta.</strong>
 * La regla del sistema es un CTA por pantalla, asi que esto es una deuda
 * reconocida y no un descuido: aqui todavia no hay ninguna accion que ofrecer.
 * Se nota mas desde HU-001, porque entrar y salir traen las dos a esta ruta. El
 * CTA entra con la portada de verdad.
 */
@Component({
  selector: 'sastra-home-page',
  imports: [TranslocoPipe],
  templateUrl: './home-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {}
