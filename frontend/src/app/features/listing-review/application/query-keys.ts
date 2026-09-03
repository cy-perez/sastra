/**
 * Claves de consulta de la bandeja de moderación de publicaciones.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 */
export const queryKeys = {
  /** La raíz de la cola. Invalidarla alcanza a todas sus páginas. */
  queue: ['listingReview', 'queue'] as const,
  /** Una página concreta. La página va **en la clave**: sin eso, cambiar de página no
   *  pediría nada nuevo y la pantalla se quedaría enseñando la misma. */
  queuePagina: (pagina: number) => ['listingReview', 'queue', pagina] as const,
  /** Una publicación concreta, para el detalle. */
  listing: (id: string) => ['listingReview', 'listing', id] as const,
  /** El árbol de categorías. Es el mismo para todas, así que se pide una vez. */
  categories: ['listingReview', 'categories'] as const,
} as const;
