package co.sastra.catalog.model;

import co.sastra.shared.money.Money;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lo que describe al producto: todo menos su estado de moderacion.
 *
 * <p>Se separa de {@link Listing} porque el ciclo de moderacion no tiene por que
 * ensuciar los datos de la prenda (docs/arquitectura/modelo-datos.md). Son dos tablas
 * y un solo agregado: las invariantes que importan —"ocho tomas para publicar"— no se
 * pueden comprobar mirando una sola.
 *
 * <p>Inmutable. Cada cambio devuelve una instancia nueva, y quien la guarda decide si
 * eso es un {@code UPDATE}.
 *
 * <p><strong>Sellado y garantia solo existen en tecnologia.</strong> Que un producto
 * sea tecnologia se reconoce por el grupo de medida {@code DEVICE} de su categoria, y
 * no por el nombre de la familia: lo que habilita declarar un empaque de fabrica y una
 * garantia del fabricante es que la cosa sea un aparato, no como se llame la rama del
 * arbol donde cuelga.
 */
public record Product(
        ProductId id,
        SellerId sellerId,
        CategoryId categoryId,
        Title title,
        Description description,
        @Nullable Brand brand,
        Condition condition,
        Size size,
        Measurements measurements,
        Color color,
        Money price,
        ShippingDimensions shipping,
        @Nullable Boolean isSealed,
        @Nullable WarrantyMonths warranty) {

    public Product {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(sellerId, "El vendedor es obligatorio");
        Objects.requireNonNull(categoryId, "La categoria es obligatoria");
        Objects.requireNonNull(title, "El titulo es obligatorio");
        Objects.requireNonNull(description, "La descripcion es obligatoria");
        Objects.requireNonNull(condition, "La condicion es obligatoria");
        Objects.requireNonNull(size, "La talla es obligatoria");
        Objects.requireNonNull(measurements, "Las medidas son obligatorias");
        Objects.requireNonNull(color, "El color es obligatorio");
        Objects.requireNonNull(price, "El precio es obligatorio");
        Objects.requireNonNull(shipping, "Las dimensiones de envio son obligatorias");

        if (price.esCero()) {
            throw new IllegalArgumentException("El precio no puede ser cero");
        }
        if (warranty != null && isSealed == null) {
            throw new IllegalArgumentException("La garantia del fabricante solo existe en tecnologia (RN-067)");
        }
    }

    /**
     * Construye el producto comprobando lo que solo se puede comprobar con la
     * categoria delante: la condicion admisible (RN-064), la escala de talla y si
     * admite declararse sellado.
     */
    public static Product crear(
            ProductId id,
            SellerId sellerId,
            Category categoria,
            Title title,
            Description description,
            @Nullable Brand brand,
            Condition condition,
            Size size,
            Measurements measurements,
            Color color,
            Money price,
            ShippingDimensions shipping,
            @Nullable Boolean isSealed,
            @Nullable WarrantyMonths warranty) {

        Objects.requireNonNull(categoria, "La categoria es obligatoria");

        if (!categoria.admitePublicaciones()) {
            throw new IllegalArgumentException("No se publica en esa categoria: " + categoria.slug());
        }
        categoria.exigirCondicionAdmisible(condition);

        if (!categoria.sizeSystems().contains(size.system())) {
            throw new IllegalArgumentException(
                    "La categoria " + categoria.slug() + " no admite el sistema de talla " + size.system());
        }
        if ((isSealed != null || warranty != null) && !esTecnologia(categoria)) {
            throw new IllegalArgumentException("Sellado y garantia solo existen en tecnologia (RN-065, RN-067)");
        }

        return new Product(
                id,
                sellerId,
                categoria.id(),
                title,
                description,
                brand,
                condition,
                size,
                measurements,
                color,
                price,
                shipping,
                isSealed,
                warranty);
    }

    private static boolean esTecnologia(Category categoria) {
        return categoria.grupoDeMedida() == MeasurementGroup.DEVICE;
    }

    /** RN-065: solo lo declarado sellado admite imagenes de referencia y baja a cuatro tomas. */
    public boolean estaSellado() {
        return Boolean.TRUE.equals(isSealed);
    }

    public boolean esTecnologia() {
        return isSealed != null;
    }

    public Product conPrecio(Money nuevo) {
        return new Product(
                id,
                sellerId,
                categoryId,
                title,
                description,
                brand,
                condition,
                size,
                measurements,
                color,
                Objects.requireNonNull(nuevo, "El precio es obligatorio"),
                shipping,
                isSealed,
                warranty);
    }

    public Product conEnvio(ShippingDimensions nuevo) {
        return new Product(
                id,
                sellerId,
                categoryId,
                title,
                description,
                brand,
                condition,
                size,
                measurements,
                color,
                price,
                Objects.requireNonNull(nuevo, "Las dimensiones son obligatorias"),
                isSealed,
                warranty);
    }

    /**
     * Comprueba que el producto esta listo para revision. RN-021.
     *
     * <p>Las medidas se validan aqui y no al construir, porque un borrador se guarda a
     * medias: el vendedor mide con la prenda en la mano y vuelve.
     */
    public void exigirCompletoPara(Category categoria) {
        Objects.requireNonNull(categoria, "La categoria es obligatoria");
        measurements.exigirCompletasPara(categoria.grupoDeMedida());
    }
}
