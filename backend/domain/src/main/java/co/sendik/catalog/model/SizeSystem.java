package co.sendik.catalog.model;

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

    /**
     * Talla numerica colombiana. Va hasta 22 y no hasta 20: las guias de talla del
     * mercado colombiano llegan a 18 en su rango corriente y las tallas grandes siguen
     * en 20 y 22, que se venden y hoy no se podrian publicar.
     */
    NUMERIC_CO(numeros(4, 22, 2)),

    /**
     * Cintura en pulgadas, <strong>de uno en uno</strong>.
     *
     * <p>Iba de dos en dos y dejaba fuera las impares. El 33 existe y se vende: es una
     * talla corriente de jean de hombre en Colombia, y con el paso de dos quien la tuviera
     * no podia publicarla ni con la talla de al lado, porque la lista es cerrada.
     */
    WAIST_INCHES(numeros(26, 44, 1)),

    /**
     * Calzado, en la numeracion colombiana, que coincide con la europea.
     *
     * <p>El rango corriente del mercado es 35 a 40 en calzado de mujer y 38 a 43 en el de
     * hombre. Este va de 34 a 45 <strong>a proposito</strong>: el catalogo no tiene eje de
     * genero, asi que una sola lista cubre los dos, y los extremos existen aunque se
     * vendan poco. Por debajo de 34 ya es calzado infantil, que no esta en el arbol.
     */
    FOOTWEAR_CO(numeros(34, 45, 1)),

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
