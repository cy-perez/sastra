package co.sastra.catalog.model;

/**
 * Meses de garantia que el dispositivo trae de fabrica. RN-067.
 *
 * <p><strong>Responde el vendedor, no Sastra.</strong> Sastra ofrece el Respaldo y
 * nada mas, y las dos palabras no se mezclan (glosario). La garantia legal de la Ley
 * 1480 de 2011 existe ademas de esto y no la sustituye ninguna regla del proyecto.
 *
 * <p>Cero es un valor valido y distinto de no declarar nada: significa que el
 * vendedor dice que no trae garantia.
 */
public record WarrantyMonths(int value) {

    /** Cinco anos. Por encima, alguien confundio meses con dias. */
    private static final int MAXIMO = 60;

    public WarrantyMonths {
        if (value < 0) {
            throw new IllegalArgumentException("La garantia no puede ser negativa: " + value);
        }
        if (value > MAXIMO) {
            throw new IllegalArgumentException("La garantia supera los " + MAXIMO + " meses: " + value);
        }
    }

    @Override
    public String toString() {
        return value + " meses";
    }
}
