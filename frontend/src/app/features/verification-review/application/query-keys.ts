/**
 * Claves de consulta de la bandeja del moderador.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 */
export const queryKeys = {
  /** La bandeja entera. Se invalida después de cada decisión. */
  inbox: ['verificationReview', 'inbox'] as const,
} as const;
