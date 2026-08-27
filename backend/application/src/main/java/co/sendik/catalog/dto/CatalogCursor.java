package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import java.time.Instant;
import java.util.Objects;

/**
 * Por donde sigue el catalogo. HU-009.
 *
 * <p><strong>Por cursor y no por numero de pagina</strong>, y el motivo lo escribe
 * contrato-api.md: sobre contenido que se inserta constantemente, la paginacion por
 * desplazamiento repite y salta elementos. Si mientras alguien mira la primera pantalla
 * se aprueban tres publicaciones, la pagina 2 por desplazamiento le devuelve tres que ya
 * vio; y si se archivan tres, se salta tres que nunca vera.
 *
 * <p>Lleva los dos valores por los que ordena el listado y no solo la fecha:
 * {@code published_at} puede repetirse —dos publicaciones aprobadas en el mismo instante
 * no son imposibles, y en una prueba con reloj fijo son la norma— y un cursor que solo
 * mirara la fecha se saltaria la segunda o la repetiria para siempre. El identificador
 * desempata y es unico, asi que el par nunca lo es.
 *
 * <p><strong>No sabe nada de base64 ni de JSON.</strong> Esto es la pregunta; convertirla
 * en una cadena opaca que viaje por HTTP es cosa del borde, y por eso el codificador vive
 * en {@code presentation}. Aqui es un par de valores tipados.
 *
 * @param publicadaEn cuando se publico el ultimo elemento entregado
 * @param id cual era ese elemento, para desempatar
 */
public record CatalogCursor(Instant publicadaEn, ListingId id) {

    public CatalogCursor {
        Objects.requireNonNull(publicadaEn, "El instante de publicacion es obligatorio");
        Objects.requireNonNull(id, "El identificador es obligatorio");
    }
}
