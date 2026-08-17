import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Casilla de verificacion con su etiqueta.
 *
 * <p>Existe como componente propio porque el consentimiento de la Ley 1581 son
 * dos casillas separadas y obligatorias, y conviene que las dos se comporten
 * exactamente igual: mismo destino tactil, mismo anuncio de error, misma
 * asociacion entre etiqueta y control.
 */
@Component({
  selector: 'sastra-checkbox-field',
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './checkbox-field.html',
  styleUrl: './form-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckboxField {
  readonly control = input.required<FormControl<boolean>>();
  readonly labelKey = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly errorKey = input<string | null>(null);

  protected readonly errorId = computed(() => `${this.fieldId()}-error`);
}
