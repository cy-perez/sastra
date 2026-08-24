package co.sastra.catalog.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Con que escala se declara la talla. Lista cerrada de HU-007.
 *
 * <p>Una categoria admite <strong>varios</strong> y el vendedor elige uno: sin eje de
 * genero, unos jeans se venden en talla numerica y en pulgadas de cintura, y partir
 * la categoria en dos por eso seria meter el genero por la puerta de atras
 * (docs/producto/categorias.md).
 *
 * <p><strong>Los valores estan sin confirmar.</strong> HU-007 los anota como
 * pendientes de revisar con alguien que venda ropa en Colombia. Se implementan
 * porque un sistema de talla que no sabe que valores admite no valida nada;
 * corregirlos es cambiar esta lista y ninguna firma.
 */
public enum SizeSystem {
    ALPHA(List.of("XS", "S", "M", "L", "XL", "XXL")),

    NUMERIC_CO(numeros(4, 20, 2)),

    WAIST_INCHES(numeros(26, 46, 2)),

    FOOTWEAR_CO(numeros(33, 46, 1)),

    /** Un dispositivo no tiene talla, y un bolso tampoco. */
    ONE_SIZE(List.of("U"));

    private final Set<String> valores;

    SizeSystem(List<String> valores) {
        this.valores = new LinkedHashSet<>(valores);
    }

    public boolean admite(String valor) {
        return valores.contains(valor);
    }

    /** Copia: la lista de un enum no se toca desde fuera. */
    public Set<String> valores() {
        return new LinkedHashSet<>(valores);
    }

    private static List<String> numeros(int desde, int hasta, int paso) {
        return IntStream.iterate(desde, n -> n <= hasta, n -> n + paso)
                .mapToObj(Integer::toString)
                .toList();
    }
}
