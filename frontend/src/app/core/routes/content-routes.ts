/**
 * Direcciones de las paginas informativas. TypeScript puro, sin Angular.
 *
 * <p>Viven en `core` por el mismo motivo que `legal-routes.ts`: las necesitan la
 * tabla de rutas, la navegacion de la cabecera y el pie del sitio, y ninguno de
 * los tres puede importar de una funcionalidad (frontend/CLAUDE.md).
 *
 * <p>Que sean constantes y no cadenas sueltas es lo que impide el fallo silencioso
 * de tener una ruta que existe y un enlace que apunta a otra parte. Es tambien lo
 * que hace comprobable el criterio 25 de HU-005: ningun enlace lleva a catalogo,
 * publicacion ni busqueda, porque desde aqui no se puede nombrar lo que no esta.
 *
 * <p>Las direcciones van en espanol y no cambian con el idioma: al cambiar a
 * ingles cambia el texto, no la direccion.
 */
export type ContentPageId = 'howItWorks' | 'about' | 'faq' | 'contact';

export const RUTAS_CONTENIDO: Readonly<Record<ContentPageId, string>> = {
  howItWorks: '/como-funciona',
  about: '/sobre-sendik',
  faq: '/preguntas-frecuentes',
  contact: '/contacto',
};

/**
 * En el orden en que se listan en la navegacion y en el pie.
 *
 * <p>El orden no es alfabetico ni casual: responde a las preguntas segun se las
 * hace quien duda antes de registrarse. Primero como funciona esto, despues
 * quien esta detras, luego lo que aun preocupa, y al final como escribir.
 */
export const PAGINAS_DE_CONTENIDO: readonly ContentPageId[] = [
  'howItWorks',
  'about',
  'faq',
  'contact',
];
