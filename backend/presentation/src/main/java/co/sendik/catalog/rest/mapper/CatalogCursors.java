package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.model.ListingId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El cursor del catalogo, de par de valores a cadena opaca y vuelta. HU-009, criterio 3.
 *
 * <p><strong>Vive en el borde y no en {@code application}, y eso no es un detalle de
 * organizacion.</strong> {@link CatalogCursor} es la pregunta —desde cuando y desde cual—;
 * que viaje en base64 dentro de una URL es una decision de transporte. Poner el base64 en
 * el caso de uso ataria la aplicacion a HTTP, que es justo lo que la direccion de las
 * dependencias prohibe.
 *
 * <p><strong>Opaco a proposito.</strong> El cliente no lo lee ni lo construye: lo recibe y
 * lo devuelve. Asi el dia que el orden del catalogo cambie —y cambiara cuando llegue la
 * relevancia de Fase 3— el contenido del cursor cambia sin romper a nadie. Es tambien la
 * razon de que se codifique en lugar de mandar dos parametros sueltos: dos parametros
 * legibles invitan a que alguien los fabrique a mano y quedan en el contrato para siempre.
 *
 * <p>En base64 <strong>de URL</strong> y sin relleno: viaja en una cadena de consulta, y
 * el {@code +} del alfabeto normal se interpreta como espacio.
 *
 * <p>Cualquier cadena que no descifre es un 400 y no un tramo arbitrario. Un cursor
 * corrupto que se ignorara en silencio devolveria la primera pagina, y quien esta
 * recorriendo el catalogo volveria al principio sin enterarse.
 */
public final class CatalogCursors {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** Cuando se publico el ultimo elemento entregado. */
    private static final String CAMPO_INSTANTE = "p";

    /** Cual era, para desempatar. */
    private static final String CAMPO_ID = "i";

    private CatalogCursors() {}

    public static @Nullable String texto(@Nullable CatalogCursor cursor) {
        if (cursor == null) {
            return null;
        }

        String json = JSON.createObjectNode()
                .put(CAMPO_INSTANTE, cursor.publicadaEn().toString())
                .put(CAMPO_ID, cursor.id().value().toString())
                .toString();

        return ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws IllegalArgumentException si no es un cursor de este listado. Sale como 400,
     *     que es lo que el manejador ya hace con esta excepcion
     */
    public static @Nullable CatalogCursor cursor(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        try {
            JsonNode nodo = JSON.readTree(new String(DECODER.decode(texto), StandardCharsets.UTF_8));

            return new CatalogCursor(
                    Instant.parse(nodo.get(CAMPO_INSTANTE).asString()),
                    ListingId.de(nodo.get(CAMPO_ID).asString()));

        } catch (RuntimeException e) {
            // Se traga el motivo a proposito: decirle a quien manda un cursor inventado
            // que le falta el campo "p" es ensenarle a fabricar uno valido.
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }
}
