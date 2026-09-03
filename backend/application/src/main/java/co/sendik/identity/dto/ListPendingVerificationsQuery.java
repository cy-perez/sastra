package co.sendik.identity.dto;

/**
 * La bandeja del moderador, paginada. HU-006.
 *
 * <p><strong>Nacio sin paginacion y a proposito.</strong> El argumento era que esta lista
 * la trabaja una persona hasta vaciarla, y que si desborda el problema no es la consulta
 * sino que nadie esta revisando. Como descripcion de la carga sigue siendo cierto: la cola
 * ordena lo mas viejo primero y decidir saca la fila, asi que quien revisa siempre tiene
 * trabajo en la primera pagina y la bandeja drena sola.
 *
 * <p>Lo que ese argumento no cubria es <strong>buscar</strong>. Un moderador que quiere
 * llegar a una solicitud concreta -porque le escribieron, porque la reclamaron- no puede
 * pasar de las primeras veinte por ningun camino, y no hay nada que le diga que hay mas.
 * Eso no es carga, es alcance, y por eso ahora se pagina.
 *
 * <p>Por numero de pagina y no por cursor: contrato-api.md reserva el cursor para el
 * catalogo publico y admite pagina y tamano en los listados administrativos acotados.
 * Es la misma forma que {@code ListPendingListingsQuery}, que es la otra mitad de la
 * misma pantalla.
 */
public record ListPendingVerificationsQuery(int pagina, int tamano) {

    /** Un techo, no configuracion: impide que un cliente pida la tabla entera. */
    public static final int TAMANO_MAXIMO = 50;

    public ListPendingVerificationsQuery {
        if (pagina < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa: " + pagina);
        }
        if (tamano <= 0 || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El tamano de pagina va de 1 a " + TAMANO_MAXIMO + ": " + tamano);
        }
    }
}
