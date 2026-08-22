/**
 * Claves de consulta de la verificación de vendedor.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 */
export const queryKeys = {
  verification: ['sellerVerification'] as const,
  institutions: ['sellerVerification', 'institutions'] as const,
} as const;
