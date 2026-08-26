package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.model.WarrantyMonths;
import co.sendik.catalog.rest.dto.ListingImageResponse;
import co.sendik.catalog.rest.dto.ListingResponse;
import co.sendik.catalog.rest.dto.MoneyPayload;
import co.sendik.catalog.rest.dto.ProductResponse;
import co.sendik.catalog.rest.dto.PublicListingResponse;
import co.sendik.catalog.rest.dto.ShippingPayload;
import co.sendik.catalog.rest.dto.SizePayload;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Del dominio a las dos formas de respuesta.
 *
 * <p><strong>Son dos y no una con campos ocultos.</strong> {@link #de} es para el dueno y
 * el moderador y lleva la cocina de la moderacion; {@link #publica} es para cualquiera y
 * no la lleva. Con una sola forma y un filtro, agregar un campo lo publica sin que ninguna
 * prueba lo note; con dos, lo que no esta declarado no tiene por donde salir.
 *
 * <p>La direccion de cada imagen la construye {@code PublicFileStore}: la clave guardada
 * es opaca y quien sabe convertirla en una direccion es el almacen, que ademas cambia
 * entre local y nube (ADR-0018).
 */
public final class ListingResponses {

    private ListingResponses() {}

    /** Para el dueno y para el moderador. */
    public static ListingResponse de(Listing publicacion, PublicFileStore almacen) {
        return new ListingResponse(
                publicacion.id().value().toString(),
                publicacion.sellerId().value().toString(),
                publicacion.status().name(),
                producto(publicacion.product()),
                imagenes(publicacion, almacen),
                publicacion.tomasExigidas(),
                publicacion.requiereAtencion(),
                marcas(publicacion.attentionReasons()),
                nombreDe(publicacion.rejectionReason()),
                publicacion.rejectionNote(),
                publicacion.publishedAt(),
                publicacion.createdAt(),
                publicacion.updatedAt(),
                publicacion.version());
    }

    /** Para quien no es ni el dueno ni un moderador. Solo se llama con algo visible. */
    public static PublicListingResponse publica(Listing publicacion, PublicFileStore almacen) {
        return new PublicListingResponse(
                publicacion.id().value().toString(),
                publicacion.sellerId().value().toString(),
                producto(publicacion.product()),
                imagenes(publicacion, almacen),
                publicacion.publishedAt());
    }

    private static ProductResponse producto(Product producto) {
        return new ProductResponse(
                producto.categoryId().value().toString(),
                valorDe(producto.title()),
                valorDe(producto.description()),
                valorDe(producto.brand()),
                nombreDe(producto.condition()),
                talla(producto.size()),
                medidas(producto),
                nombreDe(producto.color()),
                dinero(producto.price()),
                envio(producto.shipping()),
                producto.isSealed(),
                meses(producto.warranty()));
    }

    private static List<ListingImageResponse> imagenes(Listing publicacion, PublicFileStore almacen) {
        return publicacion.images().stream()
                .map(imagen -> new ListingImageResponse(
                        imagen.id().value().toString(),
                        imagen.kind().name(),
                        imagen.position(),
                        imagen.angleDegrees(),
                        almacen.direccionDe(imagen.objectKey()).toString()))
                .toList();
    }

    /**
     * Las medidas con su clave como texto.
     *
     * <p>{@link LinkedHashMap} y no {@code Map.of}: el orden del {@code EnumMap} del
     * dominio es el de la enumeracion, y es el orden en que el formulario las pide. Un
     * mapa sin orden reordenaria la ficha en cada respuesta.
     */
    private static Map<String, BigDecimal> medidas(Product producto) {
        Map<String, BigDecimal> texto = new LinkedHashMap<>();
        producto.measurements().valores().forEach((medida, centimetros) -> texto.put(medida.name(), centimetros));
        return texto;
    }

    private static Set<String> marcas(Set<AttentionReason> motivos) {
        return motivos.stream().map(AttentionReason::name).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static @Nullable SizePayload talla(@Nullable Size talla) {
        return talla == null ? null : new SizePayload(talla.system().name(), talla.value());
    }

    private static @Nullable MoneyPayload dinero(@Nullable Money precio) {
        return precio == null ? null : new MoneyPayload(precio.amount(), Money.MONEDA);
    }

    private static @Nullable ShippingPayload envio(@Nullable ShippingDimensions envio) {
        return envio == null
                ? null
                : new ShippingPayload(envio.weightGrams(), envio.lengthCm(), envio.widthCm(), envio.heightCm());
    }

    private static @Nullable String valorDe(@Nullable Title titulo) {
        return titulo == null ? null : titulo.value();
    }

    private static @Nullable String valorDe(@Nullable Description descripcion) {
        return descripcion == null ? null : descripcion.value();
    }

    private static @Nullable String valorDe(@Nullable Brand marca) {
        return marca == null ? null : marca.value();
    }

    private static @Nullable String nombreDe(@Nullable Condition condicion) {
        return condicion == null ? null : condicion.name();
    }

    private static @Nullable String nombreDe(@Nullable Color color) {
        return color == null ? null : color.name();
    }

    private static @Nullable String nombreDe(@Nullable ListingRejectionReason motivo) {
        return motivo == null ? null : motivo.name();
    }

    private static @Nullable Integer meses(@Nullable WarrantyMonths garantia) {
        return garantia == null ? null : garantia.value();
    }
}
