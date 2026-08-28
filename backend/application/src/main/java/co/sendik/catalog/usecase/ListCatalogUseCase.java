package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.exception.UnknownCategoryException;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * El catalogo publico. HU-009, criterios 1, 2, 3, 5, 8, 9 y 10.
 *
 * <p><strong>La regla que este caso de uso hace cumplir es RN-068</strong>: se ve solo lo
 * que esta {@code PUBLISHED}. No recibe quien pregunta y no tiene como saberlo, y eso es
 * deliberado: el catalogo ensena lo mismo a todo el mundo, tambien al dueno de la
 * publicacion, que ve lo suyo en su panel. Un parametro «quien mira» aqui seria la puerta
 * por la que un dia se cuela una excepcion.
 *
 * <p><strong>Una familia no es una hoja.</strong> No se publica en una familia sino en una
 * categoria suya, asi que abrir «Tecnologia» tiene que traer lo de sus siete categorias.
 * Resolverlo aqui y no en el repositorio mantiene al SQL sin saber que es un arbol.
 *
 * <p>Una categoria retirada del arbol no devuelve un listado vacio: devuelve
 * {@link UnknownCategoryException}, que sale como 404. Un vacio se leeria como «esta
 * categoria existe y no tiene nada», que es otra cosa y ademas mentira.
 */
public class ListCatalogUseCase {

    private final ListingRepository publicaciones;
    private final Categories categorias;

    public ListCatalogUseCase(ListingRepository publicaciones, Categories categorias) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
    }

    public CatalogPage execute(ListCatalogQuery consulta) {
        List<CategoryId> donde = resolver(consulta.categoria());

        // Se pide uno mas del que se va a entregar: es como se sabe si hay siguiente sin
        // contar el catalogo entero, y el sobrante nunca sale de aqui.
        List<Listing> traidas = publicaciones.publicadas(donde, consulta.desde(), consulta.limite() + 1);

        return armar(traidas, consulta.limite());
    }

    /**
     * Las categorias donde de verdad hay publicaciones.
     *
     * <p>Vacio significa «todo el catalogo» y no «ninguna»: es lo que llega cuando nadie
     * eligio categoria.
     */
    private List<CategoryId> resolver(@Nullable CategoryId elegida) {
        if (elegida == null) {
            return List.of();
        }

        List<CategoryId> publicables = categorias.publicablesBajo(elegida);
        if (publicables.isEmpty()) {
            throw new UnknownCategoryException(elegida);
        }
        return publicables;
    }

    /** Parte el tramo en lo que se entrega y la senal de que hay mas. */
    static CatalogPage armar(List<Listing> traidas, int limite) {
        if (traidas.size() <= limite) {
            return CatalogPage.ultima(traidas);
        }

        List<Listing> entregadas = new ArrayList<>(traidas.subList(0, limite));
        Listing ultima = entregadas.get(entregadas.size() - 1);

        return new CatalogPage(entregadas, new CatalogCursor(publicadaEn(ultima), ultima.id()), true);
    }

    /**
     * Cuando se publico, que es por lo que ordena el listado.
     *
     * <p>No puede ser nulo aqui: el repositorio solo devuelve publicaciones publicadas y el
     * dominio sella la fecha al aprobar. Si lo fuera, seria una fila escrita a mano.
     */
    private static Instant publicadaEn(Listing publicacion) {
        Instant momento = publicacion.publishedAt();
        if (momento == null) {
            throw new IllegalStateException(
                    "Una publicacion del catalogo sin fecha de publicacion: " + publicacion.id());
        }
        return momento;
    }
}
