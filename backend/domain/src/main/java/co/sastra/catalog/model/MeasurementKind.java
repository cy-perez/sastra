package co.sastra.catalog.model;

/**
 * Cada medida que un vendedor puede tener que declarar, en centimetros.
 *
 * <p>Son pocas y se reparten entre los grupos de {@link MeasurementGroup}: una camisa
 * pide pecho, largo, hombros y manga; un zapato pide plantilla. Nombrarlas una a una
 * es lo que permite que el dominio compruebe que estan todas, en vez de aceptar un
 * mapa con las claves que a alguien se le ocurrieran.
 */
public enum MeasurementKind {
    /** Pecho, medido tendido y de costura a costura. */
    CHEST,
    WAIST,
    HIP,
    /** Tiro. */
    RISE,
    SHOULDERS,
    /** Largo de manga. */
    SLEEVE,
    /** Largo de la prenda. */
    LENGTH,
    /** Largo de la plantilla interna. Es lo unico que se le mide a un zapato. */
    INSOLE,
    HEIGHT,
    WIDTH,
    DEPTH
}
