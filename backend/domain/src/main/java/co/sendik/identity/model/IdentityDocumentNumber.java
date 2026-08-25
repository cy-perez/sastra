package co.sendik.identity.model;

import java.util.Objects;

/**
 * Numero del documento de identidad. Dato sensible (ADR-0020).
 *
 * <p><strong>La validacion es la minima defendible y eso es deliberado.</strong>
 * Solo digitos y un rango de largo. El formato exacto de cada tipo —cuantos digitos
 * tiene una cedula de extranjeria, como se compone un PPT— no esta decidido en
 * {@code docs/producto/reglas-negocio.md}, y afinar la regla aqui con lo que uno
 * recuerde seria inventarla: el efecto de equivocarse es rechazar a alguien que
 * tiene un documento valido, en la puerta de entrada a vender.
 *
 * <p>Se normaliza antes de validar porque la gente escribe su cedula con puntos y
 * con espacios. Sin normalizar, la misma persona podria quedar verificada dos veces
 * escribiendola distinto, y eso rompe RN-010 sin que nadie lo note: el criterio 5
 * compara este valor, y "1.234" y "1234" no son iguales como texto.
 *
 * <p>Este objeto <strong>nunca</strong> se serializa hacia la API: solo salen los
 * cuatro ultimos digitos (criterio 11 de HU-002, RN-046). Por eso no expone un
 * {@code toString} con el valor.
 */
public record IdentityDocumentNumber(String value) {

    private static final int LARGO_MINIMO = 5;

    private static final int LARGO_MAXIMO = 15;

    public IdentityDocumentNumber {
        Objects.requireNonNull(value, "El numero del documento es obligatorio");
        value = normalizar(value);

        if (value.length() < LARGO_MINIMO || value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El numero del documento tiene que llevar entre " + LARGO_MINIMO + " y "
                    + LARGO_MAXIMO + " digitos");
        }
        if (!soloDigitos(value)) {
            throw new IllegalArgumentException("El numero del documento solo admite digitos");
        }
    }

    /** Los cuatro ultimos, que es lo unico que se muestra en pantalla. */
    public String ultimosCuatro() {
        return value.substring(value.length() - 4);
    }

    /**
     * No devuelve el numero.
     *
     * <p>Un {@code toString} que lo devolviera acabaria en un registro: basta con
     * interpolar el objeto en un mensaje de excepcion para que el numero de documento
     * de alguien quede escrito en Cloud Logging, y
     * {@code docs/operacion/datos-personales.md} lo prohibe expresamente, tambien en
     * nivel depuracion y tambien parcialmente.
     */
    @Override
    public String toString() {
        return "IdentityDocumentNumber[****" + ultimosCuatro() + "]";
    }

    private static String normalizar(String texto) {
        StringBuilder limpio = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);
            if (caracter != '.' && caracter != ' ' && caracter != '-' && caracter != ',') {
                limpio.append(caracter);
            }
        }
        return limpio.toString();
    }

    private static boolean soloDigitos(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            if (texto.charAt(i) < '0' || texto.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
