package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.catalog.exception.ListingConcurrentlyModifiedException;
import co.sastra.catalog.model.AttentionReason;
import co.sastra.catalog.model.Brand;
import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.CategoryId;
import co.sastra.catalog.model.Color;
import co.sastra.catalog.model.Condition;
import co.sastra.catalog.model.Description;
import co.sastra.catalog.model.ImageKind;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ListingRejectionReason;
import co.sastra.catalog.model.ListingStatus;
import co.sastra.catalog.model.MeasurementGroup;
import co.sastra.catalog.model.MeasurementKind;
import co.sastra.catalog.model.Measurements;
import co.sastra.catalog.model.ModerationAction;
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
import co.sastra.catalog.port.out.Categories;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.catalog.port.out.ModerationLog;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImageDimensions;
import co.sastra.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Los adaptadores de persistencia del catalogo, contra PostgreSQL 17 real. HU-007.
 *
 * <p>Lo que se prueba aqui y no se puede probar en otro sitio: que el agregado sale de
 * la base tal como entro —incluidas las medidas, que viajan en {@code jsonb}—, que las
 * imagenes se reescriben sin duplicarse, y que el bloqueo optimista del criterio 34
 * hace lo que dice cuando dos escrituras chocan de verdad.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CatalogPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T15:00:00Z");

    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final ModerationLog bitacora;
    private final JdbcClient jdbc;

    CatalogPersistenceTest(
            ListingRepository publicaciones, Categories categorias, ModerationLog bitacora, JdbcClient jdbc) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.bitacora = bitacora;
        this.jdbc = jdbc;
    }

    @Test
    void deberia_leer_del_arbol_sembrado_una_categoria_de_moda() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        assertThat(camisas.allowsUsed()).isTrue();
        assertThat(camisas.measurementGroup()).isEqualTo(MeasurementGroup.TOP);
        assertThat(camisas.sizeSystems()).contains(SizeSystem.ALPHA);
        assertThat(camisas.admitePublicaciones()).isTrue();
    }

    @Test
    void deberia_leer_la_tecnologia_sin_permitir_lo_usado_RN_064() {
        Category celulares = categoriaPorSlug("celulares-y-tabletas");

        assertThat(celulares.allowsUsed()).isFalse();
        assertThat(celulares.condicionesAdmisibles()).containsExactly(Condition.NEW);
        assertThat(celulares.measurementGroup()).isEqualTo(MeasurementGroup.DEVICE);
    }

    @Test
    void deberia_leer_los_dos_sistemas_de_talla_de_los_jeans() {
        Category jeans = categoriaPorSlug("jeans");

        assertThat(jeans.sizeSystems()).containsExactlyInAnyOrder(SizeSystem.WAIST_INCHES, SizeSystem.NUMERIC_CO);
    }

    // Ida y vuelta completa: si algo se pierde en el mapeo, se pierde aqui.
    @Test
    void deberia_devolver_el_agregado_tal_como_entro() {
        Listing guardada = publicaciones.guardar(borradorConTomas());

        Listing leida = publicaciones.buscar(guardada.id()).orElseThrow();

        assertThat(leida.status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(leida.sellerId()).isEqualTo(guardada.sellerId());
        assertThat(leida.product().title()).isEqualTo(guardada.product().title());
        assertThat(leida.product().price()).isEqualTo(Money.dePesos(185_000));
        assertThat(leida.tomasDelVendedor()).hasSize(8);
    }

    // Las medidas viajan en jsonb. Un double por el camino las estropearia.
    @Test
    void deberia_conservar_las_medidas_con_su_decimal_exacto_RN_021() {
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        valores.put(MeasurementKind.CHEST, new BigDecimal("52.5"));
        valores.put(MeasurementKind.LENGTH, new BigDecimal("70.0"));
        valores.put(MeasurementKind.SHOULDERS, new BigDecimal("41"));
        valores.put(MeasurementKind.SLEEVE, new BigDecimal("60.5"));

        Listing guardada = publicaciones.guardar(borradorDe(new Measurements(valores)));

        Measurements leidas =
                publicaciones.buscar(guardada.id()).orElseThrow().product().measurements();

        assertThat(leidas.valores().get(MeasurementKind.CHEST)).isEqualByComparingTo("52.5");
        assertThat(leidas.valores().get(MeasurementKind.SLEEVE)).isEqualByComparingTo("60.5");
        assertThat(leidas.valores()).hasSize(4);
    }

    @Test
    void deberia_reescribir_las_imagenes_sin_duplicarlas() {
        Listing conOcho = publicaciones.guardar(borradorConTomas());

        Listing conSieteYUnaNueva =
                conOcho.sinImagen(conOcho.images().getFirst().id(), AHORA);
        publicaciones.guardar(conSieteYUnaNueva);

        Listing leida = publicaciones.buscar(conOcho.id()).orElseThrow();
        assertThat(leida.tomasDelVendedor()).hasSize(7);

        long filas = jdbc.sql("SELECT count(*) FROM product_images WHERE product_id = :p")
                .param("p", conOcho.product().id().value())
                .query(Long.class)
                .single();
        assertThat(filas).isEqualTo(7);
    }

    @Test
    void deberia_guardar_una_imagen_de_referencia_junto_a_las_tomas_RN_066() {
        Listing sellado = publicaciones.guardar(selladoConCuatroTomasYReferencia());

        Listing leida = publicaciones.buscar(sellado.id()).orElseThrow();

        assertThat(leida.tomasDelVendedor()).hasSize(4);
        assertThat(leida.imagenesDeReferencia()).hasSize(1);
        assertThat(leida.imagenesDeReferencia().getFirst().kind()).isEqualTo(ImageKind.REFERENCE);
        assertThat(leida.imagenesDeReferencia().getFirst().angleDegrees()).isNull();
    }

    // Criterio 34: el vendedor edita mientras el moderador decide.
    @Test
    void deberia_rechazar_la_segunda_escritura_concurrente_criterio_34() {
        Listing guardada = publicaciones.guardar(borradorConTomas());

        Listing copiaDelVendedor = publicaciones.buscar(guardada.id()).orElseThrow();
        Listing copiaDelModerador = publicaciones.buscar(guardada.id()).orElseThrow();
        assertThat(copiaDelVendedor.version()).isEqualTo(copiaDelModerador.version());

        publicaciones.guardar(copiaDelVendedor.enviarARevision(AHORA));

        assertThatThrownBy(() -> publicaciones.guardar(copiaDelModerador.archivar(AHORA)))
                .isInstanceOf(ListingConcurrentlyModifiedException.class);

        // Y lo que de verdad importa: la decision del primero sigue en pie.
        assertThat(publicaciones.buscar(guardada.id()).orElseThrow().status()).isEqualTo(ListingStatus.PENDING_REVIEW);
    }

    // El escenario que destapo la revision de seguridad: el moderador retira una replica
    // y el vendedor, con la ficha abierta, cambia el precio y deshace la retirada.
    @Test
    void deberia_impedir_que_un_cambio_de_precio_deshaga_una_retirada_criterio_34() {
        Listing publicada = publicada();
        Listing copiaDelVendedor = publicaciones.buscar(publicada.id()).orElseThrow();

        publicaciones.guardar(publicaciones.buscar(publicada.id()).orElseThrow().archivar(AHORA));

        assertThatThrownBy(() -> publicaciones.guardar(copiaDelVendedor.cambiarPrecio(Money.dePesos(120_000), AHORA)))
                .isInstanceOf(ListingConcurrentlyModifiedException.class);

        assertThat(publicaciones.buscar(publicada.id()).orElseThrow().status()).isEqualTo(ListingStatus.ARCHIVED);
    }

    @Test
    void deberia_subir_la_version_en_cada_guardado() {
        Listing guardada = publicaciones.guardar(borradorConTomas());
        assertThat(guardada.version()).isZero();

        Listing enRevision = publicaciones.guardar(guardada.enviarARevision(AHORA));

        assertThat(enRevision.version()).isEqualTo(1L);
        assertThat(publicaciones.buscar(guardada.id()).orElseThrow().version()).isEqualTo(1L);
    }

    // V10: las dos marcas conviven en la columna de arreglo.
    @Test
    void deberia_guardar_las_dos_marcas_de_atencion_criterios_12_y_18() {
        Listing marcada = publicaciones.guardar(borradorDe(medidasDe(MeasurementGroup.TOP), Money.dePesos(9_000))
                .marcarCargaDesdeGaleria(AHORA));

        Listing leida = publicaciones.buscar(marcada.id()).orElseThrow();

        assertThat(leida.attentionReasons())
                .containsExactlyInAnyOrder(AttentionReason.PRICE_OUT_OF_RANGE, AttentionReason.GALLERY_UPLOAD);
    }

    @Test
    void deberia_conservar_motivo_y_nota_de_un_rechazo_RN_022() {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        publicaciones.guardar(
                enRevision.rechazar(moderador, ListingRejectionReason.PHOTOS_UNUSABLE, "Frontal borrosa", AHORA));

        Listing leida = publicaciones.buscar(enRevision.id()).orElseThrow();
        assertThat(leida.rejectionReason()).isEqualTo(ListingRejectionReason.PHOTOS_UNUSABLE);
        assertThat(leida.rejectionNote()).isEqualTo("Frontal borrosa");
        assertThat(leida.moderatedBy()).isEqualTo(moderador);
    }

    @Test
    void deberia_devolver_solo_las_del_vendedor_que_pregunta() {
        Listing mia = publicaciones.guardar(borradorConTomas());
        publicaciones.guardar(borradorConTomas());

        var suyas = publicaciones.buscarDelVendedor(mia.sellerId(), 0, 20);

        assertThat(suyas).hasSize(1);
        assertThat(suyas.getFirst().id()).isEqualTo(mia.id());
    }

    // RN-045: el rastro se acumula y no se sobrescribe.
    @Test
    void deberia_acumular_las_entradas_de_la_bitacora_RN_045() {
        Listing publicacion = publicaciones.guardar(borradorConTomas());
        ModeratorId moderador = new ModeratorId(nuevoUsuario());

        bitacora.registrar(publicacion.id(), moderador, ModerationAction.APPROVED, null, null);
        bitacora.registrar(publicacion.id(), moderador, ModerationAction.ARCHIVED, "PROHIBITED_ITEM", "replica");

        long entradas = jdbc.sql("SELECT count(*) FROM moderation_events WHERE listing_id = :l")
                .param("l", publicacion.id().value())
                .query(Long.class)
                .single();

        assertThat(entradas).isEqualTo(2);
    }

    // ------------------------------------------------------------------ apoyo

    private Category categoriaPorSlug(String slug) {
        UUID id = jdbc.sql("SELECT id FROM categories WHERE slug = :s")
                .param("s", slug)
                .query(UUID.class)
                .single();

        return categorias.buscar(new CategoryId(id)).orElseThrow();
    }

    /** Una cuenta real: products.seller_id apunta a users. */
    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Vendedor de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    private Listing borradorConTomas() {
        return conTomas(borradorDe(medidasDe(MeasurementGroup.TOP)), 8);
    }

    private Listing publicada() {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), AHORA));
    }

    private Listing borradorDe(Measurements medidas) {
        return borradorDe(medidas, Money.dePesos(185_000));
    }

    private Listing borradorDe(Measurements medidas, Money precio) {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                camisas,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                new Brand("Zara"),
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                medidas,
                Color.BEIGE,
                precio,
                envio(),
                null,
                null);

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    private Listing selladoConCuatroTomasYReferencia() {
        Category celulares = categoriaPorSlug("celulares-y-tabletas");

        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                celulares,
                new Title("Telefono de gama media, 128 GB"),
                new Description("Nuevo, sellado."),
                new Brand("Motorola"),
                Condition.NEW,
                Size.unica(),
                medidasDe(MeasurementGroup.DEVICE),
                Color.BLACK,
                Money.dePesos(900_000),
                envio(),
                true,
                null);

        Listing sellado = conTomas(Listing.crearBorrador(ListingId.nuevo(), producto, AHORA), 4);
        return sellado.conImagen(
                ProductImage.referencia(
                        ProductImageId.nuevo(),
                        clave(),
                        0,
                        new ImageDimensions(900, 1200),
                        120_000L,
                        ImageContentType.JPEG),
                AHORA);
    }

    /** Ocho tomas van seguidas; cuatro van de dos en dos, que son las canonicas. */
    private static Listing conTomas(Listing publicacion, int cuantas) {
        int paso = ProductImage.TOMAS_DE_LA_SECUENCIA / cuantas;

        Listing resultado = publicacion;
        for (int i = 0; i < cuantas; i++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            clave(),
                            i * paso,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    private static FileKey clave() {
        return new FileKey("productos/" + UUID.randomUUID() + ".jpg");
    }

    private static Measurements medidasDe(MeasurementGroup grupo) {
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        grupo.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));
        return new Measurements(valores);
    }

    private static ShippingDimensions envio() {
        return new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0"));
    }
}
