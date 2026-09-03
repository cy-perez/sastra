package co.sendik.catalog.rest.mapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Un par (instante, identificador) convertido en cadena opaca, y vuelta.
 *
 * <p>Es la mecanica que comparten los dos listados por cursor del catalogo: el publico
 * ordena por cuando se publico y la lista de favoritos por cuando se guardo, pero las dos
 * preguntas tienen la misma forma y viajan igual. Se extrajo aqui al llegar HU-011, cuando
 * la alternativa era copiar el base64, el JSON y el manejo del cursor corrupto en un
 * segundo archivo, donde tarde o temprano uno de los dos deja de rechazar lo que no
 * entiende.
 *
 * <p><strong>Lo que no se comparte es el tipo del cursor</strong>, y eso es deliberado:
 * {@code CatalogCursor} y {@code FavoriteCursor} siguen siendo records distintos, asi que
 * el compilador impide que el cursor de un listado se use en el otro. Lo comun es como se
 * transporta; lo que significa, no.
 *
 * <p><strong>Vive en el borde y no en {@code application}.</strong> El cursor es la
 * pregunta —desde cuando y desde cual—; que viaje en base64 dentro de una URL es una
 * decision de transporte, y ponerla en el caso de uso ataria la aplicacion a HTTP.
 *
 * <p><strong>Opaco a proposito.</strong> El cliente no lo lee ni lo construye: lo recibe y
 * lo devuelve. Asi, el dia que el orden de un listado cambie —y cambiara cuando llegue la
 * relevancia de Fase 3— el contenido del cursor cambia sin romper a nadie. Es tambien la
 * razon de que se codifique en lugar de mandar dos parametros sueltos: dos parametros
 * legibles invitan a que alguien los fabrique a mano y quedan en el contrato para siempre.
 *
 * <p>En base64 <strong>de URL</strong> y sin relleno: viaja en una cadena de consulta, y el
 * {@code +} del alfabeto normal se interpreta como espacio.
 */
final class Cursores {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** El instante del ultimo elemento entregado. */
    private static final String CAMPO_INSTANTE = "p";

    /** Cual era, para desempatar. */
    private static final String CAMPO_ID = "i";

    private Cursores() {}

    /** El par tal como sale del transporte, todavia sin tipo de dominio. */
    record Par(Instant instante, UUID id) {}

    static String texto(Instant instante, UUID id) {
        String json = JSON.createObjectNode()
                .put(CAMPO_INSTANTE, instante.toString())
                .put(CAMPO_ID, id.toString())
                .toString();

        return ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws IllegalArgumentException si no es un cursor de este proyecto. Sale como 400,
     *     que es lo que el manejador ya hace con esta excepcion. Un cursor corrupto que se
     *     ignorara en silencio devolveria la primera pagina, y quien esta recorriendo una
     *     lista volveria al principio sin enterarse
     */
    static Par par(String texto) {
        try {
            JsonNode nodo = JSON.readTree(new String(DECODER.decode(texto), StandardCharsets.UTF_8));

            return new Par(
                    Instant.parse(nodo.get(CAMPO_INSTANTE).asString()),
                    UUID.fromString(nodo.get(CAMPO_ID).asString()));

        } catch (RuntimeException e) {
            // Se traga el motivo a proposito: decirle a quien manda un cursor inventado
            // que le falta el campo "p" es ensenarle a fabricar uno valido.
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }
}
