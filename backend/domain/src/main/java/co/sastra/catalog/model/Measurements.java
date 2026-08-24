package co.sastra.catalog.model;

import co.sastra.catalog.exception.MeasurementsIncompleteException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Las medidas reales que declara el vendedor, en centimetros. RN-021.
 *
 * <p>Son obligatorias porque son la causa numero uno de devolucion en moda de segunda
 * mano. Cuales hacen falta lo decide el {@link MeasurementGroup} de la categoria, y
 * esa comprobacion es {@link #exigirCompletasPara}: no se hace en el constructor
 * porque un borrador se guarda a medias —el vendedor mide con la prenda en la mano y
 * vuelve— y solo al enviar a revision tienen que estar todas.
 *
 * <p>Un decimal como maximo. Media prenda no se mide en micras, y admitir mas
 * precision solo invita a copiar un numero de una ficha tecnica.
 */
public record Measurements(Map<MeasurementKind, BigDecimal> valores) {

    private static final int DECIMALES_MAXIMOS = 1;

    /** Nada mide 400 cm en este catalogo, y quien teclee 4000 se equivoco. */
    private static final BigDecimal MAXIMO_CM = new BigDecimal("400");

    public Measurements {
        Objects.requireNonNull(valores, "Las medidas son obligatorias");

        Map<MeasurementKind, BigDecimal> copia = new EnumMap<>(MeasurementKind.class);
        valores.forEach((medida, centimetros) -> {
            Objects.requireNonNull(medida, "La medida es obligatoria");
            Objects.requireNonNull(centimetros, () -> "La medida " + medida + " no puede venir vacia");

            if (centimetros.signum() <= 0) {
                throw new IllegalArgumentException("La medida " + medida + " tiene que ser positiva: " + centimetros);
            }
            if (centimetros.scale() > DECIMALES_MAXIMOS) {
                throw new IllegalArgumentException(
                        "La medida " + medida + " admite un decimal como maximo: " + centimetros);
            }
            if (centimetros.compareTo(MAXIMO_CM) > 0) {
                throw new IllegalArgumentException("La medida " + medida + " no es plausible: " + centimetros + " cm");
            }
            copia.put(medida, centimetros);
        });

        valores = Map.copyOf(copia);
    }

    public static Measurements vacias() {
        return new Measurements(Map.of());
    }

    /**
     * Comprueba que estan todas las del grupo. Se llama al enviar a revision.
     *
     * <p>Una medida que sobra no es un error: si alguien declara el pecho de un
     * pantalon, el dato es inutil pero no es falso, y rechazar la publicacion por eso
     * seria friccion sin motivo. Lo que se exige es que no falte ninguna.
     *
     * @throws MeasurementsIncompleteException si falta alguna
     */
    public void exigirCompletasPara(MeasurementGroup grupo) {
        Objects.requireNonNull(grupo, "El grupo de medida es obligatorio");

        Set<MeasurementKind> faltantes = EnumSet.copyOf(grupo.obligatorias());
        faltantes.removeAll(valores.keySet());

        if (!faltantes.isEmpty()) {
            throw new MeasurementsIncompleteException(grupo, faltantes);
        }
    }

    public boolean estanCompletasPara(MeasurementGroup grupo) {
        return valores.keySet().containsAll(grupo.obligatorias());
    }
}
