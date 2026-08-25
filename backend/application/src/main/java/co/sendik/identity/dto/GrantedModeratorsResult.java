package co.sendik.identity.dto;

import java.util.List;

/**
 * Lo que dejo el sembrado de moderadores al arrancar (HU-006).
 *
 * <p>Existe para que quien arranca pueda registrarlo: otorgar autorizacion en silencio es
 * lo que convierte un mecanismo legitimo en algo indistinguible de una puerta trasera.
 *
 * <p>Los tres grupos se distinguen porque se corrigen en sitios distintos: un correo sin
 * cuenta es un registro que falta, uno sin verificar es un enlace que nadie abrio, y una
 * entrada invalida es una errata en la variable.
 *
 * @param otorgados correos que ahora tienen el rol, lo tuvieran ya o no
 * @param sinCuenta correos configurados que no corresponden a ninguna cuenta
 * @param sinVerificar cuentas que existen pero no han verificado su correo
 * @param invalidos cuantas entradas no son un correo. Es un numero y no la lista: el
 *     valor crudo de una variable de seguridad no se escribe en el registro, porque el
 *     dia que alguien pegue ahi el contenido de otra variable, esa otra acaba impresa
 */
public record GrantedModeratorsResult(
        List<String> otorgados, List<String> sinCuenta, List<String> sinVerificar, int invalidos) {

    public GrantedModeratorsResult {
        otorgados = List.copyOf(otorgados);
        sinCuenta = List.copyOf(sinCuenta);
        sinVerificar = List.copyOf(sinVerificar);
    }
}
