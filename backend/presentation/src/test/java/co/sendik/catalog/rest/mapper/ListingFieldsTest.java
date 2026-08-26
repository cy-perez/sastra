package co.sendik.catalog.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.catalog.exception.IncompleteListingException;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.rest.dto.ProductRequest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Que todo campo que el dominio declare obligatorio tenga nombre en el contrato.
 *
 * <p><strong>No compara contra una lista escrita a mano.</strong> Le pide al dominio los
 * campos que faltan en un producto vacio —que son exactamente los que el criterio 6 puede
 * devolver— y comprueba que cada uno, traducido, es un campo real de
 * {@link ProductRequest}. Asi, el dia que alguien agregue un dato obligatorio al dominio
 * sin traducirlo, esta prueba lo dice; una lista escrita a mano se habria quedado vieja en
 * silencio.
 */
class ListingFieldsTest {

    @Test
    void todo_campo_obligatorio_del_dominio_deberia_existir_en_la_peticion() {
        List<String> faltantes = camposQueElDominioExige();

        assertThat(faltantes).isNotEmpty();
        assertThat(faltantes)
                .allSatisfy(campo -> assertThat(camposDeLaPeticion())
                        .as(
                                "el campo '%s' del dominio se traduce a '%s', que no existe en ProductRequest",
                                campo, ListingFields.enElContrato(campo))
                        .contains(ListingFields.enElContrato(campo)));
    }

    /** Ninguno sale en espanol: el contrato de la API va en ingles (CLAUDE.md). */
    @Test
    void ninguna_traduccion_deberia_dejar_el_nombre_del_dominio() {
        assertThat(ListingFields.enElContrato("titulo")).isEqualTo("title");
        assertThat(ListingFields.enElContrato("descripcion")).isEqualTo("description");
        assertThat(ListingFields.enElContrato("condicion")).isEqualTo("condition");
        assertThat(ListingFields.enElContrato("talla")).isEqualTo("size");
        assertThat(ListingFields.enElContrato("precio")).isEqualTo("price");
        assertThat(ListingFields.enElContrato("envio")).isEqualTo("shipping");
    }

    /**
     * Uno que no esta en la tabla sale tal cual y no revienta. Quedarse sin responder por
     * una entrada que falta en un mapa seria peor que devolver un nombre imperfecto.
     */
    @Test
    void deberia_devolver_tal_cual_lo_que_no_conoce() {
        assertThat(ListingFields.enElContrato("algo-que-nadie-declaro")).isEqualTo("algo-que-nadie-declaro");
    }

    private static List<String> camposQueElDominioExige() {
        Product vacio = new Product(
                ProductId.nuevo(),
                new SellerId(UUID.randomUUID()),
                CategoryId.nuevo(),
                null,
                null,
                null,
                null,
                null,
                new Measurements(Map.of()),
                null,
                null,
                null,
                null,
                null);

        Category camisas = new Category(
                CategoryId.nuevo(),
                "camisas-y-blusas",
                CategoryId.nuevo(),
                Set.of(SizeSystem.ALPHA),
                MeasurementGroup.TOP,
                true,
                true);

        try {
            vacio.exigirCompletoPara(camisas);
        } catch (IncompleteListingException fallo) {
            return fallo.faltantes();
        }
        throw new AssertionError("Un producto vacio tiene que estar incompleto");
    }

    private static List<String> camposDeLaPeticion() {
        return Arrays.stream(ProductRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
