package co.sendik.catalog.port.out;

import co.sendik.catalog.dto.FavoriteCursor;
import co.sendik.catalog.dto.FavoritedListing;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.model.ListingId;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Donde viven los favoritos. HU-011.
 *
 * <p>Puerto propio y no un metodo mas en {@link ListingRepository}: aquel es el repositorio
 * del agregado de la publicacion, y un favorito no forma parte de ese agregado. Guardar una
 * publicacion no puede tocar los favoritos de nadie, y quitar un favorito no puede escribir
 * en la publicacion; con un solo puerto esa separacion quedaria a un metodo de distancia.
 *
 * <p><strong>Ningun metodo recibe la publicacion entera.</strong> Las reglas ya se
 * comprobaron cuando se construyo el {@link Favorite}; aqui solo se escribe y se lee.
 */
public interface Favorites {

    /**
     * Guarda el favorito. <strong>Idempotente</strong>: guardarlo dos veces deja uno.
     *
     * <p>Es el criterio 4, y se resuelve donde de verdad se puede resolver. Comprobar antes
     * de escribir no basta: entre la comprobacion y la escritura cabe la peticion de la
     * otra pestana, y ahi no hay lectura que salve. Lo sostiene la unicidad del par en la
     * tabla, y quien implemente esto tiene que apoyarse en ella y no en un {@code if}.
     */
    void guardar(Favorite favorito);

    /**
     * Quita el favorito. <strong>Idempotente tambien</strong>: quitar lo que no esta no es
     * un error, y no tiene por que decirlo.
     *
     * <p>No comprueba el estado de la publicacion, y es a proposito: RN-071 conserva la
     * fila de lo que dejo de verse, asi que tiene que poder quitarse. Un desmarcado que
     * exigiera que la publicacion siguiera publicada dejaria filas imposibles de borrar.
     */
    void quitar(BuyerId quien, ListingId publicacion);

    /** Si esa persona tiene guardada esa publicacion. Criterio 1. */
    boolean existe(BuyerId quien, ListingId publicacion);

    /**
     * La lista propia: lo guardado que sigue estando {@code PUBLISHED}, lo mas reciente
     * primero. RN-071.
     *
     * <p><strong>El filtro de estado es de esta consulta y no de quien la llama.</strong>
     * Los criterios 13 y 14 son la misma consulta vista dos veces: nada se borra al pausar,
     * solo deja de casar, y volver a publicar lo devuelve a la lista sin que nadie lo haya
     * vuelto a marcar. Filtrar despues, en memoria, romperia la paginacion: un tramo de
     * veinticuatro filas del que se caen seis entrega dieciocho y dice que quedan mas.
     *
     * <p>Ordena por la fecha del favorito y no por la de publicacion (criterio 11): el
     * orden que se pide es el del gesto.
     *
     * @param desde por donde seguir, o nulo para el primer tramo
     * @param limite cuantas traer. Quien llama pide una de mas para saber si hay siguiente
     */
    List<FavoritedListing> publicadasDe(BuyerId quien, @Nullable FavoriteCursor desde, int limite);

    /**
     * Todo lo que esta persona tiene guardado, sin filtrar por estado.
     *
     * <p>Para la descarga de datos personales, y solo para eso. Aqui si sale lo que la
     * lista esconde: lo que Sendik guarda de alguien es el par y su fecha, y el derecho a
     * conocer de la Ley 1581 es sobre lo que se guarda, no sobre lo que se ensena.
     */
    List<Favorite> todosDe(BuyerId quien);

    /**
     * Borra los favoritos de esa persona. Para el cierre de cuenta.
     *
     * <p>Borrar de verdad y no anonimizar, al reves que la fila de {@code users}: alli
     * sobrevive el identificador porque hay integridad referencial que sostener, y aqui no
     * queda nada que conservar. Un favorito sin dueno no le sirve a nadie y sigue diciendo
     * que a alguien le interesaba eso.
     */
    void borrarTodosDe(BuyerId quien);
}
