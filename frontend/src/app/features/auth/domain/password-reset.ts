/**
 * Lo que se envia para recuperar el acceso. TypeScript puro, sin Angular.
 *
 * <p>Son dos pasos y dos modelos porque son dos momentos distintos: en el primero
 * la persona solo dice quien cree ser, y en el segundo ya demostro tener el buzon.
 */
export interface PasswordResetRequest {
  readonly email: string;
}

export interface PasswordReset {
  /** El valor que llego en el enlace del correo. */
  readonly token: string;
  readonly newPassword: string;
}

/**
 * Criterio 18: el enlace dura 30 minutos.
 *
 * <p>Vive aqui y no en la plantilla porque es la duracion que el servidor aplica y
 * el texto de la pantalla tiene que decir la misma. Si un dia cambia, cambia en un
 * sitio.
 */
export const MINUTOS_DE_VIGENCIA = 30;
