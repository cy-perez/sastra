/**
 * Claves de consulta de la bandeja del moderador.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 */
export const queryKeys = {
  /**
   * La raíz de la bandeja. **No se consulta con ella**: se invalida con ella.
   *
   * <p>Cada página es su propia consulta, así que invalidar por este prefijo alcanza a
   * todas. Importa después de cada decisión: quien aprueba desde la página 2 saca esa
   * fila de la cola y corre todas las demás, así que la página 1 que quedó en caché ya
   * no dice la verdad.
   */
  inbox: ['verificationReview', 'inbox'] as const,

  /** Una página concreta de la bandeja. */
  inboxPagina: (pagina: number) => ['verificationReview', 'inbox', pagina] as const,
} as const;
