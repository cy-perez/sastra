package co.sendik.catalog.dto;

/**
 * La cola del moderador, paginada. HU-008, criterio 1.
 *
 * <p>No lleva quien pregunta: la cola es una sola y el rol ya se comprobo en el borde.
 * Quien decide se necesita al decidir, para RN-063, y ahi si viaja en el mando.
 */
public record ListPendingListingsQuery(int pagina, int tamano) {

    /** Un techo, no configuracion: impide que un cliente pida la tabla entera. */
    public static final int TAMANO_MAXIMO = 50;

    public ListPendingListingsQuery {
        if (pagina < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa: " + pagina);
        }
        if (tamano <= 0 || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El tamano de pagina va de 1 a " + TAMANO_MAXIMO + ": " + tamano);
        }
    }
}
