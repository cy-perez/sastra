package co.sendik.catalog.model;

/**
 * Lista cerrada, porque el color es filtro de catalogo y en texto libre no filtra.
 *
 * <p>{@link #MULTICOLOR} cubre estampados y combinaciones. No se agrega "estampado"
 * aparte: es un patron, no un color, y mezclarlos rompe el filtro.
 */
public enum Color {
    BLACK,
    WHITE,
    GRAY,
    BEIGE,
    BROWN,
    RED,
    PINK,
    ORANGE,
    YELLOW,
    GREEN,
    BLUE,
    PURPLE,
    GOLD,
    SILVER,
    MULTICOLOR
}
