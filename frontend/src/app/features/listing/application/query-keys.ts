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

  /**
   * Las cifras del panel. HU-012.
   *
   * **Cuelga de `mine` a propósito.** TanStack casa las claves por prefijo, así que
   * invalidar `mine` -que es lo que ya hace cada mutación al terminar- invalida también
   * esto. Es lo que sostiene el criterio 4: pausar, reanudar o archivar refrescan las
   * cifras sin que nadie tenga que acordarse de añadirlas a una lista.
   */
  summary: ['catalog', 'listings', 'mine', 'summary'] as const,
  one: (id: string) => ['catalog', 'listings', id] as const,
} as const;
