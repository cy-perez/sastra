/**
 * El perfil de una persona sobre su propia cuenta. Criterio 21 de HU-001.
 *
 * <p>TypeScript puro, sin Angular: son las reglas que valen igual en el
 * navegador y en el servidor (frontend/CLAUDE.md).
 *
 * <p>La foto no esta: necesita almacenamiento de archivos y va en su propia
 * rebanada.
 */
export interface Profile {
  readonly email: string;
  readonly emailVerified: boolean;
  readonly displayName: string;
  /** Nulos cuando la persona no los tiene puestos: no se piden al registrarse. */
  readonly city: string | null;
  readonly phone: string | null;
}

/** Lo que se manda al guardar. Se manda entero, tambien lo que quedo vacio. */
export interface ProfileEdit {
  readonly displayName: string;
  readonly city: string | null;
  readonly phone: string | null;
}

const LARGO_MINIMO_DEL_NOMBRE = 2;
const LARGO_MAXIMO_DEL_NOMBRE = 80;
const LARGO_MAXIMO_DE_CIUDAD = 80;

/** Un telefono con o sin indicativo y con los separadores que la gente escribe. */
const TELEFONO = /^\+?\d{7,15}$/;
const SEPARADORES = /[\s().-]/g;

/**
 * Vacio y ausente son lo mismo: la persona no quiere tener ese dato.
 *
 * <p>Se unifica aqui, en el borde, para que el resto del camino tenga una sola
 * forma de decirlo. Sin esto, borrar la ciudad desde un formulario seria
 * imposible: el campo vaciado llegaria como cadena vacia y no como ausencia.
 */
export function comoDatoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio === '' ? null : limpio;
}

export function elNombreEsValido(valor: string): boolean {
  const limpio = valor.trim();
  return limpio.length >= LARGO_MINIMO_DEL_NOMBRE && limpio.length <= LARGO_MAXIMO_DEL_NOMBRE;
}

export function laCiudadEsValida(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || opcional.length <= LARGO_MAXIMO_DE_CIUDAD;
}

/**
 * La misma regla que el dominio del servidor, que es quien decide. Aqui solo
 * evita un viaje y un mensaje tardio.
 *
 * <p>No se valida contra el plan de numeracion colombiano: un vendedor puede
 * tener un numero de otro pais y este dato no enruta llamadas, solo permite que
 * alguien le escriba.
 */
export function elTelefonoEsValido(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || TELEFONO.test(opcional.replace(SEPARADORES, ''));
}

/**
 * Pedir el cambio al correo que ya se tiene no es un error, pero tampoco hay
 * nada que hacer: se evita el viaje y se dice en la pantalla.
 */
export function esElMismoCorreo(escrito: string, actual: string): boolean {
  return escrito.trim().toLowerCase() === actual.trim().toLowerCase();
}
