package co.sastra.identity.model;

import java.util.Objects;

/**
 * Contrasena en claro, viva solo durante el registro o el cambio de contrasena.
 *
 * <p>Existe por una razon concreta: que no se pueda imprimir por accidente. Un
 * {@code String} suelto acaba tarde o temprano dentro de un mensaje de registro,
 * de una traza de excepcion o del {@code toString()} de un DTO, y ahi ya no hay
 * vuelta atras (docs/operacion/datos-personales.md).
 *
 * <p>No se usa {@code char[]}: obligaria a convertir en cada frontera, y el
 * borrado de memoria no protege de nada mientras el mismo texto llego como
 * cuerpo JSON de la peticion. El riesgo que si se puede evitar es el registro.
 */
public record RawPassword(String value) {

    /**
     * Argon2 no tiene el limite de 72 bytes de BCrypt, pero un cuerpo sin tope es
     * una invitacion a gastar CPU: hashear un megabyte tarda lo suyo.
     */
    private static final int LARGO_MAXIMO = 200;

    public RawPassword {
        Objects.requireNonNull(value, "La contrasena es obligatoria");
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La contrasena supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    public int largo() {
        return value.length();
    }

    @Override
    public String toString() {
        return "RawPassword[oculta]";
    }
}
