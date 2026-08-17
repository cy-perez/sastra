/**
 * Modelo de dominio del registro. TypeScript puro, sin Angular.
 *
 * <p>Es distinto del DTO que viaja por HTTP: el adaptador de infraestructura
 * traduce entre los dos. Al principio se parecen; el dia que la API cambie un
 * nombre de campo, esta pantalla no se entera.
 */
export interface Registration {
  readonly email: string;
  readonly password: string;
  readonly displayName: string;
  /** En formato ISO, tal como la produce un campo de fecha del navegador. */
  readonly birthDate: string;
  readonly locale: string;
  /** Los dos consentimientos van por separado: son dos casillas distintas. */
  readonly acceptsTerms: boolean;
  readonly acceptsPrivacy: boolean;
}

/**
 * RN-008 en el cliente, para avisar antes de enviar. La decision sigue siendo
 * del servidor, que es quien conoce la fecha de verdad.
 */
export const EDAD_MINIMA = 18;

export function esMayorDeEdad(fechaIso: string, hoy: Date): boolean {
  const nacimiento = comoFechaLocal(fechaIso);
  if (nacimiento === null) {
    return false;
  }

  const cumpleEsteAno = new Date(hoy.getFullYear(), nacimiento.mes - 1, nacimiento.dia);
  const anos = hoy.getFullYear() - nacimiento.ano - (hoy < cumpleEsteAno ? 1 : 0);

  return anos >= EDAD_MINIMA;
}

/**
 * Se parte la cadena a mano en vez de usar new Date(texto).
 *
 * <p>El constructor interpreta "2008-08-18" como medianoche UTC, y en Colombia
 * eso es el 17 a las 19:00: la fecha retrocede un dia y quien cumple 18 manana
 * pasa por mayor de edad hoy. Un cumpleanos es una fecha de calendario, no un
 * instante.
 */
function comoFechaLocal(fechaIso: string): { ano: number; mes: number; dia: number } | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fechaIso.trim());
  if (partes === null) {
    return null;
  }

  // Indexado explicito y no desestructuracion: con noUncheckedIndexedAccess el
  // compilador no sabe que el regex garantiza los tres grupos.
  const ano = Number(partes[1]);
  const mes = Number(partes[2]);
  const dia = Number(partes[3]);

  const comprobacion = new Date(ano, mes - 1, dia);

  // Rechaza fechas que no existen, como el 31 de febrero: el constructor las
  // desborda al mes siguiente en lugar de fallar.
  const valida =
    comprobacion.getFullYear() === ano &&
    comprobacion.getMonth() === mes - 1 &&
    comprobacion.getDate() === dia;

  return valida ? { ano, mes, dia } : null;
}
