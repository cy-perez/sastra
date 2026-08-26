package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.exception.InvalidListingTransitionException;
import co.sendik.catalog.exception.ReferenceImageNotAllowedException;
import co.sendik.catalog.exception.ShotsIncompleteException;
import co.sendik.shared.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ListingTest {

    private static final ModeratorId MODERADOR = new ModeratorId(UUID.randomUUID());

    @Nested
    class Borrador {

        @Test
        void deberia_nacer_en_borrador_y_sin_imagenes() {
            Listing borrador = CatalogoDePrueba.borrador();

            assertThat(borrador.status()).isEqualTo(ListingStatus.DRAFT);
            assertThat(borrador.images()).isEmpty();
            assertThat(borrador.esVisible()).isFalse();
        }

        @Test
        void deberia_exigir_las_ocho_tomas_para_enviar_a_revision_RN_017() {
            Listing sinTomas = CatalogoDePrueba.borrador();

            assertThatThrownBy(() -> sinTomas.enviarARevision(AHORA))
                    .isInstanceOf(ShotsIncompleteException.class)
                    .hasMessageContaining("Se exigen 8");
        }

        @Test
        void deberia_rechazar_siete_tomas_aunque_esten_las_cuatro_canonicas_RN_017() {
            Listing casi = CatalogoDePrueba.borrador();
            for (int posicion = 0; posicion < 7; posicion++) {
                casi = casi.conImagen(CatalogoDePrueba.toma(posicion), AHORA);
            }
            Listing conSiete = casi;

            assertThatThrownBy(() -> conSiete.enviarARevision(AHORA)).isInstanceOf(ShotsIncompleteException.class);
        }

        // La rama de canonicas solo se alcanza cuando el conteo cuadra, y eso solo pasa
        // con tecnologia sellada: cuatro tomas exigidas, cuatro subidas, ninguna canonica
        // porque estan todas en posiciones impares. La version anterior de esta prueba
        // subia cuatro impares a una camisa, que exige ocho, asi que fallaba por el
        // conteo y nunca ejercitaba RN-016.
        @Test
        void deberia_exigir_las_cuatro_canonicas_RN_016() {
            Listing sellado = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true));
            for (int posicion = 1; posicion < 8; posicion += 2) {
                sellado = sellado.conImagen(CatalogoDePrueba.toma(posicion), AHORA);
            }
            Listing cuatroSinCanonicas = sellado;

            assertThat(cuatroSinCanonicas.tomasDelVendedor()).hasSize(4);
            assertThatThrownBy(() -> cuatroSinCanonicas.enviarARevision(AHORA))
                    .isInstanceOf(ShotsIncompleteException.class)
                    .hasMessageContaining("canonicas");
        }

        @Test
        void deberia_pasar_a_revision_con_las_ocho_tomas_completas() {
            Listing enviada = CatalogoDePrueba.borradorCompleto().enviarARevision(AHORA);

            assertThat(enviada.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
        }

        @Test
        void deberia_reemplazar_la_toma_de_una_posicion_en_vez_de_duplicarla() {
            Listing con = CatalogoDePrueba.borrador()
                    .conImagen(CatalogoDePrueba.toma(0), AHORA)
                    .conImagen(CatalogoDePrueba.toma(0), AHORA);

            assertThat(con.tomasDelVendedor()).hasSize(1);
        }
    }

    @Nested
    class Revision {

        @Test
        void deberia_publicar_al_aprobar_y_dejar_rastro_del_moderador() {
            Listing publicada =
                    CatalogoDePrueba.borradorCompleto().enviarARevision(AHORA).aprobar(MODERADOR, AHORA);

            assertThat(publicada.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(publicada.esVisible()).isTrue();
            assertThat(publicada.publishedAt()).isEqualTo(AHORA);
            assertThat(publicada.moderatedBy()).isEqualTo(MODERADOR);
        }

        @Test
        void deberia_exigir_motivo_al_rechazar_RN_022() {
            Listing enRevision = CatalogoDePrueba.borradorCompleto().enviarARevision(AHORA);

            assertThatThrownBy(() -> enRevision.rechazar(MODERADOR, null, "algo", AHORA))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("RN-022");
        }

        @Test
        void deberia_conservar_motivo_y_nota_al_rechazar_RN_022() {
            Listing rechazada = CatalogoDePrueba.borradorCompleto()
                    .enviarARevision(AHORA)
                    .rechazar(MODERADOR, ListingRejectionReason.PHOTOS_UNUSABLE, "Se ve borrosa la frontal", AHORA);

            assertThat(rechazada.status()).isEqualTo(ListingStatus.REJECTED);
            assertThat(rechazada.rejectionReason()).isEqualTo(ListingRejectionReason.PHOTOS_UNUSABLE);
            assertThat(rechazada.rejectionNote()).isEqualTo("Se ve borrosa la frontal");
        }

        @Test
        void deberia_conservar_datos_e_imagenes_al_retomar_una_rechazada_criterio_23() {
            Listing rechazada = CatalogoDePrueba.borradorCompleto()
                    .enviarARevision(AHORA)
                    .rechazar(MODERADOR, ListingRejectionReason.MEASUREMENTS_UNRELIABLE, null, AHORA);

            Listing retomada = rechazada.retomar(AHORA);

            assertThat(retomada.status()).isEqualTo(ListingStatus.DRAFT);
            assertThat(retomada.tomasDelVendedor()).hasSize(8);
            assertThat(retomada.product()).isEqualTo(rechazada.product());
        }

        @Test
        void deberia_dejar_retirar_una_solicitud_antes_de_que_se_decida_criterio_20() {
            Listing retirada =
                    CatalogoDePrueba.borradorCompleto().enviarARevision(AHORA).retirarDeRevision(AHORA);

            assertThat(retirada.status()).isEqualTo(ListingStatus.DRAFT);
        }

        @Test
        void deberia_impedir_aprobar_algo_que_no_esta_en_revision() {
            Listing borrador = CatalogoDePrueba.borradorCompleto();

            assertThatThrownBy(() -> borrador.aprobar(MODERADOR, AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
        }
    }

    @Nested
    class EdicionYModeracion {

        // El agujero que destapo la revision: sustituir las ocho tomas de algo ya
        // aprobado por las de una replica, sin volver a pasar por moderacion.
        @Test
        void deberia_volver_a_revision_al_cambiar_una_toma_de_algo_publicado_RN_062() {
            Listing publicada = CatalogoDePrueba.publicada();

            Listing conOtraFoto = publicada.conImagen(CatalogoDePrueba.toma(0), AHORA);

            assertThat(conOtraFoto.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
            assertThat(conOtraFoto.esVisible()).isFalse();
        }

        @Test
        void deberia_volver_a_revision_al_quitar_una_toma_de_algo_publicado_RN_062() {
            Listing publicada = CatalogoDePrueba.publicada();

            Listing conSiete = publicada.sinImagen(publicada.images().getFirst().id(), AHORA);

            assertThat(conSiete.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
            assertThat(conSiete.tomasDelVendedor()).hasSize(7);
        }

        // Criterio 19: mientras el moderador la mira, no se toca.
        @Test
        void deberia_impedir_editar_algo_que_espera_revision_criterio_19() {
            Listing enRevision = CatalogoDePrueba.borradorCompleto().enviarARevision(AHORA);

            assertThatThrownBy(() -> enRevision.editarContenido(enRevision.product(), AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
        }

        // Criterio 23: corregir una rechazada la devuelve a borrador, no a revision.
        @Test
        void deberia_llevar_a_borrador_al_editar_una_rechazada_criterio_23() {
            Listing rechazada = CatalogoDePrueba.borradorCompleto()
                    .enviarARevision(AHORA)
                    .rechazar(MODERADOR, ListingRejectionReason.PHOTOS_UNUSABLE, null, AHORA);

            Listing corregida = rechazada.editarContenido(rechazada.product(), AHORA);

            assertThat(corregida.status()).isEqualTo(ListingStatus.DRAFT);
            assertThat(corregida.tomasDelVendedor()).hasSize(8);
        }

        @Test
        void deberia_borrar_las_referencias_al_dejar_de_estar_sellado_RN_066() {
            Listing sellado = CatalogoDePrueba.conTomas(CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true)))
                    .conImagen(CatalogoDePrueba.referencia(0), AHORA);
            assertThat(sellado.imagenesDeReferencia()).hasSize(1);

            Listing abierto = sellado.editarContenido(CatalogoDePrueba.celular(false), AHORA);

            assertThat(abierto.imagenesDeReferencia()).isEmpty();
            assertThat(abierto.tomasExigidas()).isEqualTo(8);
        }

        @Test
        void deberia_conservar_las_dos_marcas_de_atencion_a_la_vez_criterios_12_y_18() {
            Listing barataYDeGaleria = CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(9_000)))
                    .marcarCargaDesdeGaleria(AHORA);

            assertThat(barataYDeGaleria.attentionReasons())
                    .containsExactlyInAnyOrder(AttentionReason.PRICE_OUT_OF_RANGE, AttentionReason.GALLERY_UPLOAD);
        }

        @Test
        void deberia_conservar_la_marca_de_galeria_al_corregir_el_precio_criterio_18() {
            Listing corregida = CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(9_000)))
                    .marcarCargaDesdeGaleria(AHORA)
                    .cambiarPrecio(Money.dePesos(120_000), AHORA);

            assertThat(corregida.attentionReasons()).containsExactly(AttentionReason.GALLERY_UPLOAD);
        }

        @Test
        void deberia_impedir_marcar_una_publicacion_archivada() {
            Listing archivada = CatalogoDePrueba.publicada().archivar(AHORA);

            assertThatThrownBy(() -> archivada.marcarCargaDesdeGaleria(AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
        }

        @Test
        void deberia_topar_las_imagenes_de_referencia_RN_066() {
            Listing sellado = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true));
            for (int posicion = 0; posicion < Listing.MAXIMO_DE_REFERENCIAS; posicion++) {
                sellado = sellado.conImagen(CatalogoDePrueba.referencia(posicion), AHORA);
            }
            Listing conElMaximo = sellado;

            assertThatThrownBy(() -> conElMaximo.conImagen(CatalogoDePrueba.referencia(9), AHORA))
                    .isInstanceOf(ReferenceImageNotAllowedException.class)
                    .hasMessageContaining("maximo");
        }

        // RN-020 es un rango cerrado: los extremos estan dentro.
        @Test
        void deberia_aceptar_sin_marca_los_limites_exactos_de_RN_020() {
            assertThat(CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(10_000)))
                            .requiereAtencion())
                    .isFalse();
            assertThat(CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(20_000_000)))
                            .requiereAtencion())
                    .isFalse();
        }
    }

    @Nested
    class DespuesDePublicada {

        @Test
        void deberia_volver_a_revision_al_editar_contenido_RN_062() {
            Listing publicada = CatalogoDePrueba.publicada();
            Product otroTitulo = publicada.product();

            Listing editada = publicada.editarContenido(otroTitulo, AHORA);

            assertThat(editada.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
            assertThat(editada.esVisible()).isFalse();
        }

        @Test
        void deberia_seguir_visible_al_cambiar_solo_el_precio_RN_030() {
            Listing conOtroPrecio = CatalogoDePrueba.publicada().cambiarPrecio(Money.dePesos(150_000), AHORA);

            assertThat(conOtroPrecio.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(conOtroPrecio.esVisible()).isTrue();
            assertThat(conOtroPrecio.product().price()).isEqualTo(Money.dePesos(150_000));
        }

        @Test
        void deberia_seguir_visible_al_cambiar_las_dimensiones_de_envio_RN_062() {
            Listing conOtroEnvio = CatalogoDePrueba.publicada().cambiarEnvio(CatalogoDePrueba.envio(), AHORA);

            assertThat(conOtroEnvio.status()).isEqualTo(ListingStatus.PUBLISHED);
        }

        @Test
        void deberia_quedarse_en_borrador_al_editar_un_borrador() {
            Listing borrador = CatalogoDePrueba.borrador();

            Listing editado = borrador.editarContenido(borrador.product(), AHORA);

            assertThat(editado.status()).isEqualTo(ListingStatus.DRAFT);
        }

        @Test
        void deberia_dejar_de_verse_al_pausar_y_volver_sin_moderacion_criterio_29() {
            Listing pausada = CatalogoDePrueba.publicada().pausar(AHORA);
            assertThat(pausada.esVisible()).isFalse();

            Listing reanudada = pausada.reanudar(AHORA);
            assertThat(reanudada.status()).isEqualTo(ListingStatus.PUBLISHED);
        }

        @Test
        void deberia_impedir_cualquier_cambio_sobre_una_archivada_criterio_30() {
            Listing archivada = CatalogoDePrueba.publicada().archivar(AHORA);

            assertThatThrownBy(() -> archivada.pausar(AHORA)).isInstanceOf(InvalidListingTransitionException.class);
            assertThatThrownBy(() -> archivada.cambiarPrecio(Money.dePesos(50_000), AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
            assertThatThrownBy(() -> archivada.editarContenido(archivada.product(), AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
            // Tambien el envio. Sin esta linea, quitarle el exigirNoTerminal a
            // cambiarEnvio no rompia ninguna prueba.
            assertThatThrownBy(() -> archivada.cambiarEnvio(CatalogoDePrueba.envio(), AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
        }
    }

    @Nested
    class Precio {

        @Test
        void deberia_marcar_para_atencion_un_precio_por_debajo_del_rango_RN_020() {
            Listing barata = CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(9_999)));

            assertThat(barata.requiereAtencion()).isTrue();
            assertThat(barata.attentionReasons()).containsExactly(AttentionReason.PRICE_OUT_OF_RANGE);
        }

        @Test
        void deberia_marcar_para_atencion_un_precio_por_encima_del_rango_RN_020() {
            Listing cara = CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(20_000_001)));

            assertThat(cara.attentionReasons()).containsExactly(AttentionReason.PRICE_OUT_OF_RANGE);
        }

        // El rango es blando: fuera de el se publica igual y solo se marca.
        @Test
        void deberia_dejar_publicar_un_precio_fuera_de_rango_criterio_12() {
            Listing cara = CatalogoDePrueba.conTomas(
                            CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(25_000_000))))
                    .enviarARevision(AHORA)
                    .aprobar(MODERADOR, AHORA);

            assertThat(cara.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(cara.requiereAtencion()).isTrue();
        }

        @Test
        void deberia_no_marcar_nada_dentro_del_rango() {
            assertThat(CatalogoDePrueba.borrador().requiereAtencion()).isFalse();
        }

        @Test
        void deberia_quitar_la_marca_si_el_precio_vuelve_al_rango() {
            Listing corregida = CatalogoDePrueba.borradorDe(CatalogoDePrueba.camisaCon(Money.dePesos(9_000)))
                    .cambiarPrecio(Money.dePesos(120_000), AHORA);

            assertThat(corregida.requiereAtencion()).isFalse();
        }
    }

    @Nested
    class Tecnologia {

        @Test
        void deberia_exigir_solo_cuatro_tomas_a_un_producto_sellado_RN_065() {
            Listing sellado = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true));

            assertThat(sellado.tomasExigidas()).isEqualTo(4);
        }

        @Test
        void deberia_exigir_las_ocho_a_un_producto_de_tecnologia_no_sellado_criterio_38() {
            Listing abierto = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(false));

            assertThat(abierto.tomasExigidas()).isEqualTo(8);
        }

        @Test
        void deberia_publicar_un_sellado_con_sus_cuatro_canonicas_del_empaque_RN_065() {
            Listing sellado = CatalogoDePrueba.conTomas(CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true)))
                    .enviarARevision(AHORA);

            assertThat(sellado.status()).isEqualTo(ListingStatus.PENDING_REVIEW);
            assertThat(sellado.tomasDelVendedor()).hasSize(4);
        }

        @Test
        void deberia_admitir_imagenes_de_referencia_solo_si_esta_sellado_RN_066() {
            Listing sellado = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true));

            Listing conReferencia = sellado.conImagen(CatalogoDePrueba.referencia(0), AHORA);

            assertThat(conReferencia.imagenesDeReferencia()).hasSize(1);
        }

        @Test
        void deberia_rechazar_una_imagen_de_referencia_en_tecnologia_no_sellada_RN_066() {
            Listing abierto = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(false));

            assertThatThrownBy(() -> abierto.conImagen(CatalogoDePrueba.referencia(0), AHORA))
                    .isInstanceOf(ReferenceImageNotAllowedException.class)
                    .hasMessageContaining("no se declaro sellado");
        }

        @Test
        void deberia_rechazar_una_imagen_de_referencia_en_moda_RN_066() {
            Listing camisa = CatalogoDePrueba.borrador();

            assertThatThrownBy(() -> camisa.conImagen(CatalogoDePrueba.referencia(0), AHORA))
                    .isInstanceOf(ReferenceImageNotAllowedException.class)
                    .hasMessageContaining("no es tecnologia");
        }

        // Sin una foto real no hay prueba de que el producto exista.
        @Test
        void deberia_impedir_enviar_a_revision_solo_con_imagenes_de_referencia_criterio_40() {
            Listing soloReferencia = CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true))
                    .conImagen(CatalogoDePrueba.referencia(0), AHORA)
                    .conImagen(CatalogoDePrueba.referencia(1), AHORA)
                    .conImagen(CatalogoDePrueba.referencia(2), AHORA)
                    .conImagen(CatalogoDePrueba.referencia(3), AHORA);

            assertThatThrownBy(() -> soloReferencia.enviarARevision(AHORA))
                    .isInstanceOf(ShotsIncompleteException.class);
        }

        @Test
        void deberia_dejar_las_de_referencia_fuera_del_conteo_de_tomas_RN_066() {
            Listing sellado = CatalogoDePrueba.conTomas(CatalogoDePrueba.borradorDe(CatalogoDePrueba.celular(true)))
                    .conImagen(CatalogoDePrueba.referencia(0), AHORA);

            assertThat(sellado.tomasDelVendedor()).hasSize(4);
            assertThat(sellado.imagenesDeReferencia()).hasSize(1);
            assertThat(sellado.images()).hasSize(5);
        }
    }
}
