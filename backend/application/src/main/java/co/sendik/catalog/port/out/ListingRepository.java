package co.sendik.catalog.port.out;

import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import java.util.List;
import java.util.Optional;

/**
 * Donde viven las publicaciones. Un repositorio por agregado, no por tabla.
 *
 * <p>Guardar una publicacion guarda tambien su producto y sus imagenes: son el mismo
 * agregado aunque sean tres tablas. Quien implemente esto decide como, pero no puede
 * ofrecer guardar una imagen suelta, porque entonces alguien podria dejar el agregado
 * en un estado que el dominio nunca habria permitido.
 */
public interface ListingRepository {

    /** Inserta o actualiza. El bloqueo optimista lo resuelve el adaptador. */
    Listing guardar(Listing publicacion);

    Optional<Listing> buscar(ListingId id);

    /**
     * La publicacion, solo si es de ese vendedor.
     *
     * <p>Metodo propio y no un filtro en cada caso de uso: la comprobacion de dueno la
     * necesitan nueve de ellos, y escrita nueve veces tarde o temprano una se escribe
     * distinta. Esa es la que deja editar la publicacion de otro.
     *
     * <p>Devuelve vacio tanto si no existe como si no es suya. Un caso de uso que
     * distinguiera las dos cosas acabaria respondiendo 403 y confirmando que existe
     * (criterio 33).
     */
    Optional<Listing> buscarDelDueno(ListingId id, SellerId vendedor);

    /**
     * Lo del vendedor, lo mas reciente primero.
     *
     * <p>Por pagina y no por cursor: es un listado acotado y de uso administrativo, que
     * es la excepcion que contrato-api.md admite. El catalogo publico, cuando llegue,
     * si va por cursor.
     */
    List<Listing> buscarDelVendedor(SellerId vendedor, int pagina, int tamano);

    /**
     * La cola del moderador: lo que espera revision, lo que lleva mas tiempo primero.
     *
     * <p>Ordena por {@code submittedAt} y no por {@code updatedAt}, y esa es toda la
     * razon de que la columna exista: una publicacion en revision puede cambiar de
     * precio, y con {@code updatedAt} tocarlo retrasaria su propio turno.
     *
     * <p>No recibe quien pregunta. Filtrar por moderador seria un error: la cola es una
     * sola y RN-063 —que nadie decida sobre lo suyo— se comprueba al decidir, no al
     * listar. Esconderle su propia publicacion le impediria ver que esta en la fila.
     */
    List<Listing> pendientesDeRevision(int pagina, int tamano);
}
