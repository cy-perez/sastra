package co.sendik.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.dto.FavoriteCommand;
import co.sendik.catalog.dto.FavoritePage;
import co.sendik.catalog.dto.FavoriteState;
import co.sendik.catalog.dto.ListFavoritesQuery;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfFavoriteForbiddenException;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Los favoritos. HU-011.
 *
 * <p>Lo que se prueba aqui es lo que deciden los casos de uso: que marcar es idempotente,
 * que las dos reglas rechazan, y que la lista aplica RN-071 sin romper la paginacion. Que
 * la consulta ordene y filtre bien contra PostgreSQL lo prueba
 * {@code FavoritePersistenceTest}, que es donde se puede.
 *
 * <p>El reloj es fijo y la prueba lo mueve a mano. Los criterios 11 y 12 ordenan por la
 * fecha del gesto, y con un reloj real dos favoritos seguidos tendrian fechas distintas
 * por accidente: el desempate por identificador no se ejercitaria nunca.
 */
class FavoritosTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T15:00:00Z");
    private static final CategoryId CAMISAS = new CategoryId(UUID.randomUUID());
    private static final BuyerId ALGUIEN = new BuyerId(UUID.randomUUID());

    private CatalogoEnMemoria.Publicaciones publicaciones;
    private CatalogoEnMemoria.Guardados guardados;

    private AddFavoriteUseCase marcar;
    private RemoveFavoriteUseCase quitar;
    private ReadFavoriteStateUseCase estado;
    private ListFavoritesUseCase listar;
    private ExportFavoritesUseCase exportar;
    private EraseFavoritesUseCase borrar;

    private Instant reloj = AHORA;

    @BeforeEach
    void montar() {
        publicaciones = new CatalogoEnMemoria.Publicaciones();
        guardados = new CatalogoEnMemoria.Guardados(publicaciones);

        marcar = new AddFavoriteUseCase(guardados, publicaciones, new RelojMovible());
        quitar = new RemoveFavoriteUseCase(guardados);
        estado = new ReadFavoriteStateUseCase(guardados, publicaciones);
        listar = new ListFavoritesUseCase(guardados);
        exportar = new ExportFavoritesUseCase(guardados);
        borrar = new EraseFavoritesUseCase(guardados);
    }

    @Nested
    class Marcar {

        @Test
        void deberia_guardar_la_publicacion_criterio_2() {
            Listing publicada = publicar();

            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            assertThat(guardados.existe(ALGUIEN, publicada.id())).isTrue();
        }

        /**
         * Criterio 4. No hay ningun "si no existe, guarda" en ninguna parte: entre esa
         * lectura y la escritura cabe la peticion de la otra pestana.
         */
        @Test
        void deberia_ser_idempotente_criterio_4() {
            Listing publicada = publicar();
            FavoriteCommand orden = new FavoriteCommand(ALGUIEN, publicada.id());

            marcar.execute(orden);
            marcar.execute(orden);

            assertThat(guardados.cuantos()).isEqualTo(1);
        }

        /**
         * Y repetir no mueve el favorito a la cabeza de la lista. Si la segunda marca
         * sobrescribiera la fecha, un reintento de red reordenaria la lista de alguien.
         */
        @Test
        void no_deberia_mover_la_fecha_al_repetir() {
            Listing publicada = publicar();
            FavoriteCommand orden = new FavoriteCommand(ALGUIEN, publicada.id());

            marcar.execute(orden);
            reloj = AHORA.plus(Duration.ofHours(2));
            marcar.execute(orden);

            assertThat(exportar.execute(ALGUIEN))
                    .singleElement()
                    .extracting(Favorite::marcadoEn)
                    .isEqualTo(AHORA);
        }

        @Test
        void deberia_rechazar_lo_que_no_esta_publicado_criterio_6() {
            Listing pausada = publicaciones.guardar(publicar().pausar(AHORA));

            assertThatThrownBy(() -> marcar.execute(new FavoriteCommand(ALGUIEN, pausada.id())))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        @Test
        void deberia_rechazar_la_publicacion_propia_RN_072() {
            Listing publicada = publicar();
            BuyerId elVendedor = new BuyerId(publicada.sellerId().value());

            assertThatThrownBy(() -> marcar.execute(new FavoriteCommand(elVendedor, publicada.id())))
                    .isInstanceOf(SelfFavoriteForbiddenException.class);
        }

        @Test
        void deberia_rechazar_una_publicacion_que_no_existe() {
            assertThatThrownBy(() -> marcar.execute(new FavoriteCommand(ALGUIEN, ListingId.nuevo())))
                    .isInstanceOf(ListingNotFoundException.class);
        }
    }

    @Nested
    class Quitar {

        @Test
        void deberia_quitar_el_favorito_criterio_3() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            quitar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            assertThat(guardados.existe(ALGUIEN, publicada.id())).isFalse();
        }

        /** Quitar lo que no esta no es un error: es el doble pulsado del caso borde. */
        @Test
        void deberia_ser_idempotente() {
            assertThatCode(() -> quitar.execute(new FavoriteCommand(ALGUIEN, ListingId.nuevo())))
                    .doesNotThrowAnyException();
        }

        /**
         * RN-071 conserva la fila de lo que dejo de verse. Si quitar exigiera que la
         * publicacion siguiera publicada, esas filas serian imposibles de borrar para su
         * propio dueno.
         */
        @Test
        void deberia_poder_quitar_lo_que_ya_no_esta_publicado() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.archivar(AHORA));

            quitar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            assertThat(guardados.existe(ALGUIEN, publicada.id())).isFalse();
        }
    }

    @Nested
    class Estado {

        @Test
        void deberia_decir_que_esta_marcada_y_que_se_puede_criterio_1() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            FavoriteState respuesta = estado.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            assertThat(respuesta).isEqualTo(new FavoriteState(true, true));
        }

        @Test
        void deberia_decir_que_no_esta_marcada_y_que_se_puede() {
            Listing publicada = publicar();

            assertThat(estado.execute(new FavoriteCommand(ALGUIEN, publicada.id())))
                    .isEqualTo(new FavoriteState(false, true));
        }

        /** Criterio 5: sobre lo propio el control no se ofrece, y esto es lo que se lo dice. */
        @Test
        void no_deberia_ofrecerse_sobre_la_publicacion_propia_RN_072() {
            Listing publicada = publicar();
            BuyerId elVendedor = new BuyerId(publicada.sellerId().value());

            assertThat(estado.execute(new FavoriteCommand(elVendedor, publicada.id())))
                    .isEqualTo(new FavoriteState(false, false));
        }

        /**
         * El caso borde de la publicacion que se vende mientras esta en pantalla: sigue
         * marcada —la fila esta— y ya no se puede marcar.
         */
        @Test
        void deberia_seguir_marcada_y_dejar_de_ser_elegible_cuando_se_archiva() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.archivar(AHORA));

            assertThat(estado.execute(new FavoriteCommand(ALGUIEN, publicada.id())))
                    .isEqualTo(new FavoriteState(true, false));
        }

        /** No lanza sobre lo que no existe: la ficha necesita una respuesta, no un error. */
        @Test
        void no_deberia_fallar_sobre_una_publicacion_que_no_existe() {
            assertThat(estado.execute(new FavoriteCommand(ALGUIEN, ListingId.nuevo())))
                    .isEqualTo(new FavoriteState(false, false));
        }
    }

    @Nested
    class Lista {

        /** Criterio 11: el orden es el del gesto, no el de publicacion. */
        @Test
        void deberia_ordenar_por_lo_marcado_mas_recientemente_criterio_11() {
            Listing vieja = publicar(AHORA.minus(Duration.ofDays(30)));
            Listing nueva = publicar(AHORA);

            reloj = AHORA;
            marcar.execute(new FavoriteCommand(ALGUIEN, nueva.id()));
            reloj = AHORA.plus(Duration.ofMinutes(5));
            marcar.execute(new FavoriteCommand(ALGUIEN, vieja.id()));

            FavoritePage tramo = listar.execute(new ListFavoritesQuery(ALGUIEN, null, 24));

            assertThat(tramo.items()).extracting(Listing::id).containsExactly(vieja.id(), nueva.id());
        }

        /** Criterio 13: lo que deja de estar publicado no aparece, y nadie lo desmarco. */
        @Test
        void no_deberia_ensenar_lo_que_dejo_de_estar_publicado_criterio_13() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.pausar(AHORA));

            assertThat(listar.execute(new ListFavoritesQuery(ALGUIEN, null, 24)).items())
                    .isEmpty();
            assertThat(guardados.cuantos()).isEqualTo(1);
        }

        /** Criterio 14: y cuando vuelve a publicarse, vuelve a verse. Misma consulta. */
        @Test
        void deberia_volver_a_ensenarla_cuando_se_republica_criterio_14() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            Listing pausada = publicaciones.guardar(publicada.pausar(AHORA));
            publicaciones.guardar(pausada.reanudar(AHORA));

            assertThat(listar.execute(new ListFavoritesQuery(ALGUIEN, null, 24)).items())
                    .extracting(Listing::id)
                    .containsExactly(publicada.id());
        }

        @Test
        void no_deberia_ensenar_los_favoritos_de_otra_persona_RN_070() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));

            assertThat(listar.execute(new ListFavoritesQuery(new BuyerId(UUID.randomUUID()), null, 24))
                            .items())
                    .isEmpty();
        }

        /**
         * Criterio 12. El fallo clasico del cursor: con exactamente tantos favoritos como
         * el limite, deducir "hay mas" de que el tramo venga lleno entrega un cursor que
         * lleva a un tramo vacio.
         */
        @Test
        void no_deberia_prometer_mas_cuando_la_lista_cabe_justa_en_el_tramo() {
            marcarVarios(2);

            FavoritePage tramo = listar.execute(new ListFavoritesQuery(ALGUIEN, null, 2));

            assertThat(tramo.items()).hasSize(2);
            assertThat(tramo.hayMas()).isFalse();
            assertThat(tramo.siguiente()).isNull();
        }

        /** Criterio 12: avanzar no repite ni salta. */
        @Test
        void deberia_recorrer_la_lista_entera_sin_repetir_ni_saltar_criterio_12() {
            marcarVarios(5);

            FavoritePage primero = listar.execute(new ListFavoritesQuery(ALGUIEN, null, 2));
            FavoritePage segundo = listar.execute(new ListFavoritesQuery(ALGUIEN, primero.siguiente(), 2));
            FavoritePage tercero = listar.execute(new ListFavoritesQuery(ALGUIEN, segundo.siguiente(), 2));

            assertThat(primero.hayMas()).isTrue();
            assertThat(tercero.hayMas()).isFalse();
            assertThat(primero.items())
                    .hasSize(2)
                    .doesNotContainAnyElementsOf(segundo.items())
                    .doesNotContainAnyElementsOf(tercero.items());
            assertThat(segundo.items()).doesNotContainAnyElementsOf(tercero.items());
            assertThat(tercero.items()).hasSize(1);
        }

        /**
         * El desempate por identificador. Todos marcados en el mismo instante, que es la
         * norma con un reloj fijo y posible en produccion con dos toques seguidos: si el
         * cursor mirara solo la fecha, este recorrido repetiria o se saltaria elementos.
         */
        @Test
        void deberia_desempatar_por_identificador_cuando_se_marcaron_a_la_vez() {
            marcarVarios(4);

            FavoritePage primero = listar.execute(new ListFavoritesQuery(ALGUIEN, null, 2));
            FavoritePage segundo = listar.execute(new ListFavoritesQuery(ALGUIEN, primero.siguiente(), 2));

            assertThat(primero.items()).doesNotContainAnyElementsOf(segundo.items());
            assertThat(segundo.items()).hasSize(2);
            assertThat(segundo.hayMas()).isFalse();
        }

        @Test
        void deberia_ensenar_vacio_cuando_no_hay_nada_criterio_15() {
            FavoritePage tramo = listar.execute(new ListFavoritesQuery(ALGUIEN, null, 24));

            assertThat(tramo.items()).isEmpty();
            assertThat(tramo.hayMas()).isFalse();
        }

        @Test
        void deberia_rechazar_un_limite_por_encima_del_tope() {
            assertThatThrownBy(() -> new ListFavoritesQuery(ALGUIEN, null, 500))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class DatosPersonales {

        /**
         * La descarga entrega lo que se guarda, no lo que se ensena: aqui si sale el
         * favorito cuya publicacion se archivo, que la lista esconde por RN-071.
         */
        @Test
        void deberia_exportar_tambien_lo_que_la_lista_esconde() {
            Listing publicada = publicar();
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));
            publicaciones.guardar(publicada.archivar(AHORA));

            assertThat(listar.execute(new ListFavoritesQuery(ALGUIEN, null, 24)).items())
                    .isEmpty();
            assertThat(exportar.execute(ALGUIEN))
                    .singleElement()
                    .extracting(Favorite::publicacion)
                    .isEqualTo(publicada.id());
        }

        @Test
        void deberia_borrar_los_de_esa_persona_al_cerrar_la_cuenta() {
            Listing publicada = publicar();
            BuyerId otra = new BuyerId(UUID.randomUUID());
            marcar.execute(new FavoriteCommand(ALGUIEN, publicada.id()));
            marcar.execute(new FavoriteCommand(otra, publicada.id()));

            borrar.execute(ALGUIEN);

            assertThat(exportar.execute(ALGUIEN)).isEmpty();
            assertThat(exportar.execute(otra)).hasSize(1);
        }

        @Test
        void no_deberia_fallar_al_borrar_una_cuenta_sin_favoritos() {
            assertThatCode(() -> borrar.execute(ALGUIEN)).doesNotThrowAnyException();
        }
    }

    // ------------------------------------------------------------- datos

    /** Un reloj que la prueba mueve a mano, para comprobar el orden del criterio 11. */
    private final class RelojMovible extends Clock {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return reloj;
        }
    }

    private void marcarVarios(int cuantos) {
        for (int i = 0; i < cuantos; i++) {
            marcar.execute(new FavoriteCommand(ALGUIEN, publicar().id()));
        }
    }

    private Listing publicar() {
        return publicar(AHORA);
    }

    private Listing publicar(Instant cuando) {
        Listing aprobada = conTomas(borrador(new SellerId(UUID.randomUUID())))
                .enviarARevision(cuando)
                .aprobar(new ModeratorId(UUID.randomUUID()), cuando);

        return publicaciones.guardar(aprobada);
    }

    private static Listing borrador(SellerId vendedor) {
        Map<MeasurementKind, BigDecimal> medidas = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> medidas.put(medida, new BigDecimal("50.0")));

        Product producto = new Product(
                ProductId.nuevo(),
                vendedor,
                CAMISAS,
                new Title("Camisa de lino color hueso"),
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

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    private static Listing conTomas(Listing publicacion) {
        Listing resultado = publicacion;
        for (int i = 0; i < ProductImage.TOMAS_DE_LA_SECUENCIA; i++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                            i,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }
}
