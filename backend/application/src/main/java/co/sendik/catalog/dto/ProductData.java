package co.sendik.catalog.dto;

import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.model.WarrantyMonths;
import co.sendik.shared.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * Los datos del producto tal como los manda el vendedor.
 *
 * <p>Se comparte entre crear y editar porque son los mismos campos; lo que cambia es
 * que editar exige una publicacion existente y decide si vuelve a moderacion (RN-062).
 *
 * <p>Lleva objetos de valor del dominio y no cadenas sueltas: para cuando un comando
 * llega hasta aqui, el borde ya tradujo y lo que no era un titulo valido no paso.
 *
 * <p><strong>Casi todo es opcional, y eso no es descuido.</strong> El criterio 5 dice que
 * un borrador se guarda a medias y que salir y volver retoma donde iba, asi que un
 * comando de creacion o de edicion llega con lo que el vendedor lleva escrito y no con
 * todo. Lo obligatorio se exige al enviar a revision, que es cuando el dominio lo
 * comprueba con la categoria delante ({@code Product.exigirCompletoPara}).
 *
 * <p>La categoria y las medidas son la excepcion porque {@code Product} las exige no
 * nulas: sin categoria no se sabe que condiciones ni que tallas admite la publicacion, y
 * las medidas son un mapa que vacio ya significa "ninguna todavia".
 */
public record ProductData(
        CategoryId categoria,
        @Nullable Title titulo,
        @Nullable Description descripcion,
        @Nullable Brand marca,
        @Nullable Condition condicion,
        @Nullable Size talla,
        Measurements medidas,
        @Nullable Color color,
        @Nullable Money precio,
        @Nullable ShippingDimensions envio,
        @Nullable Boolean sellado,
        @Nullable WarrantyMonths garantia) {}
