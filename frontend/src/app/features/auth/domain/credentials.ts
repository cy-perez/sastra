/**
 * Lo que se escribe para entrar. TypeScript puro, sin Angular.
 *
 * <p>Aqui no hay politica de contrasena y no es un olvido: al entrar no se
 * juzga la contrasena, se comprueba. Rechazar en el cliente una de nueve
 * caracteres le diria a quien la tiene de antes que su cuenta es invalida,
 * cuando lo unico cierto es que el minimo cambio despues de que la creara.
 * Ver password-policy.ts, que si aplica al registro.
 */
export interface Credentials {
  readonly email: string;
  readonly password: string;
}

/**
 * Comprobacion deliberadamente laxa: exige una arroba y un punto en el dominio,
 * nada mas.
 *
 * <p>La gramatica real de una direccion (RFC 5322) admite cosas que ninguna
 * expresion regular corta reconoce, y equivocarse aqui de mas significa dejar
 * fuera a alguien de su propia cuenta. Lo que decide es el servidor; esto solo
 * evita el viaje cuando falta lo evidente.
 */
export function esCorreoValido(valor: string): boolean {
  return /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/.test(valor.trim());
}

/** Una contrasena en blanco no llega a salir: no hay nada que comprobar. */
export function faltaLaContrasena(valor: string): boolean {
  return valor === '';
}
