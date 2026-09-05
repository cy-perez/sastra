package co.sendik.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import co.sendik.catalog.dto.ApproveListingCommand;
import co.sendik.catalog.dto.CreateListingCommand;
import co.sendik.catalog.dto.ProductData;
import co.sendik.catalog.dto.ReadModerationHistoryQuery;
import co.sendik.catalog.dto.RejectListingCommand;
import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.dto.TakeDownListingCommand;
import co.sendik.catalog.dto.UpdateListingContentCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.model.ModeratorId;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * El rastro de moderacion de una publicacion. HU-013.
 *
 * <p>Lo que se prueba aqui es lo que decide el caso de uso: que el rastro de otra persona
 * no se entrega, que el envio queda anotado por los dos caminos que llevan a revision, y
 * que varias vueltas salen todas. Que la consulta ordene y desempate bien contra PostgreSQL
 * lo prueba {@code CatalogPersistenceTest}, que es donde se puede.
 *
 * <p>El reloj es fijo y la prueba lo mueve a mano, como en {@code FavoritosTest}: el orden
 * del rastro es por fecha, y con un reloj real dos eventos seguidos tendrian fechas
 * distintas por accidente.
 */
class RastroDeModeracionTest {

    private static final Instant AHORA = Instant.parse("2026-09-04T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

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
    class DeQuienEs {

        /**
         * Criterio 7. Un 403 confirmaria que esa publicacion existe, asi que no se
         * distingue de la que no existe: las dos salen por la misma excepcion.
         */
        @Test
        void deberia_negar_el_rastro_de_una_publicacion_ajena_como_si_no_existiera() {
            Listing ajena = enRevision();
            SellerId otro = new SellerId(UUID.randomUUID());

            assertThatThrownBy(() -> rastro().execute(new ReadModerationHistoryQuery(otro, ajena.id())))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        @Test
        void deberia_negar_el_rastro_de_una_publicacion_que_no_existe() {
            assertThatThrownBy(() -> rastro().execute(new ReadModerationHistoryQuery(vendedor, ListingId.nuevo())))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        /**
         * Y no basta con que falle: tiene que fallar <strong>antes</strong> de leer la
         * bitacora. Si preguntara primero por el rastro y comprobara el dueno despues, los
         * eventos de otra persona ya habrian salido del puerto, y bastaria con olvidar un
         * {@code throw} para que salieran tambien por la API.
         */
        @Test
        void deberia_comprobar_el_dueno_antes_de_leer_la_bitacora() {
            Listing ajena = enRevision();
            SellerId otro = new SellerId(UUID.randomUUID());
            bitacora.olvidarQueSeLeyo();

            assertThatThrownBy(() -> rastro().execute(new ReadModerationHistoryQuery(otro, ajena.id())))
                    .isInstanceOf(ListingNotFoundException.class);
            assertThat(bitacora.seLeyo()).isFalse();
        }
    }

    @Nested
    class LoQueCuenta {

        /** Criterio 6: un borrador que nunca salio no tiene rastro, y eso no es un error. */
        @Test
        void deberia_devolver_un_rastro_vacio_para_un_borrador_que_nunca_salio() {
            Listing borrador = borradorConTomas();

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, borrador.id()));

            assertThat(eventos).isEmpty();
        }

        @Test
        void deberia_anotar_el_envio_a_revision() {
            Listing enviada = enRevision();

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, enviada.id()));

            assertThat(eventos)
                    .extracting(ModerationEvent::action, ModerationEvent::reason, ModerationEvent::occurredAt)
                    .containsExactly(tuple(ModerationAction.SUBMITTED, null, AHORA));
        }

        /** Criterio 1: se rechazo, cuando, y con que motivo. */
        @Test
        void deberia_contar_el_rechazo_con_su_motivo() {
            Listing enviada = enRevision();
            rechazar()
                    .execute(new RejectListingCommand(
                            unModerador(), enviada.id(), ListingRejectionReason.PHOTOS_UNUSABLE, "Salen movidas."));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, enviada.id()));

            assertThat(eventos)
                    .extracting(ModerationEvent::action, ModerationEvent::reason)
                    .containsExactly(
                            tuple(ModerationAction.REJECTED, ListingRejectionReason.PHOTOS_UNUSABLE),
                            tuple(ModerationAction.SUBMITTED, null));
        }

        /** Criterio 2: aprobar no lleva motivo, y no se le inventa ninguno. */
        @Test
        void deberia_contar_la_aprobacion_sin_motivo() {
            Listing enviada = enRevision();
            aprobar().execute(new ApproveListingCommand(unModerador(), enviada.id()));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, enviada.id()));

            assertThat(eventos)
                    .first()
                    .extracting(ModerationEvent::action, ModerationEvent::reason)
                    .containsExactly(ModerationAction.APPROVED, null);
        }

        /** Criterio 3: el retiro de RN-024 lleva motivo obligatorio, y sale. */
        @Test
        void deberia_contar_el_retiro_de_lo_que_ya_era_visible_con_su_motivo() {
            Listing publicada = publicada();
            retirar()
                    .execute(new TakeDownListingCommand(
                            unModerador(), publicada.id(), ListingRejectionReason.PROHIBITED_ITEM, null));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, publicada.id()));

            assertThat(eventos)
                    .first()
                    .extracting(ModerationEvent::action, ModerationEvent::reason)
                    .containsExactly(ModerationAction.ARCHIVED, ListingRejectionReason.PROHIBITED_ITEM);
        }

        /**
         * El caso borde de la historia, y el que ya estaba resuelto en el codigo: archivar
         * es del vendedor y retirar es del moderador, y los dos terminan en {@code ARCHIVED}.
         * Solo el segundo escribe en la bitacora, asi que un {@code ARCHIVED} en el rastro
         * siempre es un retiro. Esta prueba fija esa propiedad para que no se pierda el dia
         * que alguien decida anotar tambien lo que hace el vendedor.
         */
        @Test
        void deberia_dejar_sin_rastro_lo_que_archiva_el_propio_vendedor() {
            Listing publicada = publicada();
            archivar().execute(new SellerListingCommand(vendedor, publicada.id()));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, publicada.id()));

            assertThat(eventos).extracting(ModerationEvent::action).doesNotContain(ModerationAction.ARCHIVED);
        }
    }

    @Nested
    class LasDosVueltas {

        /**
         * Criterio 4, que es la razon de que el envio se anote como evento. Rechazada,
         * corregida y reenviada: se ven las dos vueltas enteras y no solo la ultima.
         */
        @Test
        void deberia_ensenar_las_dos_vueltas_de_una_rechazada_y_reenviada() {
            Listing primera = enviarEn(AHORA)
                    .execute(new SellerListingCommand(
                            vendedor, borradorConTomas().id()));
            rechazarEn(AHORA.plus(Duration.ofHours(1)))
                    .execute(new RejectListingCommand(
                            unModerador(), primera.id(), ListingRejectionReason.PHOTOS_UNUSABLE, null));

            // Corregir una rechazada la devuelve a borrador (RN-062), y desde ahi se reenvia.
            editarEn(AHORA.plus(Duration.ofHours(2)))
                    .execute(new UpdateListingContentCommand(vendedor, primera.id(), datosDeCamisa(arbol.camisas())));
            enviarEn(AHORA.plus(Duration.ofHours(3))).execute(new SellerListingCommand(vendedor, primera.id()));
            aprobarEn(AHORA.plus(Duration.ofHours(4))).execute(new ApproveListingCommand(unModerador(), primera.id()));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, primera.id()));

            assertThat(eventos)
                    .extracting(ModerationEvent::action)
                    .containsExactly(
                            ModerationAction.APPROVED,
                            ModerationAction.SUBMITTED,
                            ModerationAction.REJECTED,
                            ModerationAction.SUBMITTED);
        }

        /**
         * El segundo camino a {@code PENDING_REVIEW}, y el que menos se ve: RN-062 devuelve
         * a la cola lo que se edita estando publicado. El vendedor lo vivio como cambiar la
         * descripcion, asi que sin esta entrada el rastro le ensena una decision de
         * moderacion que nadie le explica de donde salio.
         */
        @Test
        void deberia_anotar_el_envio_cuando_editar_una_viva_la_devuelve_a_la_cola_RN_062() {
            Listing publicada = publicada();

            editarEn(AHORA.plus(Duration.ofHours(1)))
                    .execute(new UpdateListingContentCommand(vendedor, publicada.id(), datosDeCamisa(arbol.camisas())));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, publicada.id()));

            // Con fecha, y se puede: desde HU-013 ninguna fila del rastro se fecha con el
            // `now()` del motor. Los cinco caminos que escriben en la bitacora pasan el
            // instante con el que sellan la publicacion, asi que el orden del rastro y el de
            // los datos de la publicacion no pueden discrepar.
            assertThat(eventos)
                    .extracting(ModerationEvent::action, ModerationEvent::occurredAt)
                    .containsExactly(
                            tuple(ModerationAction.SUBMITTED, AHORA.plus(Duration.ofHours(1))),
                            tuple(ModerationAction.APPROVED, AHORA),
                            tuple(ModerationAction.SUBMITTED, AHORA));
        }

        /**
         * Editar un borrador no lo manda a ninguna parte: se queda donde esta. Anotar un
         * envio ahi llenaria el rastro de vueltas que nunca ocurrieron -el formulario guarda
         * solo con teclear- y taparia las de verdad.
         */
        @Test
        void deberia_callarse_cuando_editar_un_borrador_lo_deja_donde_estaba() {
            Listing borrador = borradorConTomas();

            editarEn(AHORA.plus(Duration.ofHours(1)))
                    .execute(new UpdateListingContentCommand(vendedor, borrador.id(), datosDeCamisa(arbol.camisas())));

            assertThat(rastro().execute(new ReadModerationHistoryQuery(vendedor, borrador.id())))
                    .isEmpty();
        }
    }

    @Nested
    class LosDosRelojes {

        /**
         * La regresion del defecto que destapo implementar esto, y la unica prueba que puede
         * verlo: con un reloj que <strong>avanza</strong>.
         *
         * <p>El envio sellaba la hora con el reloj de la aplicacion y las decisiones con el
         * {@code now()} de la tabla, y dos relojes escribiendo en un mismo registro ordenado
         * se cruzan: {@code ListingJourneyTest} se encontro una aprobacion fechada antes del
         * envio que la habia provocado.
         *
         * <p><strong>Con un {@code Clock.fixed} esto no se puede probar</strong>, y por eso
         * hace falta este: llamar al reloj una vez o dos es indistinguible cuando siempre
         * devuelve lo mismo, asi que se podria revertir el {@code Instant ahora} de los cinco
         * casos de uso y toda la suite seguiria verde. Aqui cada consulta da un instante
         * distinto, asi que la unica forma de que el evento y el sello de la publicacion
         * coincidan es que el caso de uso mire el reloj una sola vez.
         */
        @Test
        void deberia_fechar_el_envio_con_el_mismo_instante_que_sella_la_publicacion() {
            RelojQueAvanza reloj = new RelojQueAvanza(AHORA);
            Listing borrador = borradorConTomas();

            Listing enviada = new SubmitListingForReviewUseCase(publicaciones, arbol, elegibilidad, bitacora, reloj)
                    .execute(new SellerListingCommand(vendedor, borrador.id()));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, enviada.id()));

            assertThat(eventos)
                    .singleElement()
                    .satisfies(evento -> assertThat(evento.occurredAt()).isEqualTo(enviada.submittedAt()));
        }

        /** Lo mismo por el lado del moderador: el evento y {@code moderated_at} son el mismo momento. */
        @Test
        void deberia_fechar_la_decision_con_el_mismo_instante_que_sella_la_publicacion() {
            RelojQueAvanza reloj = new RelojQueAvanza(AHORA);
            Listing enRevision = enRevision();

            Listing aprobada = new ApproveListingUseCase(publicaciones, bitacora, avisos, reloj)
                    .execute(new ApproveListingCommand(unModerador(), enRevision.id()));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, aprobada.id()));

            assertThat(eventos.getFirst().action()).isEqualTo(ModerationAction.APPROVED);
            assertThat(eventos.getFirst().occurredAt()).isEqualTo(aprobada.moderatedAt());
        }

        /** Y el orden que el defecto rompia: el envio antes que la decision que provoco. */
        @Test
        void deberia_dejar_el_envio_antes_que_la_decision_que_provoco() {
            RelojQueAvanza reloj = new RelojQueAvanza(AHORA);
            Listing borrador = borradorConTomas();

            Listing enviada = new SubmitListingForReviewUseCase(publicaciones, arbol, elegibilidad, bitacora, reloj)
                    .execute(new SellerListingCommand(vendedor, borrador.id()));
            new ApproveListingUseCase(publicaciones, bitacora, avisos, reloj)
                    .execute(new ApproveListingCommand(unModerador(), enviada.id()));

            List<ModerationEvent> eventos = rastro().execute(new ReadModerationHistoryQuery(vendedor, enviada.id()));

            assertThat(eventos.getFirst().occurredAt())
                    .isAfter(eventos.getLast().occurredAt());
        }
    }

    /**
     * Un reloj que devuelve un instante distinto en cada consulta.
     *
     * <p>Existe porque {@code Clock.fixed} esconde exactamente el defecto que hay que fijar:
     * con el, mirar el reloj una vez o dos da lo mismo.
     */
    private static final class RelojQueAvanza extends Clock {

        private Instant siguiente;

        private RelojQueAvanza(Instant desde) {
            this.siguiente = desde;
        }

        @Override
        public Instant instant() {
            Instant ahora = siguiente;
            siguiente = siguiente.plus(Duration.ofMinutes(1));
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    // ------------------------------------------------------------------ apoyo

    private ReadModerationHistoryUseCase rastro() {
        return new ReadModerationHistoryUseCase(publicaciones, bitacora);
    }

    private SubmitListingForReviewUseCase enviarEn(Instant momento) {
        return new SubmitListingForReviewUseCase(
                publicaciones, arbol, elegibilidad, bitacora, Clock.fixed(momento, ZoneOffset.UTC));
    }

    private UpdateListingContentUseCase editarEn(Instant momento) {
        return new UpdateListingContentUseCase(
                publicaciones, arbol, elegibilidad, bitacora, Clock.fixed(momento, ZoneOffset.UTC));
    }

    private ApproveListingUseCase aprobarEn(Instant momento) {
        return new ApproveListingUseCase(publicaciones, bitacora, avisos, Clock.fixed(momento, ZoneOffset.UTC));
    }

    private RejectListingUseCase rechazarEn(Instant momento) {
        return new RejectListingUseCase(publicaciones, bitacora, avisos, Clock.fixed(momento, ZoneOffset.UTC));
    }

    private ApproveListingUseCase aprobar() {
        return aprobarEn(AHORA);
    }

    private RejectListingUseCase rechazar() {
        return rechazarEn(AHORA);
    }

    private TakeDownListingUseCase retirar() {
        return new TakeDownListingUseCase(publicaciones, bitacora, avisos, almacen, RELOJ);
    }

    private ArchiveListingUseCase archivar() {
        return new ArchiveListingUseCase(publicaciones, almacen, RELOJ);
    }

    private Listing enRevision() {
        return enviarEn(AHORA)
                .execute(new SellerListingCommand(vendedor, borradorConTomas().id()));
    }

    private Listing publicada() {
        return aprobar()
                .execute(new ApproveListingCommand(unModerador(), enRevision().id()));
    }

    private Listing borradorConTomas() {
        Listing borrador = new CreateListingUseCase(publicaciones, arbol, elegibilidad, RELOJ)
                .execute(new CreateListingCommand(vendedor, datosDeCamisa(arbol.camisas())));

        Listing resultado = borrador;
        for (int posicion = 0; posicion < 8; posicion++) {
            resultado = resultado.conImagen(toma(posicion), AHORA);
        }
        return publicaciones.guardar(resultado);
    }

    private static ModeratorId unModerador() {
        return new ModeratorId(UUID.randomUUID());
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
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));

        return new ProductData(
                categoria.id(),
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                new Brand("Zara"),
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(valores),
                Color.BEIGE,
                Money.dePesos(185_000),
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);
    }
}
