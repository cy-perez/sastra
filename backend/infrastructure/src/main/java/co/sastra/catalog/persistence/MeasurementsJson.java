package co.sastra.catalog.persistence;

import co.sastra.catalog.model.MeasurementKind;
import co.sastra.catalog.model.Measurements;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traduce las medidas entre el dominio y la columna {@code jsonb}.
 *
 * <p>Vive en infraestructura y no en el dominio porque es un detalle de como se
 * guardan: el dominio no sabe que existe una base de datos ni que hay JSON de por
 * medio (docs/arquitectura/vision-tecnica.md).
 *
 * <p><strong>Las claves salen ordenadas.</strong> No es cosmetica: dos publicaciones
 * con las mismas medidas producen el mismo texto, y eso hace que un {@code UPDATE} que
 * no cambia nada no ensucie el historial ni dispare una escritura inutil.
 *
 * <p>Los numeros se guardan como texto dentro del JSON y se leen con
 * {@link BigDecimal}. Un {@code double} en medio arruinaria la precision que RN-029
 * exige para el dinero y que aqui vale igual: una medida es un dato que el comprador
 * compara con una cinta metrica.
 */
final class MeasurementsJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MeasurementsJson() {}

    static String aJson(Measurements medidas) {
        ObjectNode raiz = JSON.createObjectNode();

        Map<String, BigDecimal> ordenadas = new TreeMap<>();
        medidas.valores().forEach((medida, centimetros) -> ordenadas.put(medida.name(), centimetros));
        ordenadas.forEach(raiz::put);

        return raiz.toString();
    }

    static Measurements deJson(String texto) {
        if (texto == null || texto.isBlank()) {
            return Measurements.vacias();
        }

        JsonNode raiz = JSON.readTree(texto);
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);

        raiz.propertyNames()
                .forEach(clave -> valores.put(
                        MeasurementKind.valueOf(clave), raiz.get(clave).decimalValue()));

        return new Measurements(valores);
    }
}
