package co.sastra.identity.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * Fecha de nacimiento declarada en el registro.
 *
 * <p>La regla de mayoria de edad vive aqui y no en un servicio aparte: es una
 * pregunta que el propio objeto puede responder, y el dominio se valida a si
 * mismo (backend/CLAUDE.md).
 *
 * <p>La fecha de referencia se recibe como parametro en lugar de leer el reloj.
 * Asi la regla se prueba con casos exactos, incluido el cumpleanos numero 18, en
 * vez de depender del dia en que se ejecuten las pruebas.
 */
public record BirthDate(LocalDate value) {

    /** RN-008. Solo mayores de 18. */
    public static final int EDAD_MINIMA = 18;

    /** Nadie vive tanto. Atrapa un error de tecleo del ano, no una persona real. */
    private static final int EDAD_MAXIMA_PLAUSIBLE = 120;

    public BirthDate {
        Objects.requireNonNull(value, "La fecha de nacimiento es obligatoria");
    }

    /**
     * @param hoy fecha contra la que se calcula la edad
     * @return true si ya cumplio {@value #EDAD_MINIMA} anos. El dia del cumpleanos cuenta.
     */
    public boolean esMayorDeEdad(LocalDate hoy) {
        Objects.requireNonNull(hoy, "La fecha de referencia es obligatoria");
        return Period.between(value, hoy).getYears() >= EDAD_MINIMA;
    }

    /** Una fecha futura o imposible es un error de captura, no una persona menor de edad. */
    public boolean esPlausible(LocalDate hoy) {
        Objects.requireNonNull(hoy, "La fecha de referencia es obligatoria");
        return !value.isAfter(hoy) && Period.between(value, hoy).getYears() <= EDAD_MAXIMA_PLAUSIBLE;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
