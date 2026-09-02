package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.FavoriteCursor;
import co.sendik.catalog.dto.FavoritePage;
import co.sendik.catalog.dto.FavoritedListing;
import co.sendik.catalog.dto.ListFavoritesQuery;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.Favorites;
import java.util.ArrayList;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lista propia de favoritos. HU-011, criterios 11, 12, 13 y 14.
 *
 * <p><strong>Aqui no se filtra nada por estado, y no es un olvido.</strong> RN-071 la
 * aplica la consulta del repositorio, y tiene que ser asi: filtrar despues, sobre el tramo
 * ya traido, entregaria dieciocho elementos de veinticuatro y diria que quedan mas, o
 * peor, un tramo vacio con cursor. Los criterios 13 y 14 son la misma consulta vista dos
 * veces —lo que deja de estar publicado no aparece, y si vuelve, vuelve— y ninguno de los
 * dos exige codigo aqui: nada se borra, solo deja de casar con el filtro.
 *
 * <p><strong>Recibe de quien es la lista, al reves que el catalogo publico.</strong>
 * {@code ListCatalogUseCase} no sabe quien pregunta a proposito, porque ensena lo mismo a
 * todo el mundo. Esta lista es de alguien por definicion (RN-070), y quien es sale del
 * token.
 */
public class ListFavoritesUseCase {

    private final Favorites favoritos;

    public ListFavoritesUseCase(Favorites favoritos) {
        this.favoritos = favoritos;
    }

    @Transactional
    public FavoritePage execute(ListFavoritesQuery consulta) {
        // Uno mas del que se va a entregar: es como se sabe si hay siguiente sin contar la
        // lista entera, y el sobrante nunca sale de aqui.
        List<FavoritedListing> traidas =
                favoritos.publicadasDe(consulta.quien(), consulta.desde(), consulta.limite() + 1);

        return armar(traidas, consulta.limite());
    }

    /** Parte el tramo en lo que se entrega y la senal de que hay mas. */
    static FavoritePage armar(List<FavoritedListing> traidas, int limite) {
        List<Listing> publicaciones =
                traidas.stream().map(FavoritedListing::publicacion).toList();

        if (traidas.size() <= limite) {
            return FavoritePage.ultima(publicaciones);
        }

        List<Listing> entregadas = new ArrayList<>(publicaciones.subList(0, limite));
        FavoritedListing ultima = traidas.get(limite - 1);

        return new FavoritePage(
                entregadas,
                new FavoriteCursor(ultima.marcadoEn(), ultima.publicacion().id()),
                true);
    }
}
