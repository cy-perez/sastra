package co.sastra.identity.model;

import java.util.Locale;

/**
 * Idioma en el que la persona quiere recibir los correos del sistema.
 *
 * <p>Es un enum y no una cadena porque la columna tiene un CHECK con estos dos
 * valores: si algun dia entra un tercero, el compilador obliga a revisar todos
 * los sitios donde se decide una plantilla de correo.
 */
public enum UserLocale {
    ES,
    EN;

    public static UserLocale de(String etiqueta) {
        if (etiqueta == null || etiqueta.isBlank()) {
            return ES;
        }
        String principal = etiqueta.trim().toUpperCase(Locale.ROOT).split("-")[0];
        for (UserLocale idioma : values()) {
            if (idioma.name().equals(principal)) {
                return idioma;
            }
        }
        return ES;
    }

    public String etiqueta() {
        return name().toLowerCase(Locale.ROOT);
    }
}
