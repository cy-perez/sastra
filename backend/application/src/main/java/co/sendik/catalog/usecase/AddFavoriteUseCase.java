package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.FavoriteCommand;
import co.sendik.catalog.exception.BuyerAccountClosedException;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.BuyerAccounts;
import co.sendik.catalog.port.out.Favorites;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda una publicacion como favorita. HU-011, criterios 2, 4, 5 y 6.
 *
 * <p><strong>Carga la publicacion aunque solo vaya a escribir un par de identificadores.</strong>
 * No es un rodeo: es lo que le permite a {@link Favorite#de} comprobar las dos reglas
 * —RN-071 y RN-072— con la publicacion delante. Escribir el par sin cargarla dejaria
 * guardar favoritos sobre borradores ajenos y sobre lo propio, y las dos comprobaciones
 * tendrian que repetirse en cada sitio que marque.
 *
 * <p><strong>Comprueba que la cuenta siga existiendo.</strong> Es lo unico de los cuatro
 * casos de uso que lo hace, y es el unico que escribe: los otros tres no crean dato ni
 * revelan nada sobre una cuenta cerrada. El porque completo esta en {@link BuyerAccounts}.
 *
 * <p><strong>Es idempotente y no lo consigue preguntando.</strong> Marcar lo que ya estaba
 * marcado responde igual y no crea un segundo favorito (criterio 4). Aqui no hay ningun
 * «si no existe, guarda»: entre esa lectura y la escritura cabe la peticion de la otra
 * pestana. Lo sostiene la unicidad del par en la tabla, y el puerto lo dice en su contrato.
 */
public class AddFavoriteUseCase {

    private final Favorites favoritos;
    private final ListingRepository publicaciones;
    private final BuyerAccounts cuentas;
    private final Clock reloj;

    public AddFavoriteUseCase(
            Favorites favoritos, ListingRepository publicaciones, BuyerAccounts cuentas, Clock reloj) {
        this.favoritos = favoritos;
        this.publicaciones = publicaciones;
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    /**
     * @throws BuyerAccountClosedException si la cuenta del token ya se cerro
     * @throws ListingNotFoundException si no existe o no esta publicada. Las dos con el
     *     mismo codigo, que es RN-068: decir «esto existia» ya es decir algo
     * @throws co.sendik.catalog.exception.SelfFavoriteForbiddenException si es suya (RN-072)
     */
    @Transactional
    public void execute(FavoriteCommand comando) {
        // Antes que nada, y antes de tocar la publicacion. El token sobrevive quince
        // minutos al cierre de la cuenta (ADR-0003), y sin esto una cuenta ya cerrada podia
        // escribir favoritos que nada volveria a borrar, porque el cierre ya paso.
        if (!cuentas.estaActiva(comando.quien())) {
            throw new BuyerAccountClosedException();
        }

        Listing publicacion = publicaciones
                .buscar(comando.publicacion())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        favoritos.guardar(Favorite.de(comando.quien(), publicacion, reloj.instant()));
    }
}
