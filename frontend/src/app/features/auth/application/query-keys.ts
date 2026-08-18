/**
 * Claves de consulta de la funcionalidad de cuentas.
 *
 * <p>Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md): una
 * clave escrita a mano en dos sitios se separa en cuanto una de las dos cambie, y
 * entonces invalidar deja de invalidar nada.
 */
export const queryKeys = {
  sessions: ['auth', 'sessions'] as const,
} as const;
