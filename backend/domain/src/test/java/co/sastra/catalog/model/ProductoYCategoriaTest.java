package co.sastra.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.catalog.exception.ConditionNotAllowedException;
import co.sastra.catalog.exception.MeasurementsIncompleteException;
import co.sastra.shared.money.Money;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductoYCategoriaTest {

    @Nested
    class Categorias {

        @Test
        void deberia_rechazar_lo_usado_en_tecnologia_RN_064() {
            Category celulares = CatalogoDePrueba.celulares();

            assertThatThrownBy(() -> celulares.exigirCondicionAdmisible(Condition.GOOD))
                    .isInstanceOf(ConditionNotAllowedException.class)
                    .hasMessageContaining("RN-064");
        }

        @Test
        void deberia_admitir_lo_nuevo_en_tecnologia_RN_064() {
            CatalogoDePrueba.celulares().exigirCondicionAdmisible(Condition.NEW);

            assertThat(CatalogoDePrueba.celulares().condicionesAdmisibles()).containsExactly(Condition.NEW);
        }

        @Test
        void deberia_admitir_las_cuatro_condiciones_en_moda_RN_064() {
            assertThat(CatalogoDePrueba.camisas().condicionesAdmisibles())
                    .containsExactlyInAnyOrder(Condition.values());
        }

        @Test
        void deberia_impedir_que_una_familia_declare_talla_o_medidas() {
            assertThatThrownBy(() -> new Category(
                            CategoryId.nuevo(),
                            "tops",
                            null,
                            Set.of(SizeSystem.ALPHA),
                            MeasurementGroup.TOP,
                            true,
                            true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("familia");
        }

        @Test
        void deberia_exigir_a_una_hoja_su_grupo_de_medida_y_al_menos_una_talla() {
            assertThatThrownBy(() -> new Category(
                            CategoryId.nuevo(), "camisetas", CategoryId.nuevo(), Set.of(), null, true, true))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("necesita grupo de medida");
        }

        @Test
        void deberia_impedir_publicar_en_una_familia() {
            assertThat(CatalogoDePrueba.familia().admitePublicaciones()).isFalse();
            assertThat(CatalogoDePrueba.familia().esFamilia()).isTrue();
        }

        @Test
        void deberia_impedir_publicar_en_una_categoria_retirada() {
            Category retirada = new Category(
                    CategoryId.nuevo(),
                    "gafas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ONE_SIZE),
                    MeasurementGroup.ACCESSORY_FLAT,
                    true,
                    false);

            assertThat(retirada.admitePublicaciones()).isFalse();
        }

        @Test
        void deberia_negarse_a_dar_grupo_de_medida_una_familia() {
            assertThatThrownBy(() -> CatalogoDePrueba.familia().grupoDeMedida())
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Productos {

        @Test
        void deberia_rechazar_un_celular_usado_RN_064() {
            Category celulares = CatalogoDePrueba.celulares();

            assertThatThrownBy(() -> Product.crear(
                            ProductId.nuevo(),
                            new SellerId(UUID.randomUUID()),
                            celulares,
                            new Title("Telefono usado en buen estado"),
                            new Description("Con marcas de uso."),
                            null,
                            Condition.GOOD,
                            Size.unica(),
                            CatalogoDePrueba.medidasDe(MeasurementGroup.DEVICE),
                            Color.BLACK,
                            Money.dePesos(500_000),
                            CatalogoDePrueba.envio(),
                            false,
                            null))
                    .isInstanceOf(ConditionNotAllowedException.class);
        }

        @Test
        void deberia_rechazar_una_talla_de_un_sistema_que_la_categoria_no_admite() {
            Category camisas = CatalogoDePrueba.camisas();

            assertThatThrownBy(() -> Product.crear(
                            ProductId.nuevo(),
                            new SellerId(UUID.randomUUID()),
                            camisas,
                            new Title("Camisa de lino color hueso"),
                            new Description("Sin detalles."),
                            null,
                            Condition.NEW,
                            new Size(SizeSystem.FOOTWEAR_CO, "40"),
                            CatalogoDePrueba.medidasDe(MeasurementGroup.TOP),
                            Color.BEIGE,
                            Money.dePesos(100_000),
                            CatalogoDePrueba.envio(),
                            null,
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no admite el sistema de talla");
        }

        @Test
        void deberia_rechazar_sellado_o_garantia_en_moda_criterio_36() {
            Category camisas = CatalogoDePrueba.camisas();

            assertThatThrownBy(() -> Product.crear(
                            ProductId.nuevo(),
                            new SellerId(UUID.randomUUID()),
                            camisas,
                            new Title("Camisa de lino color hueso"),
                            new Description("Sin detalles."),
                            null,
                            Condition.NEW,
                            new Size(SizeSystem.ALPHA, "M"),
                            CatalogoDePrueba.medidasDe(MeasurementGroup.TOP),
                            Color.BEIGE,
                            Money.dePesos(100_000),
                            CatalogoDePrueba.envio(),
                            true,
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("solo existen en tecnologia");
        }

        @Test
        void deberia_impedir_publicar_en_una_familia() {
            Category familia = CatalogoDePrueba.familia();

            assertThatThrownBy(() -> Product.crear(
                            ProductId.nuevo(),
                            new SellerId(UUID.randomUUID()),
                            familia,
                            new Title("Algo cualquiera"),
                            new Description("x"),
                            null,
                            Condition.NEW,
                            new Size(SizeSystem.ALPHA, "M"),
                            Measurements.vacias(),
                            Color.BEIGE,
                            Money.dePesos(100_000),
                            CatalogoDePrueba.envio(),
                            null,
                            null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No se publica en esa categoria");
        }

        @Test
        void deberia_rechazar_un_precio_de_cero() {
            assertThatThrownBy(() -> CatalogoDePrueba.camisaCon(Money.dePesos(0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cero");
        }

        @Test
        void deberia_reconocer_lo_sellado_y_lo_que_es_tecnologia_RN_065() {
            assertThat(CatalogoDePrueba.celular(true).estaSellado()).isTrue();
            assertThat(CatalogoDePrueba.celular(false).estaSellado()).isFalse();
            assertThat(CatalogoDePrueba.celular(false).esTecnologia()).isTrue();
            assertThat(CatalogoDePrueba.camisa().esTecnologia()).isFalse();
        }

        @Test
        void deberia_exigir_las_medidas_del_grupo_al_enviar_a_revision_RN_021() {
            Category camisas = CatalogoDePrueba.camisas();
            Product sinMedidas = Product.crear(
                    ProductId.nuevo(),
                    new SellerId(UUID.randomUUID()),
                    camisas,
                    new Title("Camisa de lino color hueso"),
                    new Description("Sin detalles."),
                    null,
                    Condition.NEW,
                    new Size(SizeSystem.ALPHA, "M"),
                    Measurements.vacias(),
                    Color.BEIGE,
                    Money.dePesos(100_000),
                    CatalogoDePrueba.envio(),
                    null,
                    null);

            assertThatThrownBy(() -> sinMedidas.exigirCompletoPara(camisas))
                    .isInstanceOf(MeasurementsIncompleteException.class);
        }
    }

    @Nested
    class Medidas {

        @Test
        void deberia_nombrar_las_que_faltan_para_que_el_borde_las_marque_por_campo() {
            Measurements soloPecho = new Measurements(Map.of(MeasurementKind.CHEST, new BigDecimal("52")));

            assertThatThrownBy(() -> soloPecho.exigirCompletasPara(MeasurementGroup.TOP))
                    .isInstanceOf(MeasurementsIncompleteException.class)
                    .hasMessageContaining("SHOULDERS");
        }

        @Test
        void deberia_aceptar_medidas_que_sobran_mientras_no_falte_ninguna() {
            Measurements conSobrante = new Measurements(Map.of(
                    MeasurementKind.INSOLE, new BigDecimal("26.5"),
                    MeasurementKind.CHEST, new BigDecimal("50")));

            assertThat(conSobrante.estanCompletasPara(MeasurementGroup.FOOTWEAR))
                    .isTrue();
        }

        @Test
        void deberia_rechazar_una_medida_negativa_o_cero() {
            assertThatThrownBy(() -> new Measurements(Map.of(MeasurementKind.CHEST, new BigDecimal("0"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positiva");
        }

        @Test
        void deberia_rechazar_mas_de_un_decimal() {
            assertThatThrownBy(() -> new Measurements(Map.of(MeasurementKind.CHEST, new BigDecimal("52.25"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("un decimal");
        }

        @Test
        void deberia_rechazar_una_medida_que_no_es_plausible() {
            assertThatThrownBy(() -> new Measurements(Map.of(MeasurementKind.LENGTH, new BigDecimal("4000"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("plausible");
        }

        @Test
        void deberia_dar_a_device_y_a_accesorio_con_volumen_las_mismas_medidas_y_seguir_siendo_grupos_distintos() {
            assertThat(MeasurementGroup.DEVICE.obligatorias())
                    .isEqualTo(MeasurementGroup.ACCESSORY_VOLUME.obligatorias());
            assertThat(MeasurementGroup.DEVICE).isNotEqualTo(MeasurementGroup.ACCESSORY_VOLUME);
        }
    }

    @Nested
    class Tallas {

        @Test
        void deberia_rechazar_una_talla_que_no_existe_en_su_sistema() {
            assertThatThrownBy(() -> new Size(SizeSystem.ALPHA, "XXXL"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("no existe en el sistema");
        }

        @Test
        void deberia_normalizar_a_mayusculas() {
            assertThat(new Size(SizeSystem.ALPHA, " m ").value()).isEqualTo("M");
        }

        @Test
        void deberia_admitir_las_dos_escalas_que_los_jeans_necesitan() {
            assertThat(SizeSystem.WAIST_INCHES.admite("32")).isTrue();
            assertThat(SizeSystem.NUMERIC_CO.admite("10")).isTrue();
            assertThat(SizeSystem.WAIST_INCHES.admite("33")).isFalse();
        }

        @Test
        void deberia_dejar_la_talla_unica_en_un_solo_valor() {
            assertThat(Size.unica().system()).isEqualTo(SizeSystem.ONE_SIZE);
            assertThat(SizeSystem.ONE_SIZE.valores()).containsExactly("U");
        }

        @Test
        void deberia_entregar_los_valores_sin_dejar_modificar_el_sistema() {
            SizeSystem.ALPHA.valores().clear();

            assertThat(SizeSystem.ALPHA.valores()).contains("XS", "XXL");
        }
    }

    @Nested
    class Imagenes {

        @Test
        void deberia_derivar_el_angulo_de_la_posicion_RN_017() {
            assertThat(CatalogoDePrueba.toma(0).angleDegrees()).isZero();
            assertThat(CatalogoDePrueba.toma(2).angleDegrees()).isEqualTo(90);
            assertThat(CatalogoDePrueba.toma(6).angleDegrees()).isEqualTo(270);
        }

        @Test
        void deberia_reconocer_como_canonicas_solo_las_de_0_90_180_y_270_RN_016() {
            assertThat(CatalogoDePrueba.toma(0).esCanonica()).isTrue();
            assertThat(CatalogoDePrueba.toma(1).esCanonica()).isFalse();
            assertThat(CatalogoDePrueba.toma(4).esCanonica()).isTrue();
        }

        @Test
        void deberia_rechazar_una_toma_fuera_de_la_secuencia_de_ocho() {
            assertThatThrownBy(() -> CatalogoDePrueba.toma(8))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("va de 0 a 7");
        }

        @Test
        void deberia_dejar_una_imagen_de_referencia_sin_angulo_y_sin_ser_canonica_RN_066() {
            ProductImage referencia = CatalogoDePrueba.referencia(0);

            assertThat(referencia.angleDegrees()).isNull();
            assertThat(referencia.esCanonica()).isFalse();
            assertThat(referencia.esTomaDelVendedor()).isFalse();
        }
    }
}
