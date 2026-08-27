package co.sendik.catalog.rest;

import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.model.WarrantyMonths;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Publicaciones de ejemplo para las pruebas del borde.
 *
 * <p>Se arma con {@code Listing.existente} y no recorriendo los casos de uso: lo que se
 * prueba aqui es la traduccion a JSON, y montar el ciclo completo para llegar a un estado
 * haria que un cambio en las reglas de transicion rompiera pruebas que no hablan de eso.
 */
final class CatalogoDelBorde {

    static final Instant AHORA = Instant.parse("2026-08-25T15:00:00Z");

    /**
     * Cuando entro a revision. **Distinto de AHORA a proposito**: con los dos iguales, una
     * prueba que afirmara `waitingSince` pasaba igual leyendo `updatedAt`, que es
     * exactamente la confusion que V12 existe para deshacer.
     */
    static final Instant ENTRO_A_REVISION = Instant.parse("2026-08-25T09:00:00Z");

    static final String TITULO = "Camisa de lino color hueso";

    private CatalogoDelBorde() {}

    static Listing publicada(SellerId vendedor) {
        return con(vendedor, ListingStatus.PUBLISHED, null, null, Set.of());
    }

    static Listing borrador(SellerId vendedor) {
        return con(vendedor, ListingStatus.DRAFT, null, null, Set.of());
    }

    static Listing con(
            SellerId vendedor,
            ListingStatus estado,
            @Nullable ListingRejectionReason motivo,
            @Nullable String nota,
            Set<co.sendik.catalog.model.AttentionReason> marcas) {

        return Listing.reconstruir()
                .id(ListingId.nuevo())
                .producto(producto(vendedor))
                .estado(estado)
                .imagenes(List.of(toma(0), toma(1)))
                .enviada(estado == ListingStatus.PENDING_REVIEW ? ENTRO_A_REVISION : null)
                .publicada(estado == ListingStatus.PUBLISHED ? AHORA : null)
                .decididaPor(null, motivo == null ? null : AHORA)
                .rechazadaPor(motivo, nota)
                .marcas(marcas)
                .version(7L)
                .creada(AHORA)
                .tocada(AHORA)
                .armar();
    }

    /**
     * Tecnologia sellada: cuatro tomas propias y una imagen de referencia.
     *
     * <p>Es la unica forma que admite una imagen de referencia (RN-066) y la unica que baja
     * de ocho tomas a cuatro (RN-065), asi que los criterios 37, 39 y 41 se prueban con
     * esta.
     */
    static Listing tecnologiaSellada(SellerId vendedor) {
        Product telefono = new Product(
                ProductId.nuevo(),
                vendedor,
                CategoryId.nuevo(),
                new Title("Telefono nuevo sellado"),
                new Description("Caja cerrada, factura incluida."),
                null,
                Condition.NEW,
                Size.unica(),
                new Measurements(Map.of()),
                Color.BLACK,
                Money.dePesos(2_400_000),
                new ShippingDimensions(700, new BigDecimal("20.0"), new BigDecimal("12.0"), new BigDecimal("8.0")),
                true,
                new WarrantyMonths(12));

        return Listing.reconstruir()
                .id(ListingId.nuevo())
                .producto(telefono)
                .estado(ListingStatus.DRAFT)
                .imagenes(List.of(toma(0), toma(2), toma(4), toma(6), referencia(0)))
                .version(3L)
                .creada(AHORA)
                .tocada(AHORA)
                .armar();
    }

    static Product producto(SellerId vendedor) {
        Map<MeasurementKind, BigDecimal> medidas = new EnumMap<>(MeasurementKind.class);
        medidas.put(MeasurementKind.CHEST, new BigDecimal("52.0"));

        return new Product(
                ProductId.nuevo(),
                vendedor,
                CategoryId.nuevo(),
                new Title(TITULO),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(medidas),
                Color.BEIGE,
                Money.dePesos(185_000),
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);
    }

    /** Solo se admite en tecnologia sellada, y nunca cuenta como toma (RN-066). */
    static ProductImage referencia(int posicion) {
        return ProductImage.referencia(
                ProductImageId.nuevo(),
                new FileKey("productos/referencia-" + posicion + ".jpg"),
                posicion,
                new ImageDimensions(900, 1200),
                90_000L,
                ImageContentType.JPEG);
    }

    static ProductImage toma(int posicion) {
        return ProductImage.toma(
                ProductImageId.nuevo(),
                new FileKey("productos/clave-opaca-" + posicion + ".jpg"),
                posicion,
                new ImageDimensions(900, 1200),
                120_000L,
                ImageContentType.JPEG);
    }
}
