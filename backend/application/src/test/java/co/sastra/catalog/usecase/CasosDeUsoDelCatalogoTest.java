package co.sastra.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.catalog.dto.ApproveListingCommand;
import co.sastra.catalog.dto.ChangeListingPriceCommand;
import co.sastra.catalog.dto.CreateListingCommand;
import co.sastra.catalog.dto.ListSellerListingsQuery;
import co.sastra.catalog.dto.ProductData;
import co.sastra.catalog.dto.RejectListingCommand;
import co.sastra.catalog.dto.SellerListingCommand;
import co.sastra.catalog.dto.TakeDownListingCommand;
import co.sastra.catalog.dto.UpdateListingContentCommand;
import co.sastra.catalog.exception.ConditionNotAllowedException;
import co.sastra.catalog.exception.ListingNotFoundException;
import co.sastra.catalog.exception.MeasurementsIncompleteException;
import co.sastra.catalog.exception.SelfModerationForbiddenException;
import co.sastra.catalog.exception.SellerNotEligibleException;
import co.sastra.catalog.exception.UnknownCategoryException;
import co.sastra.catalog.model.Brand;
import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.Color;
import co.sastra.catalog.model.Condition;
import co.sastra.catalog.model.Description;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ListingRejectionReason;
import co.sastra.catalog.model.ListingStatus;
import co.sastra.catalog.model.MeasurementGroup;
import co.sastra.catalog.model.MeasurementKind;
import co.sastra.catalog.model.Measurements;
import co.sastra.catalog.model.ModerationAction;
import co.sastra.catalog.model.ModeratorId;
import co.sastra.catalog.model.ProductImage;
import co.sastra.catalog.model.ProductImageId;
import co.sastra.catalog.model.SellerId;
import co.sastra.catalog.model.ShippingDimensions;
import co.sastra.catalog.model.Size;
import co.sastra.catalog.model.SizeSystem;
import co.sastra.catalog.model.Title;
import co.sastra.catalog.model.WarrantyMonths;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImageDimensions;
import co.sastra.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CasosDeUsoDelCatalogoTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-08-24T15:00:00Z"), ZoneOffset.UTC);

    private CatalogoEnMemoria.Publicaciones publicaciones;
    private CatalogoEnMemoria.Arbol arbol;
    private CatalogoEnMemoria.Elegibilidad elegibilidad;
    private CatalogoEnMemoria.Bitacora bitacora;
    private CatalogoEnMemoria.Avisos avisos;
    private CatalogoEnMemoria.Almacen almacen;

    private SellerId vendedor;

    @BeforeEach
    void preparar() {
        publicaciones = new CatalogoEnMemoria.Publicaciones();
        arbol = new CatalogoEnMemoria.Arbol();
        elegibilidad = new CatalogoEnMemoria.Elegibilidad();
        bitacora = new CatalogoEnMemoria.Bitacora();
        avisos = new CatalogoEnMemoria.Avisos();
        almacen = new CatalogoEnMemoria.Almacen();
        vendedor = new SellerId(UUID.randomUUID());
    }

    @Nested
    class Crear {

        @Test
        void deberia_impedir_publicar_a_quien_no_esta_verificado_RN_011() {
            elegibilidad.revocar(vendedor);
            Category camisas = arbol.camisas();

            assertThatThrownBy(() -> crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas))))
                    .isInstanceOf(SellerNotEligibleException.class);
            assertThat(publicaciones.cuantas()).isZero();
        }

        // RN-013: quien perdio el sello no crea nuevas, y lo que ya tenia publicado no
        // se toca. Lo segundo lo garantiza que este caso de uso no mira nada existente.
        @Test
        void deberia_impedir_crear_a_quien_le_revocaron_el_sello_RN_013() {
            Category camisas = arbol.camisas();
            Listing ya = crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas)));

            elegibilidad.revocar(vendedor);

            assertThatThrownBy(() -> crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas))))
                    .isInstanceOf(SellerNotEligibleException.class);
            assertThat(publicaciones.buscar(ya.id())).isPresent();
        }

        @Test
        void deberia_crear_el_borrador_criterio_4() {
            Category camisas = arbol.camisas();

            Listing borrador = crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas)));

            assertThat(borrador.status()).isEqualTo(ListingStatus.DRAFT);
            assertThat(borrador.sellerId()).isEqualTo(vendedor);
            assertThat(publicaciones.buscar(borrador.id())).isPresent();
        }

        @Test
        void deberia_rechazar_una_categoria_que_no_existe() {
            Category huerfana = new Category(
                    co.sastra.catalog.model.CategoryId.nuevo(),
                    "inventada",
                    co.sastra.catalog.model.CategoryId.nuevo(),
                    java.util.Set.of(SizeSystem.ALPHA),
                    MeasurementGroup.TOP,
                    true,
                    true);

            assertThatThrownBy(() -> crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(huerfana))))
                    .isInstanceOf(UnknownCategoryException.class);
        }

        @Test
        void deberia_rechazar_una_categoria_retirada() {
            Category retirada = arbol.retirada();

            assertThatThrownBy(() -> crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(retirada))))
                    .isInstanceOf(UnknownCategoryException.class);
        }

        @Test
        void deberia_rechazar_un_celular_usado_RN_064() {
            Category celulares = arbol.celulares();
            ProductData usado = new ProductData(
                    celulares.id(),
                    new Title("Telefono usado en buen estado"),
                    new Description("Con marcas de uso."),
                    null,
                    Condition.GOOD,
                    Size.unica(),
                    medidasDe(MeasurementGroup.DEVICE),
                    Color.BLACK,
                    Money.dePesos(500_000),
                    envio(),
                    false,
                    null);

            assertThatThrownBy(() -> crear().execute(new CreateListingCommand(vendedor, usado)))
                    .isInstanceOf(ConditionNotAllowedException.class);
        }
    }

    @Nested
    class EnviarARevision {

        @Test
        void deberia_exigir_las_medidas_del_grupo_RN_021() {
            Category camisas = arbol.camisas();
            ProductData sinMedidas = conMedidas(datosDeCamisa(camisas), Measurements.vacias());
            Listing borrador = crear().execute(new CreateListingCommand(vendedor, sinMedidas));
            conOchoTomas(borrador);

            assertThatThrownBy(() -> enviar().execute(new SellerListingCommand(vendedor, borrador.id())))
                    .isInstanceOf(MeasurementsIncompleteException.class);
        }

        // El caso borde de RN-013: los borradores se conservan y no se pueden enviar.
        @Test
        void deberia_impedir_enviar_si_perdio_el_sello_con_borradores_abiertos_RN_013() {
            Listing borrador = borradorConTomas();
            elegibilidad.revocar(vendedor);

            assertThatThrownBy(() -> enviar().execute(new SellerListingCommand(vendedor, borrador.id())))
                    .isInstanceOf(SellerNotEligibleException.class);
            assertThat(publicaciones.buscar(borrador.id())).isPresent();
        }

        @Test
        void deberia_pasar_a_revision_con_todo_completo_criterio_19() {
            Listing borrador = borradorConTomas();

            Listing enviada = enviar().execute(new SellerListingCommand(vendedor, borrador.id()));

            assertThat(enviada.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
        }

        @Test
        void deberia_responder_como_si_no_existiera_la_publicacion_de_otro_criterio_33() {
            Listing ajena = borradorConTomas();
            SellerId otro = new SellerId(UUID.randomUUID());

            assertThatThrownBy(() -> enviar().execute(new SellerListingCommand(otro, ajena.id())))
                    .isInstanceOf(ListingNotFoundException.class);
        }
    }

    @Nested
    class Moderacion {

        @Test
        void deberia_publicar_avisar_y_dejar_rastro_criterios_21_y_26() {
            Listing enRevision = enviar().execute(new SellerListingCommand(
                    vendedor, borradorConTomas().id()));
            ModeratorId moderador = new ModeratorId(UUID.randomUUID());

            Listing aprobada = aprobar().execute(new ApproveListingCommand(moderador, enRevision.id()));

            assertThat(aprobada.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(bitacora.entradas()).singleElement().satisfies(entrada -> {
                assertThat(entrada.accion()).isEqualTo(ModerationAction.APPROVED);
                assertThat(entrada.actor()).isEqualTo(moderador);
            });
            assertThat(avisos.enviados())
                    .singleElement()
                    .satisfies(aviso -> assertThat(aviso.tipo()).isEqualTo("aprobada"));
        }

        // RN-063: es RN-060 aplicada al catalogo.
        @Test
        void deberia_impedir_que_un_moderador_apruebe_su_propia_publicacion_RN_063() {
            Listing enRevision = enviar().execute(new SellerListingCommand(
                    vendedor, borradorConTomas().id()));
            ModeratorId elMismo = new ModeratorId(vendedor.value());

            assertThatThrownBy(() -> aprobar().execute(new ApproveListingCommand(elMismo, enRevision.id())))
                    .isInstanceOf(SelfModerationForbiddenException.class);

            assertThat(publicaciones.buscar(enRevision.id()).orElseThrow().status())
                    .isEqualTo(ListingStatus.PENDING_REVIEW);
            assertThat(bitacora.entradas()).isEmpty();
            assertThat(avisos.enviados()).isEmpty();
        }

        @Test
        void deberia_impedir_que_un_moderador_rechace_su_propia_publicacion_RN_063() {
            Listing enRevision = enviar().execute(new SellerListingCommand(
                    vendedor, borradorConTomas().id()));
            ModeratorId elMismo = new ModeratorId(vendedor.value());

            assertThatThrownBy(() -> rechazar()
                            .execute(new RejectListingCommand(
                                    elMismo, enRevision.id(), ListingRejectionReason.PROHIBITED_ITEM, null)))
                    .isInstanceOf(SelfModerationForbiddenException.class);
        }

        @Test
        void deberia_guardar_motivo_y_avisar_al_rechazar_criterios_22_y_26() {
            Listing enRevision = enviar().execute(new SellerListingCommand(
                    vendedor, borradorConTomas().id()));
            ModeratorId moderador = new ModeratorId(UUID.randomUUID());

            Listing rechazada = rechazar()
                    .execute(new RejectListingCommand(
                            moderador, enRevision.id(), ListingRejectionReason.PHOTOS_UNUSABLE, "Frontal borrosa"));

            assertThat(rechazada.status()).isEqualTo(ListingStatus.REJECTED);
            assertThat(bitacora.entradas())
                    .singleElement()
                    .satisfies(entrada -> assertThat(entrada.motivo()).isEqualTo("PHOTOS_UNUSABLE"));
            // La nota viaja al vendedor: es el dato del criterio 22.
            assertThat(avisos.enviados()).singleElement().satisfies(aviso -> {
                assertThat(aviso.tipo()).isEqualTo("rechazada");
                assertThat(aviso.nota()).isEqualTo("Frontal borrosa");
            });
        }

        @Test
        void deberia_dejar_al_moderador_bajar_algo_ya_visible_RN_024_criterio_31() {
            Listing publicada = publicada();
            ModeratorId moderador = new ModeratorId(UUID.randomUUID());

            Listing retirada = retirar()
                    .execute(new TakeDownListingCommand(
                            moderador, publicada.id(), ListingRejectionReason.SUSPECTED_COUNTERFEIT, null));

            assertThat(retirada.status()).isEqualTo(ListingStatus.ARCHIVED);
            // Dos entradas y no una: la aprobacion previa tambien dejo rastro, y ese
            // rastro no se sobrescribe. Es justo lo que RN-045 exige.
            assertThat(bitacora.entradas()).hasSize(2).last().satisfies(entrada -> {
                assertThat(entrada.accion()).isEqualTo(ModerationAction.ARCHIVED);
                assertThat(entrada.motivo()).isEqualTo("SUSPECTED_COUNTERFEIT");
            });
            assertThat(avisos.enviados())
                    .anySatisfy(aviso -> assertThat(aviso.tipo()).isEqualTo("retirada"));
        }
    }

    @Nested
    class DespuesDePublicada {

        @Test
        void deberia_volver_a_revision_al_editar_contenido_RN_062() {
            Listing publicada = publicada();
            Category camisas = arbol.camisas();

            Listing editada =
                    editar().execute(new UpdateListingContentCommand(vendedor, publicada.id(), datosDeCamisa(camisas)));

            assertThat(editada.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
        }

        @Test
        void deberia_seguir_visible_al_cambiar_el_precio_RN_030() {
            Listing publicada = publicada();

            Listing conOtroPrecio = cambiarPrecio()
                    .execute(new ChangeListingPriceCommand(vendedor, publicada.id(), Money.dePesos(120_000)));

            assertThat(conOtroPrecio.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(conOtroPrecio.product().price()).isEqualTo(Money.dePesos(120_000));
        }

        @Test
        void deberia_pausar_y_reanudar_sin_moderacion_criterio_29() {
            Listing publicada = publicada();

            Listing pausada = pausar().execute(new SellerListingCommand(vendedor, publicada.id()));
            assertThat(pausada.status()).isEqualTo(ListingStatus.PAUSED);

            Listing reanudada = reanudar().execute(new SellerListingCommand(vendedor, publicada.id()));
            assertThat(reanudada.status()).isEqualTo(ListingStatus.PUBLISHED);
        }

        @Test
        void deberia_archivar_a_peticion_del_vendedor_criterio_30() {
            Listing publicada = publicada();

            Listing archivada = archivar().execute(new SellerListingCommand(vendedor, publicada.id()));

            assertThat(archivada.status()).isEqualTo(ListingStatus.ARCHIVED);
        }

        @Test
        void deberia_dejar_retirar_de_revision_criterio_20() {
            Listing enRevision = enviar().execute(new SellerListingCommand(
                    vendedor, borradorConTomas().id()));

            Listing retirada = retirarDeRevision().execute(new SellerListingCommand(vendedor, enRevision.id()));

            assertThat(retirada.status()).isEqualTo(ListingStatus.DRAFT);
        }
    }

    @Nested
    class Listado {

        @Test
        void deberia_devolver_solo_las_del_vendedor_que_pregunta() {
            Category camisas = arbol.camisas();
            crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas)));
            crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas)));
            crear().execute(new CreateListingCommand(new SellerId(UUID.randomUUID()), datosDeCamisa(camisas)));

            var suyas =
                    new ListSellerListingsUseCase(publicaciones).execute(new ListSellerListingsQuery(vendedor, 0, 20));

            assertThat(suyas)
                    .hasSize(2)
                    .allSatisfy(p -> assertThat(p.sellerId()).isEqualTo(vendedor));
        }

        @Test
        void deberia_rechazar_una_pagina_o_un_tamano_absurdos() {
            assertThatThrownBy(() -> new ListSellerListingsQuery(vendedor, -1, 20))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ListSellerListingsQuery(vendedor, 0, 51))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new ListSellerListingsQuery(vendedor, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------ apoyo

    private CreateListingUseCase crear() {
        return new CreateListingUseCase(publicaciones, arbol, elegibilidad, RELOJ);
    }

    private SubmitListingForReviewUseCase enviar() {
        return new SubmitListingForReviewUseCase(publicaciones, arbol, elegibilidad, RELOJ);
    }

    private ApproveListingUseCase aprobar() {
        return new ApproveListingUseCase(publicaciones, bitacora, avisos, RELOJ);
    }

    private RejectListingUseCase rechazar() {
        return new RejectListingUseCase(publicaciones, bitacora, avisos, RELOJ);
    }

    private TakeDownListingUseCase retirar() {
        return new TakeDownListingUseCase(publicaciones, bitacora, avisos, almacen, RELOJ);
    }

    private UpdateListingContentUseCase editar() {
        return new UpdateListingContentUseCase(publicaciones, arbol, elegibilidad, RELOJ);
    }

    private ChangeListingPriceUseCase cambiarPrecio() {
        return new ChangeListingPriceUseCase(publicaciones, RELOJ);
    }

    private PauseListingUseCase pausar() {
        return new PauseListingUseCase(publicaciones, RELOJ);
    }

    private ResumeListingUseCase reanudar() {
        return new ResumeListingUseCase(publicaciones, RELOJ);
    }

    private ArchiveListingUseCase archivar() {
        return new ArchiveListingUseCase(publicaciones, almacen, RELOJ);
    }

    private WithdrawListingUseCase retirarDeRevision() {
        return new WithdrawListingUseCase(publicaciones, RELOJ);
    }

    private Listing borradorConTomas() {
        Category camisas = arbol.camisas();
        Listing borrador = crear().execute(new CreateListingCommand(vendedor, datosDeCamisa(camisas)));
        return conOchoTomas(borrador);
    }

    /** Las tomas se ponen directamente sobre el agregado: subirlas es otro caso de uso. */
    private Listing conOchoTomas(Listing borrador) {
        Listing resultado = borrador;
        for (int posicion = 0; posicion < 8; posicion++) {
            resultado = resultado.conImagen(toma(posicion), Instant.now(RELOJ));
        }
        return publicaciones.guardar(resultado);
    }

    private Listing publicada() {
        Listing enRevision = enviar().execute(
                        new SellerListingCommand(vendedor, borradorConTomas().id()));
        return aprobar().execute(new ApproveListingCommand(new ModeratorId(UUID.randomUUID()), enRevision.id()));
    }

    private static ProductImage toma(int posicion) {
        return ProductImage.toma(
                ProductImageId.nuevo(),
                new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                posicion,
                new ImageDimensions(900, 1200),
                120_000L,
                ImageContentType.JPEG);
    }

    private static ProductData datosDeCamisa(Category categoria) {
        return new ProductData(
                categoria.id(),
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
                null);
    }

    private static ProductData conMedidas(ProductData datos, Measurements medidas) {
        return new ProductData(
                datos.categoria(),
                datos.titulo(),
                datos.descripcion(),
                datos.marca(),
                datos.condicion(),
                datos.talla(),
                medidas,
                datos.color(),
                datos.precio(),
                datos.envio(),
                datos.sellado(),
                datos.garantia());
    }

    private static Measurements medidasDe(MeasurementGroup grupo) {
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        grupo.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));
        return new Measurements(valores);
    }

    private static ShippingDimensions envio() {
        return new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0"));
    }

    @SuppressWarnings("unused")
    private static WarrantyMonths garantia() {
        return new WarrantyMonths(12);
    }
}
