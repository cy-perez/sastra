package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.exception.InvalidListingTransitionException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ObjetosDeValorTest {

    @Nested
    class Identificadores {

        @Test
        void deberia_construirse_desde_texto_y_volver_a_el() {
            String texto = UUID.randomUUID().toString();

            assertThat(ListingId.de(texto)).hasToString(texto);
            assertThat(ProductId.de(texto)).hasToString(texto);
            assertThat(CategoryId.de(texto)).hasToString(texto);
            assertThat(ProductImageId.de(texto)).hasToString(texto);
            assertThat(SellerId.de(texto)).hasToString(texto);
            assertThat(ModeratorId.de(texto)).hasToString(texto);
        }

        @Test
        void deberia_rechazar_un_texto_que_no_es_uuid() {
            assertThatThrownBy(() -> ListingId.de("no-soy-un-uuid"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no es un UUID valido");
            assertThatThrownBy(() -> SellerId.de("tampoco")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ModeratorId.de("ni yo")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> CategoryId.de("nada")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductId.de("nada")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ProductImageId.de("nada")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_generar_identificadores_distintos() {
            assertThat(ListingId.nuevo()).isNotEqualTo(ListingId.nuevo());
            assertThat(ProductId.nuevo()).isNotEqualTo(ProductId.nuevo());
            assertThat(CategoryId.nuevo()).isNotEqualTo(CategoryId.nuevo());
            assertThat(ProductImageId.nuevo()).isNotEqualTo(ProductImageId.nuevo());
        }
    }

    @Nested
    class Textos {

        @Test
        void deberia_colapsar_espacios_y_quitar_control_del_titulo() {
            assertThat(new Title("Camisa   de\tlino\nblanca").value()).isEqualTo("Camisa de lino blanca");
        }

        @Test
        void deberia_rechazar_un_titulo_demasiado_corto_o_demasiado_largo() {
            assertThatThrownBy(() -> new Title("Hola")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Title("x".repeat(121))).isInstanceOf(IllegalArgumentException.class);
        }

        // Los saltos de linea si se conservan: una descripcion larga sin parrafos no se lee.
        @Test
        void deberia_conservar_los_saltos_de_linea_de_la_descripcion() {
            assertThat(new Description("Primera linea.\nSegunda linea.").value())
                    .isEqualTo("Primera linea.\nSegunda linea.");
        }

        @Test
        void deberia_rechazar_una_descripcion_vacia_o_demasiado_larga() {
            assertThatThrownBy(() -> new Description("   ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Description("x".repeat(4001))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_rechazar_una_marca_vacia_porque_para_no_tenerla_se_deja_sin_poner() {
            assertThatThrownBy(() -> new Brand("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("se deja sin poner");
            assertThatThrownBy(() -> new Brand("x".repeat(61))).isInstanceOf(IllegalArgumentException.class);
            assertThat(new Brand("  Levi   Strauss ")).hasToString("Levi Strauss");
        }
    }

    @Nested
    class Envio {

        @Test
        void deberia_rechazar_un_peso_no_positivo_o_desmedido() {
            assertThatThrownBy(() -> new ShippingDimensions(0, uno(), uno(), uno()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("peso");
            assertThatThrownBy(() -> new ShippingDimensions(50_001, uno(), uno(), uno()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("supera");
        }

        @Test
        void deberia_rechazar_un_lado_no_positivo() {
            assertThatThrownBy(() -> new ShippingDimensions(500, new BigDecimal("0"), uno(), uno()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("largo");
            assertThatThrownBy(() -> new ShippingDimensions(500, uno(), new BigDecimal("-1"), uno()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ancho");
        }

        @Test
        void deberia_rechazar_mas_de_un_decimal_y_un_lado_no_plausible() {
            assertThatThrownBy(() -> new ShippingDimensions(500, uno(), uno(), new BigDecimal("10.55")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("un decimal");
            assertThatThrownBy(() -> new ShippingDimensions(500, new BigDecimal("301"), uno(), uno()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("plausible");
        }

        private static BigDecimal uno() {
            return new BigDecimal("10.0");
        }
    }

    @Nested
    class Garantia {

        @Test
        void deberia_admitir_cero_meses_que_es_distinto_de_no_declarar_nada_RN_067() {
            assertThat(new WarrantyMonths(0).value()).isZero();
            assertThat(new WarrantyMonths(12)).hasToString("12 meses");
        }

        @Test
        void deberia_rechazar_una_garantia_negativa_o_absurda() {
            assertThatThrownBy(() -> new WarrantyMonths(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new WarrantyMonths(61))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("supera");
        }
    }

    @Nested
    class MasDeLaPublicacion {

        @Test
        void deberia_quitar_una_imagen_por_su_identificador() {
            ProductImage toma = CatalogoDePrueba.toma(0);
            Listing con = CatalogoDePrueba.borrador().conImagen(toma, AHORA);

            assertThat(con.sinImagen(toma.id(), AHORA).images()).isEmpty();
        }

        @Test
        void deberia_marcar_la_carga_desde_galeria_criterio_18() {
            Listing marcada = CatalogoDePrueba.borrador().marcarCargaDesdeGaleria(AHORA);

            assertThat(marcada.requiereAtencion()).isTrue();
            assertThat(marcada.attentionReasons()).containsExactly(AttentionReason.GALLERY_UPLOAD);
        }

        // Una publicacion enviada a revision no se toca: el moderador la esta mirando.
        @Test
        void deberia_impedir_tocar_las_imagenes_de_algo_que_espera_revision() {
            Listing enRevision = CatalogoDePrueba.borradorCompleto().enviarARevision(AHORA);

            assertThatThrownBy(() -> enRevision.conImagen(CatalogoDePrueba.toma(0), AHORA))
                    .isInstanceOf(InvalidListingTransitionException.class);
        }

        @Test
        void deberia_reconstruir_lo_guardado_sin_revalidar() {
            Listing original = CatalogoDePrueba.publicada();

            Listing reconstruida = Listing.existente(
                    original.id(),
                    original.product(),
                    original.status(),
                    original.images(),
                    original.publishedAt(),
                    original.moderatedBy(),
                    original.moderatedAt(),
                    original.rejectionReason(),
                    original.rejectionNote(),
                    original.attentionReasons(),
                    original.version(),
                    original.createdAt(),
                    original.updatedAt());

            assertThat(reconstruida.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(reconstruida.sellerId()).isEqualTo(original.sellerId());
            assertThat(reconstruida.images()).hasSize(8);
            assertThat(reconstruida.updatedAt()).isEqualTo(original.updatedAt());
        }

        @Test
        void deberia_registrar_las_tres_acciones_de_moderacion_que_dejan_rastro() {
            assertThat(ModerationAction.values())
                    .containsExactly(ModerationAction.APPROVED, ModerationAction.REJECTED, ModerationAction.ARCHIVED);
            assertThat(ModerationAction.valueOf("APPROVED")).isEqualTo(ModerationAction.APPROVED);
        }
    }
}
