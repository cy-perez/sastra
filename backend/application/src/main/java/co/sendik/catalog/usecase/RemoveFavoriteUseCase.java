package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.FavoriteCommand;
import co.sendik.catalog.port.out.Favorites;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quita un favorito. HU-011, criterio 3.
 *
 * <p><strong>No carga la publicacion, y esa es toda la diferencia con marcar.</strong>
 * Marcar comprueba dos reglas que necesitan la publicacion delante; quitar no comprueba
 * ninguna, porque no hay ninguna que comprobar: lo que se borra es una fila de quien
 * pregunta, sobre algo que el mismo guardo.
 *
 * <p>Exigir que la publicacion siguiera publicada seria peor que inutil: RN-071 conserva a
 * proposito la fila de lo que se vendio o se archivo, asi que esa comprobacion dejaria
 * filas que su propio dueno no puede borrar.
 *
 * <p><strong>Idempotente</strong>: quitar lo que no esta no es un error y no lo dice. Con
 * un 404 ahi, el doble pulsado del criterio borde acabaria en un mensaje de error sobre
 * algo que salio exactamente como se pidio.
 */
public class RemoveFavoriteUseCase {

    private final Favorites favoritos;

    public RemoveFavoriteUseCase(Favorites favoritos) {
        this.favoritos = favoritos;
    }

    @Transactional
    public void execute(FavoriteCommand comando) {
        favoritos.quitar(comando.quien(), comando.publicacion());
    }
}
