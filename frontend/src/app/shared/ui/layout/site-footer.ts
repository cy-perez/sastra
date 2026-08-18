import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import {
  DOCUMENTOS_LEGALES,
  type LegalDocumentId,
  RUTAS_LEGALES,
} from '../../../core/routes/legal-routes';

@Component({
  selector: 'sastra-site-footer',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './site-footer.html',
  styleUrl: './site-footer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SiteFooter {
  protected readonly documentosLegales = DOCUMENTOS_LEGALES;

  protected rutaDe(documento: LegalDocumentId): string {
    return RUTAS_LEGALES[documento];
  }
}
