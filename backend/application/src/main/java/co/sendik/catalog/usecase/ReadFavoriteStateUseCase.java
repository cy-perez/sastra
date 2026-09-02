package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.FavoriteCommand;
import co.sendik.catalog.dto.FavoriteState;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.Favorites;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si una publicacion esta guardada, y si se puede guardar. HU-011, criterios 1 y 5.
 *
 * <p><strong>Existe para no traerse la lista entera.</strong> Saber si una publicacion
 * concreta esta marcada se podria deducir de la lista, y es lo barato mientras alguien
 * tenga seis favoritos. Con trescientos, abrir una ficha descargaria trescientas
 * publicaciones para mirar una.
 *
 * <p><strong>Responde dos cosas y la segunda no es un capricho.</strong> {@code elegible}
 * es lo que le permite a la ficha no ofrecer el control sobre la publicacion propia
 * (criterio 5) sin que el navegador tenga que saber quien es el dueno. La regla sigue
 * comprobandose al marcar: esto solo evita que alguien pulse para enterarse.
 *
 * <p>Nunca falla por no encontrar la publicacion. Una ficha que se acaba de vender
 * responde {@code (false, false)} y con eso la pantalla retira el control, que es lo que
 * hace falta; lanzar aqui obligaria a la ficha a distinguir un error real de una respuesta
 * normal.
 */
public class ReadFavoriteStateUseCase {

    private final Favorites favoritos;
    private final ListingRepository publicaciones;

    public ReadFavoriteStateUseCase(Favorites favoritos, ListingRepository publicaciones) {
        this.favoritos = favoritos;
        this.publicaciones = publicaciones;
    }

    /*
     * Sin readOnly = true, por lo mismo que ReadListingUseCase: presentation no declara
     * spring-tx, y al leer el atributo para inyectar esta clase avisa de que no puede
     * resolverlo. Con -Xlint:all -Werror ese aviso rompe la compilacion.
     */
    @Transactional
    public FavoriteState execute(FavoriteCommand consulta) {
        boolean marcado = favoritos.existe(consulta.quien(), consulta.publicacion());

        boolean sePuede = publicaciones
                .buscar(consulta.publicacion())
                .filter(Listing::esVisible)
                .filter(publicacion -> !esSuya(consulta, publicacion))
                .isPresent();

        return new FavoriteState(marcado, sePuede);
    }

    /**
     * Los dos identificadores envuelven el mismo UUID y son tipos distintos: la
     * comparacion baja a los valores, igual que en {@code Favorite}. Aqui se repite
     * porque el dominio no puede exponer su comprobacion sin construir el favorito, y
     * construirlo lanzaria en el caso que esta consulta tiene que responder con calma.
     */
    private static boolean esSuya(FavoriteCommand consulta, Listing publicacion) {
        return Optional.of(publicacion.sellerId().value())
                .filter(vendedor -> vendedor.equals(consulta.quien().value()))
                .isPresent();
    }
}
