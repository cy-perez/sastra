import { inject } from '@angular/core';
import type { ResolveFn } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import type { LegalDocumentId } from '../../../core/routes/legal-routes';
import type { LegalDocument } from '../domain/legal-document';
import { LegalContentApi } from '../infrastructure/legal-content.api';

/**
 * El documento y su texto, o el documento sin texto si el archivo no esta.
 *
 * @param html nulo cuando falta el archivo de esa version. Es un fallo de
 *     despliegue, no del visitante: alguien cambio la variable de version y no
 *     subio el texto nuevo
 */
export interface LegalContent {
  readonly documento: LegalDocument;
  readonly html: string | null;
}

/**
 * Trae el texto antes de pintar la ruta.
 *
 * <p><strong>Es un resolutor y no una carga dentro del componente, y eso
 * importa.</strong> El router espera a que termine antes de renderizar, asi que
 * el texto legal viaja dentro del HTML que sirve el servidor. Cargandolo despues
 * quedaria fuera: un buscador veria una pagina vacia donde deberia estar la
 * politica de tratamiento, y quien no ejecute JavaScript no la leeria nunca. Es
 * el documento que una autoridad revisa primero (docs/operacion/datos-personales.md).
 */
export const legalContentResolver: ResolveFn<LegalContent> = async (ruta) => {
  const id = ruta.data['documento'] as LegalDocumentId;
  const versiones = inject(APP_CONFIG).legalVersions;

  const documento: LegalDocument = {
    id,
    version: versiones[id],
    locale: inject(TranslocoService).getActiveLang(),
  };

  const contenido = inject(LegalContentApi);

  try {
    return { documento, html: await contenido.texto(documento) };
  } catch {
    // No se propaga: un texto que falta no debe dejar la ruta sin responder. La
    // pantalla lo dice, que es mas util que una pagina de error generica.
    return { documento, html: null };
  }
};
