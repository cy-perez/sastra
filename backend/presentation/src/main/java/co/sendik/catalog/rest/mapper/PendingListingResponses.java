package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.rest.dto.MoneyPayload;
import co.sendik.catalog.rest.dto.PendingListingResponse;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Traduce una publicacion a la fila que el moderador ve en su bandeja. HU-008.
 *
 * <p>Lo que no sale es tan deliberado como lo que sale: aqui no viaja el identificador
 * del vendedor. La fila dice si la publicacion es de quien mira —{@code own}, para
 * RN-063— y nada mas, porque una bandeja que reparte identificadores de vendedores es una
 * lista de quien vende que, disponible para cualquiera con el rol.
 */
public final class PendingListingResponses {

    /** La toma de 0 grados: es la frontal de RN-016 y la que reconoce la prenda. */
    private static final int FRENTE = 0;

    private PendingListingResponses() {}

    public static PendingListingResponse de(Listing publicacion, ModeratorId quienModera, PublicFileStore almacen) {
        return new PendingListingResponse(
                publicacion.id().value().toString(),
                tituloDe(publicacion),
                dinero(publicacion),
                // Nunca nulo en la practica —solo se listan las que estan en revision, y
                // entrar ahi sella la fecha—, pero el tipo lo admite: las filas que
                // quedaron en revision antes de V12 no lo tienen. Se cae a `updatedAt`, que
                // para ellas es exactamente el momento en que entraron.
                Objects.requireNonNullElse(publicacion.submittedAt(), publicacion.updatedAt()),
                publicacion.requiereAtencion(),
                marcas(publicacion.attentionReasons()),
                portada(publicacion, almacen),
                publicacion.laPublico(quienModera));
    }

    /**
     * El titulo y el precio son anulables en el dominio —un borrador esta a medias— pero
     * aqui no pueden faltar: a esta bandeja solo llega lo que esta en revision, y entrar
     * exige el producto completo (RN-021, {@code exigirCompletoPara}).
     *
     * <p>Por eso se exige en vez de rellenar con un vacio. Un titulo en blanco en la cola
     * no seria un detalle cosmetico: seria que algo incompleto se cologo en revision, y
     * taparlo dejaria al moderador decidiendo sobre lo que no puede ver.
     */
    private static String tituloDe(Listing publicacion) {
        Title titulo = Objects.requireNonNull(
                publicacion.product().title(), "Una publicacion en revision no puede estar sin titulo");
        return titulo.value();
    }

    private static MoneyPayload dinero(Listing publicacion) {
        Money precio = Objects.requireNonNull(
                publicacion.product().price(), "Una publicacion en revision no puede estar sin precio");
        return new MoneyPayload(precio.amount(), Money.MONEDA);
    }

    private static Set<String> marcas(Set<AttentionReason> motivos) {
        return motivos.stream().map(AttentionReason::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * La frontal, o nada.
     *
     * <p>No se cae a otra toma cuando falta: una lista donde la miniatura es a veces la
     * espalda de la prenda enseña a desconfiar de la miniatura. Sin frontal, la fila lo
     * dice y quien revisa abre el detalle.
     */
    private static @Nullable String portada(Listing publicacion, PublicFileStore almacen) {
        return publicacion.tomasDelVendedor().stream()
                .filter(imagen -> imagen.position() == FRENTE)
                .findFirst()
                .map(ProductImage::objectKey)
                .map(clave -> almacen.direccionDe(clave).toString())
                .orElse(null);
    }
}
