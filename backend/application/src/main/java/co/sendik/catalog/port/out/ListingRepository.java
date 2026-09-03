package co.sendik.catalog.port.out;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
     * El catalogo publico: lo que esta publicado, lo mas reciente primero. RN-068.
     *
     * <p><strong>Por cursor y no por pagina</strong>, que es lo que contrato-api.md exige
     * para esta lista y lo que este mismo archivo anunciaba desde HU-007. El cursor lleva
     * la fecha y el identificador porque {@code published_at} se repite: ordenar solo por
     * fecha deja pares en orden indefinido, y un tramo que empieza donde el anterior creia
     * haber terminado se salta o repite elementos.
     *
     * <p>El filtro de categorias llega ya resuelto y vacio significa «todo el catalogo».
     * El caso de uso es quien sabe que una familia son sus hijas; aqui no hay arbol.
     *
     * @param categorias donde buscar, o vacio para todas
     * @param desde por donde seguir, o nulo para el primer tramo
     * @param limite cuantas traer. Quien llama pide una de mas para saber si hay siguiente
     */
    List<Listing> publicadas(List<CategoryId> categorias, @Nullable CatalogCursor desde, int limite);

    /**
     * Lo publicado de un vendedor, para cualquiera. RN-068.
     *
     * <p>Metodo aparte de {@link #buscarDelVendedor} a proposito: aquel es el panel del
     * dueno y trae los siete estados, este es el escaparate y trae uno. Escribirlos como
     * el mismo metodo con un booleano seria dejar la diferencia entre lo publico y lo
     * privado a merced de un parametro.
     */
    List<Listing> publicadasDelVendedor(SellerId vendedor, @Nullable CatalogCursor desde, int limite);

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
     *
     * <p><strong>Salto y no numero de pagina.</strong> Con {@code (pagina, tamano)} el
     * desplazamiento se deriva del mismo argumento que el limite, asi que quien pida una
     * fila de mas -para saber si hay pagina siguiente sin contar la tabla- mueve tambien
     * el arranque: {@code (1, 21)} salta 21 filas en vez de 20 y la fila 21 no sale en
     * ninguna pagina. Se pierde una por pagina, en silencio.
     *
     * <p>Aqui no llego a pasar porque nadie pide de mas todavia; le paso a la cola de
     * verificaciones en cuanto quiso su {@code hasMore}, y esta firma es la misma leccion
     * aplicada antes de pagarla. Ver {@code SellerVerificationRepository}.
     *
     * @param salto cuantas filas se saltan antes de empezar. Como {@code long}: el
     *     producto de pagina por tamano desborda en {@code int} mucho antes de que la
     *     tabla llegue ahi, y desbordar da un salto negativo, que PostgreSQL responde con
     *     un error y no con una pagina vacia
     * @param cuantas cuantas traer
     */
    List<Listing> pendientesDeRevision(long salto, int cuantas);

    /**
     * Si queda al menos una esperando revision a partir de esa posicion. La respuesta a
     * «¿hay pagina siguiente?».
     *
     * <p>Existe para no tener que traer a nadie para contestarlo. Pedir una fila de mas y
     * usar su presencia como señal funciona, pero {@link #pendientesDeRevision} devuelve
     * la publicacion entera y ademas resuelve su portada: seria una consulta de imagenes
     * mas por cada carga, para una fila que nadie va a ver.
     *
     * <p>Tampoco es contar: no dice cuantas quedan -que exigiria recorrerlas todas- sino
     * si queda alguna, y para eso basta con llegar hasta la primera.
     *
     * @param salto la posicion a partir de la cual se pregunta. Para saber si hay pagina
     *     siguiente es el final de la actual
     */
    boolean hayPendientesDesde(long salto);
}
