import type { LegalDocumentId } from '../../../core/routes/legal-routes';

/**
 * Los documentos legales del sitio. TypeScript puro, sin Angular.
 *
 * <p>Terminos y tratamiento son los que la persona acepta al registrarse, cada
 * uno en su propia casilla, y de cada uno se guarda evidencia con su version
 * (docs/operacion/datos-personales.md). El de cookies no se consiente en ningun
 * formulario, pero es obligatorio publicarlo igual.
 *
 * <p>El identificador y las direcciones viven en `core`: los necesitan tambien el
 * registro y el pie, y una funcionalidad no importa de otra.
 */

/**
 * Un documento listo para mostrar: que version es y donde esta su texto.
 *
 * <p>La version no es decorativa. Es lo unico que permite comprobar, meses
 * despues, que el texto que alguien acepto es el que se le enseño: sin ella la
 * evidencia guardada apunta a un documento que ya no existe.
 */
export interface LegalDocument {
  readonly id: LegalDocumentId;
  readonly version: string;
  readonly locale: string;
}

/**
 * Donde vive el texto de un documento.
 *
 * <p>La version va en el nombre del archivo, y eso es deliberado: publicar un
 * texto nuevo es subir su archivo y cambiar la variable de version, sin tocar
 * codigo. Tambien impide el fallo silencioso de cambiar la version y servir el
 * texto viejo, porque el archivo con la version nueva sencillamente no estaria.
 *
 * <p>Empieza por barra para que el interceptor de la API la deje pasar tal cual:
 * es un activo de este sitio, no una llamada al backend.
 */
export function rutaDelTexto(documento: LegalDocument): string {
  return `/legal/${documento.id}.${documento.version}.${documento.locale}.html`;
}

/**
 * Si esta version corresponde a un texto que todavia no es el definitivo.
 *
 * <p>Existe para que la pantalla pueda decirlo en voz alta. Un borrador enlazado
 * desde una casilla de consentimiento es peor que no tener pagina: la persona
 * creeria haber leido algo que no la obliga a nada, y el sitio tendria guardada
 * la evidencia de que lo acepto.
 */
export const VERSION_DE_BORRADOR = 'borrador-local';

export function esBorrador(version: string): boolean {
  return version === VERSION_DE_BORRADOR;
}
