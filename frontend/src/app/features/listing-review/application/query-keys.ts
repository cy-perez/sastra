/**
 * Claves de consulta de la bandeja de moderación de publicaciones.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 */
export const queryKeys = {
  /** La cola entera. Se invalida después de cada decisión. */
  queue: ['listingReview', 'queue'] as const,
  /** Una publicación concreta, para el detalle. */
  listing: (id: string) => ['listingReview', 'listing', id] as const,
  /** El árbol de categorías. Es el mismo para todas, así que se pide una vez. */
  categories: ['listingReview', 'categories'] as const,
} as const;
