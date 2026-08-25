package co.sastra.catalog.persistence;

import co.sastra.catalog.exception.ListingConcurrentlyModifiedException;
import co.sastra.catalog.model.AttentionReason;
import co.sastra.catalog.model.Brand;
import co.sastra.catalog.model.CategoryId;
import co.sastra.catalog.model.Color;
import co.sastra.catalog.model.Condition;
import co.sastra.catalog.model.Description;
import co.sastra.catalog.model.ImageKind;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ListingRejectionReason;
import co.sastra.catalog.model.ListingStatus;
import co.sastra.catalog.model.ModeratorId;
import co.sastra.catalog.model.Product;
import co.sastra.catalog.model.ProductId;
import co.sastra.catalog.model.ProductImage;
import co.sastra.catalog.model.ProductImageId;
import co.sastra.catalog.model.SellerId;
import co.sastra.catalog.model.ShippingDimensions;
import co.sastra.catalog.model.Size;
import co.sastra.catalog.model.SizeSystem;
import co.sastra.catalog.model.Title;
import co.sastra.catalog.model.WarrantyMonths;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImageDimensions;
import co.sastra.shared.money.Money;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia del catalogo. HU-007.
 *
 * <p>Un repositorio por agregado y no por tabla: guardar una publicacion escribe en
 * {@code listings}, {@code products} y {@code product_images} dentro de la misma
 * transaccion. No hay forma de guardar una imagen suelta desde fuera, y eso es
 * deliberado: si la hubiera, alguien podria dejar el agregado en un estado que el
 * dominio nunca habria permitido, como una publicacion visible con siete tomas.
 *
 * <p><strong>Las imagenes se reescriben enteras en cada guardado.</strong> Son ocho
 * filas como maximo y comparar cual cambio costaria mas codigo del que ahorra. Lo que
 * si importa es que el borrado y la insercion van juntos, para que nunca exista un
 * instante con la mitad de la secuencia.
 *
 * <p>El bloqueo optimista es del criterio 34: el {@code UPDATE} exige la version que
 * se leyo, y si no la encuentra es que alguien escribio en medio.
 */
@Repository
public class JdbcListingRepository implements ListingRepository {

    private static final String SELECT_BASE = """
            SELECT l.id, l.status, l.published_at, l.sold_at,
                   l.moderated_by, l.moderated_at, l.rejection_reason, l.rejection_note,
                   l.attention_reasons, l.version,
                   l.created_at, l.updated_at,
                   p.id AS product_id, p.seller_id, p.category_id, p.title, p.description, p.brand,
                   p.condition, p.size_system, p.size_value, p.measurements, p.color, p.price,
                   p.weight_grams, p.length_cm, p.width_cm, p.height_cm,
                   p.is_sealed, p.manufacturer_warranty_months
            FROM listings l
            JOIN products p ON p.id = l.product_id
            """;

    private final JdbcClient jdbc;

    public JdbcListingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Listing guardar(Listing publicacion) {
        guardarProducto(publicacion.product());
        long versionNueva = guardarPublicacion(publicacion);
        reescribirImagenes(publicacion);

        return publicacion.conVersion(versionNueva);
    }

    @Override
    public Optional<Listing> buscar(ListingId id) {
        return jdbc.sql(SELECT_BASE + " WHERE l.id = :id")
                .param("id", id.value())
                .query(JdbcListingRepository::filaAPublicacion)
                .optional()
                .map(this::conImagenes);
    }

    @Override
    public Optional<Listing> buscarDelDueno(ListingId id, SellerId vendedor) {
        return jdbc.sql(SELECT_BASE + " WHERE l.id = :id AND p.seller_id = :vendedor")
                .param("id", id.value())
                .param("vendedor", vendedor.value())
                .query(JdbcListingRepository::filaAPublicacion)
                .optional()
                .map(this::conImagenes);
    }

    @Override
    public List<Listing> buscarDelVendedor(SellerId vendedor, int pagina, int tamano) {
        return jdbc
                .sql(SELECT_BASE
                        + " WHERE p.seller_id = :vendedor ORDER BY l.created_at DESC LIMIT :limite OFFSET :salto")
                .param("vendedor", vendedor.value())
                .param("limite", tamano)
                .param("salto", (long) pagina * tamano)
                .query(JdbcListingRepository::filaAPublicacion)
                .list()
                .stream()
                .map(this::conImagenes)
                .toList();
    }

    // ------------------------------------------------------------------ escritura

    private void guardarProducto(Product producto) {
        jdbc.sql("""
                        INSERT INTO products (
                            id, seller_id, category_id, title, description, brand, condition,
                            size_system, size_value, measurements, color, price,
                            weight_grams, length_cm, width_cm, height_cm,
                            is_sealed, manufacturer_warranty_months, updated_at)
                        VALUES (
                            :id, :vendedor, :categoria, :titulo, :descripcion, :marca, :condicion,
                            :sistemaTalla, :talla, :medidas::jsonb, :color, :precio,
                            :gramos, :largo, :ancho, :alto,
                            :sellado, :garantia, now())
                        ON CONFLICT (id) DO UPDATE SET
                            category_id                  = EXCLUDED.category_id,
                            title                        = EXCLUDED.title,
                            description                  = EXCLUDED.description,
                            brand                        = EXCLUDED.brand,
                            condition                    = EXCLUDED.condition,
                            size_system                  = EXCLUDED.size_system,
                            size_value                   = EXCLUDED.size_value,
                            measurements                 = EXCLUDED.measurements,
                            color                        = EXCLUDED.color,
                            price                        = EXCLUDED.price,
                            weight_grams                 = EXCLUDED.weight_grams,
                            length_cm                    = EXCLUDED.length_cm,
                            width_cm                     = EXCLUDED.width_cm,
                            height_cm                    = EXCLUDED.height_cm,
                            is_sealed                    = EXCLUDED.is_sealed,
                            manufacturer_warranty_months = EXCLUDED.manufacturer_warranty_months,
                            updated_at                   = now()
                        """)
                .param("id", producto.id().value())
                .param("vendedor", producto.sellerId().value())
                .param("categoria", producto.categoryId().value())
                .param("titulo", producto.title().value())
                .param("descripcion", producto.description().value())
                .param(
                        "marca",
                        producto.brand() == null ? null : producto.brand().value())
                .param("condicion", producto.condition().name())
                .param("sistemaTalla", producto.size().system().name())
                .param("talla", producto.size().value())
                .param("medidas", MeasurementsJson.aJson(producto.measurements()))
                .param("color", producto.color().name())
                .param("precio", producto.price().enPesos())
                .param("gramos", producto.shipping().weightGrams())
                .param("largo", producto.shipping().lengthCm())
                .param("ancho", producto.shipping().widthCm())
                .param("alto", producto.shipping().heightCm())
                .param("sellado", producto.isSealed())
                .param(
                        "garantia",
                        producto.warranty() == null ? null : producto.warranty().value())
                .update();
    }

    /**
     * Inserta o actualiza exigiendo la version leida. Criterio 34.
     *
     * <p>Cuando el {@code UPDATE} no toca ninguna fila, la version cambio entre la
     * lectura y la escritura: alguien mas guardo en medio y esta decision se pierde. No
     * se reintenta desde aqui, porque quien reintente tiene que volver a decidir con lo
     * que hay ahora, y eso no lo sabe un repositorio.
     */
    private long guardarPublicacion(Listing publicacion) {
        long versionLeida = publicacion.version();
        Product producto = publicacion.product();

        if (esNueva(publicacion)) {
            jdbc.sql("""
                            INSERT INTO listings (
                                id, product_id, status, published_at, sold_at,
                                moderated_by, moderated_at, rejection_reason, rejection_note,
                                attention_reasons, version, created_at, updated_at)
                            VALUES (
                                :id, :producto, :estado, :publicado, NULL,
                                :moderador, :moderado, :motivo, :nota,
                                :marcas, 0, :creado, :actualizado)
                            """)
                    .param("id", publicacion.id().value())
                    .param("producto", producto.id().value())
                    .param("estado", publicacion.status().name())
                    .param("publicado", marca(publicacion.publishedAt()))
                    .param(
                            "moderador",
                            publicacion.moderatedBy() == null
                                    ? null
                                    : publicacion.moderatedBy().value())
                    .param("moderado", marca(publicacion.moderatedAt()))
                    .param(
                            "motivo",
                            publicacion.rejectionReason() == null
                                    ? null
                                    : publicacion.rejectionReason().name())
                    .param("nota", publicacion.rejectionNote())
                    .param("marcas", marcasDe(publicacion))
                    .param("creado", Timestamp.from(publicacion.createdAt()))
                    .param("actualizado", Timestamp.from(publicacion.updatedAt()))
                    .update();
            return 0L;
        }

        int filas = jdbc.sql("""
                        UPDATE listings SET
                            status             = :estado,
                            published_at       = :publicado,
                            moderated_by       = :moderador,
                            moderated_at       = :moderado,
                            rejection_reason   = :motivo,
                            rejection_note     = :nota,
                            attention_reasons  = :marcas,
                            version            = version + 1,
                            updated_at         = :actualizado
                        WHERE id = :id AND version = :version
                        """)
                .param("id", publicacion.id().value())
                .param("version", versionLeida)
                .param("estado", publicacion.status().name())
                .param("publicado", marca(publicacion.publishedAt()))
                .param(
                        "moderador",
                        publicacion.moderatedBy() == null
                                ? null
                                : publicacion.moderatedBy().value())
                .param("moderado", marca(publicacion.moderatedAt()))
                .param(
                        "motivo",
                        publicacion.rejectionReason() == null
                                ? null
                                : publicacion.rejectionReason().name())
                .param("nota", publicacion.rejectionNote())
                .param("marcas", marcasDe(publicacion))
                .param("actualizado", Timestamp.from(publicacion.updatedAt()))
                .update();

        if (filas == 0) {
            throw new ListingConcurrentlyModifiedException(publicacion.id());
        }
        return versionLeida + 1;
    }

    /**
     * Nueva es la que todavia no tiene fila, no la que tiene version cero.
     *
     * <p>Se pregunta a la base y no al agregado: una publicacion recien creada y una
     * guardada una sola vez tienen las dos version cero, y confundirlas convertiria un
     * INSERT en un UPDATE que no encuentra nada, o al reves.
     */
    private boolean esNueva(Listing publicacion) {
        return jdbc.sql("SELECT count(*) FROM listings WHERE id = :id")
                        .param("id", publicacion.id().value())
                        .query(Long.class)
                        .single()
                == 0L;
    }

    private void reescribirImagenes(Listing publicacion) {
        jdbc.sql("DELETE FROM product_images WHERE product_id = :producto")
                .param("producto", publicacion.product().id().value())
                .update();

        for (ProductImage imagen : publicacion.images()) {
            jdbc.sql("""
                            INSERT INTO product_images (
                                id, product_id, kind, object_key, position, angle_degrees,
                                is_canonical, width, height, bytes, content_type)
                            VALUES (
                                :id, :producto, :clase, :clave, :posicion, :angulo,
                                :canonica, :ancho, :alto, :bytes, :tipo)
                            """)
                    .param("id", imagen.id().value())
                    .param("producto", publicacion.product().id().value())
                    .param("clase", imagen.kind().name())
                    .param("clave", imagen.objectKey().value())
                    .param("posicion", imagen.position())
                    .param("angulo", imagen.angleDegrees())
                    .param("canonica", imagen.esCanonica())
                    .param("ancho", imagen.dimensions().width())
                    .param("alto", imagen.dimensions().height())
                    .param("bytes", imagen.bytes())
                    .param("tipo", imagen.contentType().mediaType())
                    .update();
        }
    }

    // ------------------------------------------------------------------ lectura

    private Listing conImagenes(Listing publicacion) {
        List<ProductImage> imagenes = jdbc.sql("""
                        SELECT id, kind, object_key, position, angle_degrees, width, height, bytes, content_type
                        FROM product_images
                        WHERE product_id = :producto
                        ORDER BY kind, position
                        """)
                .param("producto", publicacion.product().id().value())
                .query(JdbcListingRepository::filaAImagen)
                .list();

        return Listing.existente(
                publicacion.id(),
                publicacion.product(),
                publicacion.status(),
                imagenes,
                publicacion.publishedAt(),
                publicacion.moderatedBy(),
                publicacion.moderatedAt(),
                publicacion.rejectionReason(),
                publicacion.rejectionNote(),
                publicacion.attentionReasons(),
                publicacion.version(),
                publicacion.createdAt(),
                publicacion.updatedAt());
    }

    private static Listing filaAPublicacion(ResultSet fila, int numero) throws SQLException {
        Product producto = filaAProducto(fila);

        String motivoRechazo = fila.getString("rejection_reason");
        UUID moderador = fila.getObject("moderated_by", UUID.class);

        return Listing.existente(
                new ListingId(fila.getObject("id", UUID.class)),
                producto,
                ListingStatus.valueOf(fila.getString("status")),
                List.of(),
                instante(fila.getTimestamp("published_at")),
                moderador == null ? null : new ModeratorId(moderador),
                instante(fila.getTimestamp("moderated_at")),
                motivoRechazo == null ? null : ListingRejectionReason.valueOf(motivoRechazo),
                fila.getString("rejection_note"),
                marcas(fila.getArray("attention_reasons")),
                fila.getLong("version"),
                instante(fila.getTimestamp("created_at")),
                instante(fila.getTimestamp("updated_at")));
    }

    private static Product filaAProducto(ResultSet fila) throws SQLException {
        String marca = fila.getString("brand");
        Object sellado = fila.getObject("is_sealed");
        Object garantia = fila.getObject("manufacturer_warranty_months");

        return new Product(
                new ProductId(fila.getObject("product_id", UUID.class)),
                new SellerId(fila.getObject("seller_id", UUID.class)),
                new CategoryId(fila.getObject("category_id", UUID.class)),
                new Title(fila.getString("title")),
                new Description(fila.getString("description")),
                marca == null ? null : new Brand(marca),
                Condition.valueOf(fila.getString("condition")),
                new Size(SizeSystem.valueOf(fila.getString("size_system")), fila.getString("size_value")),
                MeasurementsJson.deJson(fila.getString("measurements")),
                Color.valueOf(fila.getString("color")),
                Money.dePesos(fila.getLong("price")),
                new ShippingDimensions(
                        fila.getInt("weight_grams"),
                        fila.getBigDecimal("length_cm"),
                        fila.getBigDecimal("width_cm"),
                        fila.getBigDecimal("height_cm")),
                sellado == null ? null : (Boolean) sellado,
                garantia == null ? null : new WarrantyMonths(((Number) garantia).intValue()));
    }

    private static ProductImage filaAImagen(ResultSet fila, int numero) throws SQLException {
        Object angulo = fila.getObject("angle_degrees");

        return new ProductImage(
                new ProductImageId(fila.getObject("id", UUID.class)),
                ImageKind.valueOf(fila.getString("kind")),
                new FileKey(fila.getString("object_key")),
                fila.getInt("position"),
                angulo == null ? null : ((Number) angulo).intValue(),
                new ImageDimensions(fila.getInt("width"), fila.getInt("height")),
                fila.getLong("bytes"),
                ImageContentType.porMediaType(fila.getString("content_type")));
    }

    /** Los nombres del enum, como arreglo de texto para la columna. */
    private static String[] marcasDe(Listing publicacion) {
        return publicacion.attentionReasons().stream().map(Enum::name).toArray(String[]::new);
    }

    private static Set<AttentionReason> marcas(@Nullable Array columna) throws SQLException {
        if (columna == null) {
            return Set.of();
        }
        return Arrays.stream((String[]) columna.getArray())
                .map(AttentionReason::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AttentionReason.class)));
    }

    private static @Nullable Timestamp marca(@Nullable Instant momento) {
        return momento == null ? null : Timestamp.from(momento);
    }

    private static @Nullable Instant instante(@Nullable Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }
}
