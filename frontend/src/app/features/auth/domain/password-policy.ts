/**
 * RN-005, la mitad que se puede comprobar sin servidor.
 *
 * <p>TypeScript puro: sin Angular y sin RxJS, para poder probarlo sin TestBed.
 *
 * <p>Duplica a proposito el minimo que el backend ya aplica. No es la misma
 * regla escrita dos veces por descuido: aqui sirve para no hacer viajar una
 * contrasena que se sabe corta, y alla sigue siendo la unica que decide. Si
 * algun dia difieren, manda el servidor.
 *
 * <p>Lo que <strong>no</strong> se duplica es la lista de contrasenas filtradas:
 * eso solo lo sabe el backend (ADR-0013).
 */
export const LARGO_MINIMO_DE_CONTRASENA = 10;

export function cumpleElLargoMinimo(contrasena: string): boolean {
  return [...contrasena].length >= LARGO_MINIMO_DE_CONTRASENA;
}

/**
 * Fuerza orientativa. El criterio 4 de HU-001 dice que no bloquea el envio: es
 * una ayuda para elegir mejor, no una barrera mas.
 */
export type FuerzaDeContrasena = 'corta' | 'aceptable' | 'buena';

export function fuerzaDe(contrasena: string): FuerzaDeContrasena {
  const largo = [...contrasena].length;

  if (largo < LARGO_MINIMO_DE_CONTRASENA) {
    return 'corta';
  }
  // Una frase larga vale mas que una corta con simbolos: es la misma idea que
  // sostiene RN-005 al no exigir complejidad artificial.
  return largo >= 16 || /\s/.test(contrasena) ? 'buena' : 'aceptable';
}
