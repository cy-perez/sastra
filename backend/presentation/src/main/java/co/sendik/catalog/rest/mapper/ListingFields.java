package co.sendik.catalog.rest.mapper;

import java.util.Map;

/**
 * Del nombre que usa el dominio al nombre que tiene el campo en la peticion.
 *
 * <p><strong>El formulario marca el campo que falta por su nombre, asi que tiene que ser
 * el suyo.</strong> {@code Product.exigirCompletoPara} arma su lista con nombres en
 * espanol —{@code titulo}, {@code precio}— porque el dominio se nombra en espanol; el
 * cuerpo de la peticion es {@code ProductRequest} y sus campos son {@code title} y
 * {@code price}. Devolver el nombre del dominio en {@code errors} deja al formulario
 * buscando un campo que no existe, que es justo lo que el criterio 6 quiere evitar.
 *
 * <p>Es ademas la regla de idioma de {@code CLAUDE.md}: el dominio se nombra en espanol y
 * la API en ingles. La traduccion vive en el borde, como todas las demas.
 *
 * <p>Un nombre que no este en la tabla sale tal cual. No se lanza: quedarse sin responder
 * porque falta una entrada en un mapa seria peor que devolver un nombre imperfecto, y el
 * caso lo cubre la prueba que recorre los siete.
 */
public final class ListingFields {

    private static final Map<String, String> NOMBRES = Map.of(
            "titulo", "title",
            "descripcion", "description",
            "condicion", "condition",
            "talla", "size",
            "color", "color",
            "precio", "price",
            "envio", "shipping");

    private ListingFields() {}

    public static String enElContrato(String campoDelDominio) {
        return NOMBRES.getOrDefault(campoDelDominio, campoDelDominio);
    }
}
