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

        return Listing.existente(
                ListingId.nuevo(),
                producto(vendedor),
                estado,
                List.of(toma(0), toma(1)),
                estado == ListingStatus.PUBLISHED ? AHORA : null,
                null,
                motivo == null ? null : AHORA,
                motivo,
                nota,
                marcas,
                7L,
                AHORA,
                AHORA);
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
