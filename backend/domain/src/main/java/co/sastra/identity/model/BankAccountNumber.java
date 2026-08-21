package co.sastra.identity.model;

import java.util.Objects;

/**
 * Numero de la cuenta donde el vendedor recibe. Dato sensible (ADR-0020, RN-046).
 *
 * <p>Solo digitos y un rango de largo, por lo mismo que
 * {@link IdentityDocumentNumber}: cada entidad numera como quiere, y una billetera
 * usa el numero de celular como cuenta. Una regla mas estrecha rechazaria cuentas
 * validas, y el precio de equivocarse lo paga alguien que no puede cobrar.
 *
 * <p>Se normaliza quitando lo que la gente escribe de adorno. Aqui la normalizacion
 * no protege una unicidad como en el documento —dos personas pueden compartir
 * cuenta, y eso no lo prohibe ninguna regla— sino el desembolso: un numero con un
 * espacio de sobra es un numero que la pasarela no encuentra.
 */
public record BankAccountNumber(String value) {

    private static final int LARGO_MINIMO = 6;

    private static final int LARGO_MAXIMO = 25;

    public BankAccountNumber {
        Objects.requireNonNull(value, "El numero de la cuenta es obligatorio");
        value = normalizar(value);

        if (value.length() < LARGO_MINIMO || value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El numero de la cuenta tiene que llevar entre " + LARGO_MINIMO + " y "
                    + LARGO_MAXIMO + " digitos");
        }
        if (!soloDigitos(value)) {
            throw new IllegalArgumentException("El numero de la cuenta solo admite digitos");
        }
    }

    /**
     * Los cuatro ultimos digitos, que es lo unico que se muestra
     * (docs/operacion/datos-personales.md).
     */
    public String ultimosCuatro() {
        return value.substring(value.length() - 4);
    }

    /** No devuelve el numero, por lo mismo que {@link IdentityDocumentNumber}. */
    @Override
    public String toString() {
        return "BankAccountNumber[****" + ultimosCuatro() + "]";
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
