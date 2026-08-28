package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.exception.ListingConcurrentlyModifiedException;
import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /**
     * El borrador recien nacido, con la categoria y nada mas. Criterio 5.
     *
     * <p>Es como empieza toda publicacion: el formulario pide la categoria, pulsa
     * «Empezar» y manda {@code {"categoryId": ...}} sin un solo campo mas. Ninguna
     * prueba lo cubria —todas creaban el producto completo— y contra PostgreSQL de
     * verdad no funcionaba: doce columnas {@code NOT NULL} en V9 y un repositorio que
     * desreferenciaba lo anulable sin guarda. La pantalla de publicar respondia 500 en
     * su primera peticion.
     */
    @Test
    void deberia_guardar_y_releer_un_borrador_con_solo_la_categoria_criterio_5() {
        Listing vacio = borradorVacio();

        Listing guardada = publicaciones.guardar(vacio);
        Listing leida = publicaciones.buscar(guardada.id()).orElseThrow();

        assertThat(leida.status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(leida.product().categoryId()).isEqualTo(vacio.product().categoryId());
        assertThat(leida.product().title()).isNull();
        assertThat(leida.product().description()).isNull();
        assertThat(leida.product().brand()).isNull();
        assertThat(leida.product().condition()).isNull();
        assertThat(leida.product().size()).isNull();
        assertThat(leida.product().color()).isNull();
        assertThat(leida.product().price()).isNull();
        assertThat(leida.product().shipping()).isNull();
        assertThat(leida.product().measurements().valores()).isEmpty();
        assertThat(leida.images()).isEmpty();
    }

    /**
     * «Salir a la mitad y volver retoma donde iba», que es la otra frase del criterio 5.
     *
     * <p>Va aparte de la anterior porque ejercita la otra rama del guardado: la fila ya
     * existe, asi que entra por el {@code ON CONFLICT DO UPDATE}, y lo que se comprueba
     * es que las columnas pasan de nulo a valor sin dejar nada por el camino.
     */
    @Test
    void deberia_completar_despues_el_borrador_que_nacio_vacio_criterio_5() {
        Listing vacio = publicaciones.guardar(borradorVacio());

        Listing completado = publicaciones.guardar(vacio.editarContenido(
                Product.crear(
                        vacio.product().id(),
                        vacio.product().sellerId(),
                        categoriaPorSlug("camisas-y-blusas"),
                        new Title("Camisa de lino color hueso"),
                        new Description("Usada dos veces."),
                        new Brand("Zara"),
                        Condition.LIKE_NEW,
                        new Size(SizeSystem.ALPHA, "M"),
                        medidasDe(MeasurementGroup.TOP),
                        Color.BEIGE,
                        Money.dePesos(185_000),
                        envio(),
                        null,
                        null),
                AHORA));

        Product leido = publicaciones.buscar(completado.id()).orElseThrow().product();

        assertThat(leido.title()).isEqualTo(new Title("Camisa de lino color hueso"));
        assertThat(leido.condition()).isEqualTo(Condition.LIKE_NEW);
        assertThat(leido.size()).isEqualTo(new Size(SizeSystem.ALPHA, "M"));
        assertThat(leido.color()).isEqualTo(Color.BEIGE);
        assertThat(leido.price()).isEqualTo(Money.dePesos(185_000));
        assertThat(leido.shipping()).isEqualTo(envio());
        assertThat(leido.measurements().valores()).isNotEmpty();
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

    // V12: la cola del moderador, contra el indice parcial y la base de verdad.
    @Test
    void deberia_devolver_en_la_cola_solo_lo_que_espera_revision_HU_008() {
        Listing esperando = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        publicaciones.guardar(borradorConTomas());
        publicada();

        var cola = publicaciones.pendientesDeRevision(0, 50);

        assertThat(cola)
                .as("la cola no puede traer nada que no este esperando")
                .allSatisfy(publicacion -> assertThat(publicacion.status()).isEqualTo(ListingStatus.PENDING_REVIEW));
        assertThat(cola).extracting(Listing::id).contains(esperando.id());
    }

    @Test
    void deberia_ordenar_la_cola_por_lo_que_lleva_mas_tiempo_esperando_HU_008() {
        Instant temprano = AHORA.minus(Duration.ofHours(6));
        Instant mediodia = AHORA.minus(Duration.ofHours(2));

        Listing tercera = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        Listing primera = publicaciones.guardar(borradorConTomas().enviarARevision(temprano));
        Listing segunda = publicaciones.guardar(borradorConTomas().enviarARevision(mediodia));

        var cola = publicaciones.pendientesDeRevision(0, 50);

        assertThat(cola).extracting(Listing::id).containsSubsequence(primera.id(), segunda.id(), tercera.id());
    }

    /** La razon de que exista la columna: con `updated_at`, esta prueba fallaria. */
    @Test
    void no_deberia_perder_el_turno_por_cambiar_el_precio_mientras_espera_HU_008() {
        Instant temprano = AHORA.minus(Duration.ofHours(6));

        Listing primera = publicaciones.guardar(borradorConTomas().enviarARevision(temprano));
        Listing segunda = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));

        publicaciones.guardar(publicaciones
                .buscar(primera.id())
                .orElseThrow()
                .cambiarPrecio(Money.dePesos(190_000), AHORA.plus(Duration.ofHours(1))));

        var cola = publicaciones.pendientesDeRevision(0, 50);

        assertThat(cola).extracting(Listing::id).containsSubsequence(primera.id(), segunda.id());
        assertThat(cola)
                .filteredOn(publicacion -> publicacion.id().equals(primera.id()))
                .singleElement()
                .satisfies(publicacion -> assertThat(publicacion.submittedAt()).isEqualTo(temprano));
    }

    /**
     * La cola trae la frontal y nada mas.
     *
     * <p>Antes traia las ocho de cada fila —una consulta por publicacion— para pintar una
     * miniatura. Esta prueba fija las dos mitades: que la frontal esta, y que las otras
     * siete no viajan.
     */
    @Test
    void deberia_traer_solo_la_toma_frontal_en_la_cola_HU_008() {
        Listing esperando = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));

        Listing enLaCola = publicaciones.pendientesDeRevision(0, 50).stream()
                .filter(p -> p.id().equals(esperando.id()))
                .findFirst()
                .orElseThrow();

        assertThat(enLaCola.images()).hasSize(1);
        assertThat(enLaCola.images().getFirst().position()).isZero();
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

    // --- El catalogo publico. HU-009 -----------------------------------------

    /**
     * RN-068 contra la base de verdad.
     *
     * <p>Se siembran los siete estados y se comprueba que sale uno. Es la prueba que el
     * criterio 2 pide y la unica forma de demostrarlo: en memoria el filtro es una linea
     * de Java, aqui es la clausula que de verdad corre.
     */
    @Test
    void deberia_traer_del_catalogo_solo_lo_publicado_RN_068() {
        Listing visible = publicada();
        Listing borrador = publicaciones.guardar(borradorConTomas());
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        Listing pausada = publicaciones.guardar(publicada().pausar(AHORA));

        List<ListingId> catalogo = publicaciones.publicadas(List.of(), null, 50).stream()
                .map(Listing::id)
                .toList();

        // Por contencion y no por igualdad: esta clase comparte la base entre pruebas y
        // varias dejan publicaciones vivas. Lo que se afirma es lo que esta prueba
        // sembro, que es de lo unico que puede responder.
        assertThat(catalogo).contains(visible.id());
        assertThat(catalogo).doesNotContain(borrador.id(), enRevision.id(), pausada.id());
    }

    /** Lo mas reciente primero, que es lo que el indice de V14 sostiene. */
    @Test
    void deberia_ordenar_el_catalogo_por_fecha_de_publicacion_descendente() {
        Listing vieja = publicadaEn(AHORA.minus(Duration.ofHours(2)));
        Listing nueva = publicadaEn(AHORA);

        List<ListingId> mias = publicaciones.publicadas(List.of(), null, 50).stream()
                .map(Listing::id)
                .filter(id -> id.equals(nueva.id()) || id.equals(vieja.id()))
                .toList();

        // El orden relativo entre las dos, que es lo que la consulta decide. El absoluto
        // depende de lo que hayan dejado las demas pruebas.
        assertThat(mias).containsExactly(nueva.id(), vieja.id());
    }

    /**
     * El cursor sobre la pareja, no sobre la fecha.
     *
     * <p>Las tres se publican en el <strong>mismo instante</strong>, que es lo que pasa con
     * un reloj fijo y lo que puede pasar en produccion. Con un cursor que solo mirara
     * `published_at`, el segundo tramo se saltaria dos o repetiria las tres para siempre.
     * Esta prueba es la razon de que la consulta use `(published_at, id) < (:fecha, :id)`.
     */
    @Test
    void deberia_recorrer_el_catalogo_sin_repetir_ni_perder_publicadas_en_el_mismo_instante() {
        // Las tres del mismo vendedor, y se recorre su escaparate: asi el tramo es solo lo
        // que esta prueba sembro. La clausula del cursor es la misma en las dos consultas
        // -la escribe `condicionDelCursor`-, asi que recorrer una demuestra la otra.
        SellerId vendedor = new SellerId(nuevoUsuario());
        publicadaDe(vendedor, AHORA);
        publicadaDe(vendedor, AHORA);
        publicadaDe(vendedor, AHORA);

        List<Listing> primera = publicaciones.publicadasDelVendedor(vendedor, null, 2);
        CatalogCursor desde = new CatalogCursor(
                Objects.requireNonNull(primera.getLast().publishedAt()),
                primera.getLast().id());
        List<Listing> segunda = publicaciones.publicadasDelVendedor(vendedor, desde, 2);

        assertThat(primera).hasSize(2);
        assertThat(segunda).hasSize(1);
        assertThat(primera)
                .extracting(Listing::id)
                .doesNotContainAnyElementsOf(segunda.stream().map(Listing::id).toList());
    }

    /** Criterio 8: el filtro de categoria es el de la publicacion, no el de su familia. */
    @Test
    void deberia_filtrar_el_catalogo_por_categoria() {
        Listing camisa = publicada();
        Category jeans = categoriaPorSlug("jeans");

        List<Listing> soloCamisas =
                publicaciones.publicadas(List.of(camisa.product().categoryId()), null, 50);

        assertThat(soloCamisas).extracting(Listing::id).contains(camisa.id());
        assertThat(soloCamisas)
                .allSatisfy(publicacion -> assertThat(publicacion.product().categoryId())
                        .isEqualTo(camisa.product().categoryId()));

        // Ninguna prueba de esta clase publica en jeans, asi que el filtro se puede
        // afirmar por el lado vacio tambien.
        assertThat(publicaciones.publicadas(List.of(jeans.id()), null, 24)).isEmpty();
    }

    /** El escaparate de un vendedor: lo suyo y publicado, no sus borradores. */
    @Test
    void deberia_traer_del_escaparate_solo_lo_publicado_del_vendedor() {
        Listing visible = publicada();
        SellerId suyo = visible.sellerId();
        publicaciones.guardar(borradorConTomas());

        List<Listing> escaparate = publicaciones.publicadasDelVendedor(suyo, null, 24);

        // Aqui si es igualdad: el vendedor es de esta prueba y no tiene nada mas.
        assertThat(escaparate).extracting(Listing::id).containsExactly(visible.id());
    }

    /** Criterio 10: una familia son sus hojas, y las de una familia retirada no cuentan. */
    @Test
    void deberia_resolver_una_familia_en_sus_hojas_publicables() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");
        Category tops =
                categorias.buscar(Objects.requireNonNull(camisas.parentId())).orElseThrow();

        List<CategoryId> hojas = categorias.publicablesBajo(tops.id());

        assertThat(hojas).contains(camisas.id()).doesNotContain(tops.id());
        assertThat(categorias.publicablesBajo(camisas.id())).containsExactly(camisas.id());
    }

    /** Criterio 9: retirada del arbol no es lo mismo que vacia. */
    @Test
    void no_deberia_resolver_una_categoria_retirada_criterio_9() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");
        retirar("camisas-y-blusas");
        try {
            assertThat(categorias.publicablesBajo(camisas.id())).isEmpty();
        } finally {
            devolver("camisas-y-blusas");
        }
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * El arbol completo, contra lo que sembraron V9 y V11.
     *
     * <p>Es la unica prueba que ve los nombres visibles: no estan en el modelo de dominio,
     * asi que ninguna prueba de dominio puede mirarlos. Y son texto que lee un comprador.
     */
    @Test
    void deberia_armar_el_arbol_activo_con_sus_nombres() {
        List<CategoryView> arbol = categorias.arbolActivo();

        assertThat(arbol).hasSize(6).allSatisfy(familia -> {
            assertThat(familia.esFamilia()).isTrue();
            assertThat(familia.hijas()).isNotEmpty();
        });

        assertThat(arbol.stream().flatMap(familia -> familia.hijas().stream())).hasSize(31);
    }

    /** V11: los nombres se sembraron sin tildes y eso lo lee un comprador. */
    @Test
    void deberia_traer_los_nombres_del_espanol_bien_escritos() {
        List<String> nombres = categorias.arbolActivo().stream()
                .flatMap(familia -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(familia.nombreEs()),
                        familia.hijas().stream().map(CategoryView::nombreEs)))
                .toList();

        assertThat(nombres)
                .contains("Tecnología", "Suéteres, buzos y sacos", "Trajes de baño", "Cámaras")
                .doesNotContain("Tecnologia", "Sueteres, buzos y sacos", "Trajes de bano", "Camaras");
    }

    /** RN-064: las siete de tecnologia no admiten lo usado, y el formulario lo necesita. */
    @Test
    void deberia_decir_que_la_tecnologia_no_admite_lo_usado_RN_064() {
        CategoryView tecnologia = categorias.arbolActivo().stream()
                .filter(familia -> "tech".equals(familia.slug()))
                .findFirst()
                .orElseThrow();

        assertThat(tecnologia.hijas()).hasSize(7).allSatisfy(hija -> {
            assertThat(hija.admiteUsado()).isFalse();
            assertThat(hija.medidasObligatorias()).isNotEmpty();
        });
    }

    /**
     * Una categoria retirada no se ofrece, y la publicacion que ya la tenia la conserva.
     *
     * <p>Es el caso borde de la historia, y hasta ahora el {@code WHERE active} se podia
     * quitar sin que ninguna prueba se enterara: las tres leian el arbol sembrado, donde
     * todo esta activo.
     */
    @Test
    void no_deberia_ofrecer_una_categoria_retirada() {
        Category gafas = categoriaPorSlug("gafas");
        retirar("gafas");

        try {
            List<String> hojas = categorias.arbolActivo().stream()
                    .flatMap(familia -> familia.hijas().stream())
                    .map(CategoryView::slug)
                    .toList();

            assertThat(hojas).doesNotContain("gafas").hasSize(30);
            // Y sigue existiendo para quien ya la tenia: no se borra, se retira.
            assertThat(categorias.buscar(gafas.id())).isPresent();
        } finally {
            devolver("gafas");
        }
    }

    /** Retirada la familia, sus hojas no cuelgan de la nada: desaparecen con ella. */
    @Test
    void no_deberia_ofrecer_las_hojas_de_una_familia_retirada() {
        retirar("tech");

        try {
            List<CategoryView> arbol = categorias.arbolActivo();

            assertThat(arbol).hasSize(5).noneMatch(familia -> "tech".equals(familia.slug()));
            assertThat(arbol.stream().flatMap(familia -> familia.hijas().stream()))
                    .hasSize(24)
                    .noneMatch(hoja -> "celulares-y-tabletas".equals(hoja.slug()));
        } finally {
            devolver("tech");
        }
    }

    /**
     * El orden sale de la columna {@code position}.
     *
     * <p>Su motivo declarado es que el desplegable no cambie de orden entre peticiones, y
     * eso no lo comprueba contar cuantas hay.
     */
    @Test
    void deberia_devolver_las_familias_en_el_orden_sembrado() {
        List<String> familias =
                categorias.arbolActivo().stream().map(CategoryView::slug).toList();

        assertThat(familias).containsExactly("tops", "bottoms", "full-body", "footwear", "accessories", "tech");
    }

    /** Retira una categoria o una familia, como haria una migracion futura. */
    private void retirar(String slug) {
        cambiarActiva(slug, false);
    }

    /**
     * Devuelve al arbol lo que la prueba retiro.
     *
     * <p>Esta clase comparte la base entre pruebas: sin deshacerlo, las que cuentan el
     * arbol entero empiezan a fallar por culpa de esta.
     */
    private void devolver(String slug) {
        cambiarActiva(slug, true);
    }

    private void cambiarActiva(String slug, boolean activa) {
        jdbc.sql("UPDATE categories SET active = :activa WHERE slug = :slug")
                .param("activa", activa)
                .param("slug", slug)
                .update();
    }

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

    /** Publicada en un instante concreto, para las pruebas de orden y de cursor. */
    private Listing publicadaEn(Instant cuando) {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(cuando));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), cuando));
    }

    /** Publicada por un vendedor concreto, para aislar un escaparate de las demas pruebas. */
    private Listing publicadaDe(SellerId vendedor, Instant cuando) {
        Listing borrador = conTomas(
                Listing.crearBorrador(ListingId.nuevo(), productoDe(vendedor, medidasDe(MeasurementGroup.TOP)), AHORA),
                8);

        Listing enRevision = publicaciones.guardar(borrador.enviarARevision(cuando));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), cuando));
    }

    private Product productoDe(SellerId vendedor, Measurements medidas) {
        return Product.crear(
                ProductId.nuevo(),
                vendedor,
                categoriaPorSlug("camisas-y-blusas"),
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                new Brand("Zara"),
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                medidas,
                Color.BEIGE,
                Money.dePesos(185_000),
                envio(),
                null,
                null);
    }

    /** Lo que crea «Empezar»: la categoria y nada mas. Criterio 5. */
    private Listing borradorVacio() {
        Product producto = Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                categoriaPorSlug("camisas-y-blusas"),
                null,
                null,
                null,
                null,
                null,
                Measurements.vacias(),
                null,
                null,
                null,
                null,
                null);

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
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
