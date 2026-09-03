package co.sendik.catalog.usecase;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.port.out.Favorites;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra los favoritos de una persona. HU-011, y el derecho de supresion de la Ley 1581.
 *
 * <p>Lo llama el cierre de cuenta, desde {@code identity}, y por un puerto: un contexto no
 * escribe en las tablas de otro. Vive aqui porque la tabla es del catalogo y porque el dia
 * que un favorito tenga algo mas que borrar —un archivo, un aviso pendiente— este es el
 * sitio que lo sabra.
 *
 * <p><strong>Borra de verdad, no anonimiza.</strong> La fila de {@code users} sobrevive
 * vaciada porque hay integridad referencial que sostener; aqui no queda nada que
 * conservar. Un favorito sin dueno no le sirve a nadie y seguiria diciendo que a alguien le
 * interesaba eso.
 *
 * <p>No falla si no hay ninguno, que es el caso de casi todas las cuentas que se cierran.
 */
public class EraseFavoritesUseCase {

    private final Favorites favoritos;

    public EraseFavoritesUseCase(Favorites favoritos) {
        this.favoritos = favoritos;
    }

    @Transactional
    public void execute(BuyerId quien) {
        favoritos.borrarTodosDe(quien);
    }
}
