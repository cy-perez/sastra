/**
 * Claves de consulta de la publicación de producto.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 *
 * `una` es una función porque depende del identificador: dos publicaciones distintas no
 * pueden compartir entrada en la caché.
 */
export const queryKeys = {
  categories: ['catalog', 'categories'] as const,
  mine: ['catalog', 'listings', 'mine'] as const,
  one: (id: string) => ['catalog', 'listings', id] as const,
} as const;
