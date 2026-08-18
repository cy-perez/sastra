/**
 * Lo que una persona puede ver y hacer sobre su propia cuenta. TypeScript puro.
 *
 * <p>Criterios 17, 22 y 23 de HU-001: sus sesiones abiertas, sus datos y el
 * cierre de la cuenta.
 */
export interface ActiveSession {
  /** El de la familia: el que sobrevive a los refrescos y sirve para cerrarla. */
  readonly id: string;
  readonly userAgent: string | null;
  readonly startedAt: string;
  readonly expiresAt: string;
  /** Si es la sesion desde la que se esta mirando la lista. */
  readonly current: boolean;
}

/**
 * Cerrar una cuenta no se deshace, asi que se escribe el propio correo. Comparar
 * normalizado es lo mismo que hace el servidor: quien lo escribe con mayusculas
 * no se esta equivocando de cuenta.
 */
export function laConfirmacionCoincide(escrito: string, correo: string): boolean {
  return escrito.trim().toLowerCase() === correo.trim().toLowerCase();
}
