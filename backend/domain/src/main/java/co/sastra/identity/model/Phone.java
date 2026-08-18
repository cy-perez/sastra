package co.sastra.identity.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Telefono de contacto. Dato personal de nivel interno: solo lo ven el titular y
 * la operacion, nunca un perfil publico (docs/operacion/datos-personales.md).
 *
 * <p>Se acepta con o sin indicativo y con los separadores que la gente usa al
 * escribirlo; se guarda solo con digitos y un mas opcional delante. Normalizar al
 * entrar evita que el mismo numero exista escrito de cinco formas.
 *
 * <p>No se valida contra el plan de numeracion colombiano. Un vendedor puede
 * tener un numero de otro pais, y una validacion demasiado estricta deja fuera a
 * quien no encaje sin ganar nada: este numero no se usa para enrutar llamadas, se
 * usa para que alguien pueda escribirle.
 */
public record Phone(String value) {

    private static final Pattern SEPARADORES = Pattern.compile("[\\s().-]");
    private static final Pattern VALIDO = Pattern.compile("\\+?\\d{7,15}");

    public Phone {
        Objects.requireNonNull(value, "El telefono es obligatorio");
        value = SEPARADORES.matcher(value.trim()).replaceAll("");

        if (!VALIDO.matcher(value).matches()) {
            throw new IllegalArgumentException("El telefono necesita entre 7 y 15 digitos, con un + opcional delante");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
