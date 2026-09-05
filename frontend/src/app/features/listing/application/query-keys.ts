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

  /**
   * El rastro de moderación de una publicación. HU-013.
   *
   * **Cuelga de `one` pero no se invalida con ella.** `refrescar` deja la publicación en la
   * caché con `setQueryData`, que escribe una clave exacta y no invalida nada por prefijo,
   * así que el rastro no se enteraría de un envío recién hecho. Por eso el store lo
   * invalida aparte: enviar a revisión y editar una publicación viva crean una entrada, y
   * el rastro abierto en esa misma pantalla mostraría el de antes.
   */
  history: (id: string) => ['catalog', 'listings', id, 'history'] as const,
} as const;
