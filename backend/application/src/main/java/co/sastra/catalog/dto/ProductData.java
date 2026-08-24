package co.sastra.catalog.dto;

import co.sastra.catalog.model.Brand;
import co.sastra.catalog.model.CategoryId;
import co.sastra.catalog.model.Color;
import co.sastra.catalog.model.Condition;
import co.sastra.catalog.model.Description;
import co.sastra.catalog.model.Measurements;
import co.sastra.catalog.model.ShippingDimensions;
import co.sastra.catalog.model.Size;
import co.sastra.catalog.model.Title;
import co.sastra.catalog.model.WarrantyMonths;
import co.sastra.shared.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * Los datos del producto tal como los manda el vendedor.
 *
 * <p>Se comparte entre crear y editar porque son los mismos campos; lo que cambia es
 * que editar exige una publicacion existente y decide si vuelve a moderacion (RN-062).
 *
 * <p>Lleva objetos de valor del dominio y no cadenas sueltas: para cuando un comando
 * llega hasta aqui, el borde ya tradujo y lo que no era un titulo valido no paso.
 */
public record ProductData(
        CategoryId categoria,
        Title titulo,
        Description descripcion,
        @Nullable Brand marca,
        Condition condicion,
        Size talla,
        Measurements medidas,
        Color color,
        Money precio,
        ShippingDimensions envio,
        @Nullable Boolean sellado,
        @Nullable WarrantyMonths garantia) {}
