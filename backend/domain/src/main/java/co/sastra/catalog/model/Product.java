package co.sastra.catalog.model;

import co.sastra.catalog.exception.IncompleteListingException;
import co.sastra.shared.money.Money;
import java.util.ArrayList;
import java.util.List;
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
        @Nullable Title title,
        @Nullable Description description,
        @Nullable Brand brand,
        @Nullable Condition condition,
        @Nullable Size size,
        Measurements measurements,
        @Nullable Color color,
        @Nullable Money price,
        @Nullable ShippingDimensions shipping,
        @Nullable Boolean isSealed,
        @Nullable WarrantyMonths warranty) {

    public Product {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(sellerId, "El vendedor es obligatorio");
        Objects.requireNonNull(categoryId, "La categoria es obligatoria");
        Objects.requireNonNull(measurements, "Las medidas son obligatorias");

        if (price != null && price.esCero()) {
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
            @Nullable Title title,
            @Nullable Description description,
            @Nullable Brand brand,
            @Nullable Condition condition,
            @Nullable Size size,
            Measurements measurements,
            @Nullable Color color,
            @Nullable Money price,
            @Nullable ShippingDimensions shipping,
            @Nullable Boolean isSealed,
            @Nullable WarrantyMonths warranty) {

        Objects.requireNonNull(categoria, "La categoria es obligatoria");

        if (!categoria.admitePublicaciones()) {
            throw new IllegalArgumentException("No se publica en esa categoria: " + categoria.slug());
        }
        if (condition != null) {
            categoria.exigirCondicionAdmisible(condition);
        }
        if (size != null && !categoria.sizeSystems().contains(size.system())) {
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
        Objects.requireNonNull(nuevo, "El precio es obligatorio");
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
                nuevo,
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
     * Comprueba que el producto esta listo para revision. Criterios 6 y 10, RN-021.
     *
     * <p><strong>Nada de esto se exige al construir.</strong> El criterio 5 pide que el
     * borrador se guarde con lo que lleve y se retome donde iba: el vendedor mide con la
     * prenda en la mano, se va y vuelve. Es el mismo patron que ya usa
     * {@code SellerVerification} con el documento, la selfie y la cuenta bancaria.
     *
     * <p>Reune todo lo que falta en un solo error en vez de fallar en el primero, porque
     * el criterio 6 pide «una entrada en {@code errors} por cada campo que falta» y con
     * fallo temprano el vendedor los descubre de uno en uno.
     *
     * @throws IncompleteListingException si falta algo
     * @throws MeasurementsIncompleteException si faltan medidas del grupo
     */
    public void exigirCompletoPara(Category categoria) {
        Objects.requireNonNull(categoria, "La categoria es obligatoria");

        List<String> faltantes = new ArrayList<>();
        if (title == null) {
            faltantes.add("titulo");
        }
        if (description == null) {
            faltantes.add("descripcion");
        }
        if (condition == null) {
            faltantes.add("condicion");
        }
        if (size == null) {
            faltantes.add("talla");
        }
        if (color == null) {
            faltantes.add("color");
        }
        if (price == null) {
            faltantes.add("precio");
        }
        if (shipping == null) {
            faltantes.add("envio");
        }

        if (!faltantes.isEmpty()) {
            throw new IncompleteListingException(faltantes);
        }
        measurements.exigirCompletasPara(categoria.grupoDeMedida());
    }

    /** Si se puede enviar a revision sin que falte nada. */
    public boolean estaCompletoPara(Category categoria) {
        return title != null
                && description != null
                && condition != null
                && size != null
                && color != null
                && price != null
                && shipping != null
                && measurements.estanCompletasPara(categoria.grupoDeMedida());
    }
}
