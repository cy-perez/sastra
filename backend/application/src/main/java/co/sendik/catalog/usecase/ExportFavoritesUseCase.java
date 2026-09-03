package co.sendik.catalog.usecase;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.port.out.Favorites;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo lo que una persona tiene guardado, para su descarga de datos. HU-011.
 *
 * <p><strong>No existe para el catalogo sino para {@code identity}</strong>, que es quien
 * atiende el derecho a conocer de la Ley 1581 y no puede leer esta tabla: un contexto no
 * consulta las tablas de otro, le pregunta por un caso de uso publico
 * (docs/arquitectura/vision-tecnica.md). Es el mismo patron que usa el catalogo en la
 * direccion contraria para saber si un vendedor esta verificado.
 *
 * <p><strong>Sin filtrar por estado, al reves que {@link ListFavoritesUseCase}.</strong>
 * La lista de la pantalla ensena lo que se puede volver a ver (RN-071); esto entrega lo
 * que Sendik guarda, que es el par y su fecha. Esconder en una descarga de datos
 * personales las filas cuya publicacion se archivo seria responder con un resumen a un
 * derecho que es sobre lo que hay.
 *
 * <p>No pagina. La descarga se sirve entera de una vez y en un solo archivo, que es como
 * ya se sirve el resto (criterio 22 de HU-001).
 */
public class ExportFavoritesUseCase {

    private final Favorites favoritos;

    public ExportFavoritesUseCase(Favorites favoritos) {
        this.favoritos = favoritos;
    }

    @Transactional
    public List<Favorite> execute(BuyerId quien) {
        return favoritos.todosDe(quien);
    }
}
