/**
 * Direcciones de los documentos legales. TypeScript puro, sin Angular.
 *
 * <p>Viven en `core` y no en `features/legal` porque las necesitan tres sitios
 * que no pueden depender de esa funcionalidad: la tabla de rutas, el formulario
 * de registro, que enlaza lo que la persona acepta, y el pie del sitio. Una
 * funcionalidad no importa de otra (frontend/CLAUDE.md).
 *
 * <p>Que sean constantes y no cadenas sueltas es lo que impide el fallo silencioso
 * de tener una ruta que existe y un enlace que apunta a otra parte: las dos cosas
 * salen de aqui.
 *
 * <p>Las direcciones van en espanol y no cambian con el idioma, como el resto del
 * sitio.
 */
export type LegalDocumentId = 'terms' | 'privacy' | 'cookies';

export const RUTAS_LEGALES: Readonly<Record<LegalDocumentId, string>> = {
  terms: '/terminos',
  privacy: '/tratamiento-de-datos',
  cookies: '/politica-de-cookies',
};

/** En el orden en que se listan en el pie. */
export const DOCUMENTOS_LEGALES: readonly LegalDocumentId[] = ['terms', 'privacy', 'cookies'];
