package co.sastra.identity.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * El nombre como aparece en un documento de identidad o en una cuenta bancaria.
 *
 * <p>No es {@link DisplayName}. Ese es como la persona quiere que la vean en el
 * sitio y es dato publico; este es el nombre legal y solo existe para comparar dos
 * cosas que tienen que coincidir (RN-012, criterio 4 de HU-002).
 *
 * <p><strong>La comparacion es exacta despues de normalizar, y no difusa.</strong>
 * Se quitan acentos, mayusculas, puntuacion y espacios de sobra, porque son
 * diferencias de escritura y no de persona: "JOSE PEREZ" y "José Pérez" son el
 * mismo nombre escrito por dos formularios distintos. Lo que no se hace es aceptar
 * un apellido que falta o una inicial en lugar del nombre: nadie ha decidido cuanta
 * diferencia es tolerable, y si se decidiera, la decision tendria que quedar en
 * {@code reglas-negocio.md} y no en este archivo.
 *
 * <p><strong>Este objeto no verifica nada, y conviene no confundirse.</strong> Los
 * dos nombres que compara los escribe la misma persona: el que dice que esta en su
 * documento y el que dice que esta en su cuenta. Coincidir aqui solo demuestra que
 * no se contradijo. Que el nombre sea de verdad el del documento lo comprueba el
 * moderador mirando la imagen, y esa es la comprobacion que cuenta.
 */
public record LegalName(String value) {

    private static final int LARGO_MINIMO = 2;

    private static final int LARGO_MAXIMO = 120;

    public LegalName {
        Objects.requireNonNull(value, "El nombre del titular es obligatorio");
        value = value.trim().replaceAll(" +", " ");

        if (value.length() < LARGO_MINIMO) {
            throw new IllegalArgumentException(
                    "El nombre del titular necesita al menos " + LARGO_MINIMO + " caracteres");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El nombre del titular supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    /** RN-012: el titular de la cuenta y el del documento tienen que ser el mismo. */
    public boolean coincideCon(LegalName otro) {
        Objects.requireNonNull(otro, "El nombre con el que se compara es obligatorio");
        return normalizado().equals(otro.normalizado());
    }

    /**
     * La forma con la que se compara: sin acentos, en mayusculas, sin puntuacion y
     * con un solo espacio entre palabras.
     *
     * <p>Se descompone en NFD y se descartan las marcas diacriticas en vez de
     * sustituir letra por letra. Una tabla de reemplazos cubre la enie y las cinco
     * vocales acentuadas, y falla con la dieresis, con la cedilla y con cualquier
     * apellido que no sea castellano; descomponer no falla con ninguno.
     */
    String normalizado() {
        String descompuesto = Normalizer.normalize(value, Normalizer.Form.NFD);

        StringBuilder limpio = new StringBuilder(descompuesto.length());
        for (int i = 0; i < descompuesto.length(); i++) {
            char caracter = descompuesto.charAt(i);

            if (Character.getType(caracter) == Character.NON_SPACING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(caracter)) {
                limpio.append(Character.toUpperCase(caracter));
            } else if (Character.isWhitespace(caracter) || caracter == '-') {
                // El guion de un apellido compuesto separa palabras igual que un
                // espacio: "GARCIA-LOPEZ" y "GARCIA LOPEZ" son el mismo nombre.
                limpio.append(' ');
            }
            // Todo lo demas —puntos, comas, apostrofos— se descarta.
        }

        return limpio.toString().trim().replaceAll(" +", " ").toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
