package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfFavoriteForbiddenException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FavoriteTest {

    private static final BuyerId ALGUIEN = new BuyerId(UUID.randomUUID());

    @Nested
    class Marcar {

        @Test
        void deberia_guardar_quien_marco_que_y_cuando() {
            Listing publicacion = CatalogoDePrueba.publicada();

            Favorite favorito = Favorite.de(ALGUIEN, publicacion, AHORA);

            assertThat(favorito.quien()).isEqualTo(ALGUIEN);
            assertThat(favorito.publicacion()).isEqualTo(publicacion.id());
            assertThat(favorito.marcadoEn()).isEqualTo(AHORA);
        }

        @Test
        void deberia_rechazar_la_publicacion_propia_RN_072() {
            Listing publicacion = CatalogoDePrueba.publicada();
            BuyerId elVendedor = new BuyerId(publicacion.sellerId().value());

            assertThatThrownBy(() -> Favorite.de(elVendedor, publicacion, AHORA))
                    .isInstanceOf(SelfFavoriteForbiddenException.class);
        }
    }

    @Nested
    class LoQueNoSeVe {

        @Test
        void deberia_rechazar_un_borrador_RN_068() {
            Listing borrador = CatalogoDePrueba.borradorCompleto();

            assertThatThrownBy(() -> Favorite.de(ALGUIEN, borrador, AHORA))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        @Test
        void deberia_rechazar_lo_pausado_RN_071() {
            Listing pausada = CatalogoDePrueba.publicada().pausar(AHORA);

            assertThatThrownBy(() -> Favorite.de(ALGUIEN, pausada, AHORA)).isInstanceOf(ListingNotFoundException.class);
        }

        /**
         * El estado se mira antes que el dueno, y no al reves: sobre el borrador de otra
         * persona, un 403 confirmaria que ese identificador es una publicacion real que
         * existe y no es suya. Todo lo que no esta publicado tiene que responder igual que
         * lo que no existe.
         */
        @Test
        void deberia_responder_no_encontrada_y_no_prohibida_sobre_el_borrador_ajeno() {
            Listing borrador = CatalogoDePrueba.borradorCompleto();
            BuyerId elVendedor = new BuyerId(borrador.sellerId().value());

            assertThatThrownBy(() -> Favorite.de(elVendedor, borrador, AHORA))
                    .isInstanceOf(ListingNotFoundException.class);
        }
    }

    @Nested
    class Identidad {

        /**
         * Criterio 4: volver a marcar lo que ya estaba marcado no crea un segundo
         * favorito. La fecha no entra en la identidad, o dos pestanas darian dos.
         */
        @Test
        void deberia_ser_el_mismo_favorito_aunque_se_marque_en_otro_momento() {
            Listing publicacion = CatalogoDePrueba.publicada();

            Favorite primero = Favorite.de(ALGUIEN, publicacion, AHORA);
            Favorite repetido = Favorite.de(ALGUIEN, publicacion, AHORA.plus(Duration.ofHours(3)));

            assertThat(repetido).isEqualTo(primero);
            assertThat(repetido).hasSameHashCodeAs(primero);
        }

        @Test
        void deberia_distinguir_el_mismo_producto_guardado_por_dos_personas() {
            Listing publicacion = CatalogoDePrueba.publicada();

            Favorite mio = Favorite.de(ALGUIEN, publicacion, AHORA);
            Favorite suyo = Favorite.de(new BuyerId(UUID.randomUUID()), publicacion, AHORA);

            assertThat(mio).isNotEqualTo(suyo);
        }

        @Test
        void deberia_distinguir_dos_publicaciones_de_la_misma_persona() {
            Favorite una = Favorite.de(ALGUIEN, CatalogoDePrueba.publicada(), AHORA);
            Favorite otra = Favorite.de(ALGUIEN, CatalogoDePrueba.publicada(), AHORA);

            assertThat(una).isNotEqualTo(otra);
        }
    }

    @Nested
    class Reconstruir {

        /**
         * RN-071: lo que deja de estar publicado desaparece de la lista sin borrarse. Si
         * leer la fila volviera a comprobar las reglas, pausar una publicacion guardada
         * romperia la lectura de la lista entera en vez de hacerla desaparecer de ella.
         */
        @Test
        void deberia_leer_un_favorito_guardado_sin_volver_a_comprobar_el_estado() {
            Listing pausada = CatalogoDePrueba.publicada().pausar(AHORA);

            Favorite guardado = Favorite.reconstruir(ALGUIEN, pausada.id(), AHORA);

            assertThat(guardado.publicacion()).isEqualTo(pausada.id());
            assertThat(guardado.marcadoEn()).isEqualTo(AHORA);
        }
    }
}
