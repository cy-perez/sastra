package co.sendik.catalog.persistence;

import co.sendik.catalog.dto.FavoriteCursor;
import co.sendik.catalog.dto.FavoritedListing;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.port.out.Favorites;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia de los favoritos. HU-011.
 *
 * <p><strong>Adaptador propio y no un metodo mas en {@link JdbcListingRepository}.</strong>
 * Aquel es el repositorio de un agregado y escribe en tres tablas dentro de la misma
 * transaccion; un favorito no forma parte de ese agregado, y guardar una publicacion no
 * puede tocar los favoritos de nadie.
 *
 * <p>Lo que si comparte con el es como se lee una publicacion: la proyeccion, el mapeador
 * de filas y la carga de portadas son suyos y estan abiertos al paquete. Copiarlos aqui
 * habria dejado dos proyecciones de treinta columnas y dos mapeadores de sesenta lineas
 * que alguien tendria que acordarse de cambiar a la vez.
 */
@Repository
public class JdbcFavorites implements Favorites {

    private final JdbcClient jdbc;

    public JdbcFavorites(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotente por la clave primaria y no por una lectura previa. Criterio 4.
     *
     * <p><strong>{@code ON CONFLICT DO NOTHING} y no {@code DO UPDATE}.</strong> Volver a
     * marcar lo que ya estaba marcado tiene que dejarlo como estaba, con su fecha
     * original: con {@code DO UPDATE SET created_at = EXCLUDED.created_at}, un reintento
     * de red moveria el favorito a la cabeza de una lista que ordena justamente por esa
     * fecha.
     *
     * <p>Un {@code SELECT} antes del {@code INSERT} no serviria: entre los dos cabe la
     * peticion de la otra pestana, y lo unico que puede decidir entre dos escrituras
     * simultaneas es la restriccion de la tabla.
     */
    @Override
    public void guardar(Favorite favorito) {
        jdbc.sql("""
                        INSERT INTO favorites (user_id, listing_id, created_at)
                        VALUES (:quien, :publicacion, :cuando)
                        ON CONFLICT (user_id, listing_id) DO NOTHING
                        """)
                .param("quien", favorito.quien().value())
                .param("publicacion", favorito.publicacion().value())
                .param("cuando", Timestamp.from(favorito.marcadoEn()))
                .update();
    }

    /** Idempotente: borrar cero filas es un resultado, no un error. */
    @Override
    public void quitar(BuyerId quien, ListingId publicacion) {
        jdbc.sql("DELETE FROM favorites WHERE user_id = :quien AND listing_id = :publicacion")
                .param("quien", quien.value())
                .param("publicacion", publicacion.value())
                .update();
    }

    /**
     * Criterio 1, y la razon de que exista una lectura puntual: responde con un contador
     * sobre la clave primaria en vez de traerse la lista entera de alguien para mirar si
     * una publicacion esta dentro.
     */
    @Override
    public boolean existe(BuyerId quien, ListingId publicacion) {
        return jdbc.sql("SELECT count(*) FROM favorites WHERE user_id = :quien AND listing_id = :publicacion")
                        .param("quien", quien.value())
                        .param("publicacion", publicacion.value())
                        .query(Long.class)
                        .single()
                > 0;
    }

    /**
     * La lista propia. Criterios 11, 12, 13 y 14, y RN-071.
     *
     * <p><strong>El filtro de estado va en el {@code WHERE} y no despues.</strong> Es lo
     * que hace que los criterios 13 y 14 salgan gratis: nada se borra al pausar, la fila
     * deja de casar; y al republicar vuelve a casar sin que nadie la haya vuelto a marcar.
     * Filtrado en memoria, un tramo de veinticuatro filas del que se caen seis entregaria
     * dieciocho y diria que quedan mas.
     *
     * <p><strong>Ordena por la fecha del favorito y no por la de publicacion</strong>
     * (criterio 11): el orden que se pide es el del gesto. Va contra el indice de V16, que
     * es esta consulta escrita como indice.
     *
     * <p>El cursor compara la pareja entera —{@code (f.created_at, f.listing_id) <
     * (:cuando, :ultima)}, que es una comparacion de filas de PostgreSQL— y no la fecha
     * suelta. Dos toques seguidos caen en el mismo instante con normalidad, y con un reloj
     * fijo en pruebas siempre: filtrar solo por fecha se salta el segundo del par, y con
     * {@code <=} lo repite para siempre.
     *
     * <p><strong>La fecha del gesto se lee en la misma pasada</strong>, anteponiendo una
     * columna a la proyeccion compartida. Es la razon de que aquella este partida en dos:
     * una segunda consulta que trajera las fechas de esta persona cargaria su lista entera
     * para pintar veinticuatro tarjetas, que es justo lo que la paginacion evita.
     */
    @Override
    public List<FavoritedListing> publicadasDe(BuyerId quien, @Nullable FavoriteCursor desde, int limite) {
        String condicionDelCursor = desde == null ? "" : " AND (f.created_at, f.listing_id) < (:cuando, :ultima)";

        var consulta = jdbc.sql("SELECT f.created_at AS favorited_at, "
                        + JdbcListingRepository.COLUMNAS_BASE
                        + JdbcListingRepository.DESDE_BASE
                        + """
                         JOIN favorites f ON f.listing_id = l.id
                         WHERE f.user_id = :quien AND l.status = 'PUBLISHED'
                        """
                        + condicionDelCursor
                        + """
                         ORDER BY f.created_at DESC, f.listing_id DESC
                         LIMIT :limite
                        """)
                .param("quien", quien.value())
                .param("limite", limite);

        if (desde != null) {
            consulta = consulta.param("cuando", Timestamp.from(desde.marcadoEn()))
                    .param("ultima", desde.id().value());
        }

        List<FavoritedListing> tramo = consulta.query((fila, numero) -> new FavoritedListing(
                        JdbcListingRepository.filaAPublicacion(fila, numero),
                        fila.getTimestamp("favorited_at").toInstant()))
                .list();

        return conPortadas(tramo);
    }

    @Override
    public List<Favorite> todosDe(BuyerId quien) {
        return jdbc.sql("""
                        SELECT listing_id, created_at
                        FROM favorites
                        WHERE user_id = :quien
                        ORDER BY created_at DESC, listing_id DESC
                        """)
                .param("quien", quien.value())
                .query((fila, numero) -> Favorite.reconstruir(
                        quien,
                        new ListingId(fila.getObject("listing_id", UUID.class)),
                        fila.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public void borrarTodosDe(BuyerId quien) {
        jdbc.sql("DELETE FROM favorites WHERE user_id = :quien")
                .param("quien", quien.value())
                .update();
    }

    /**
     * Le pone a cada publicacion del tramo su toma frontal, en una sola consulta.
     *
     * <p>La rejilla de favoritos pinta las mismas tarjetas que el catalogo, asi que
     * necesita lo mismo: la portada de RN-016 y nada mas. Sin esto, cada tarjeta cargaria
     * las ocho tomas de su publicacion para quedarse con una.
     *
     * <p>Se apoya en que {@code conPortadas} devuelve la lista en el mismo orden y con el
     * mismo tamano —mapea sobre ella, no la filtra—, que es lo que permite volver a casar
     * cada publicacion con su fecha por posicion en vez de por otra busqueda.
     */
    private List<FavoritedListing> conPortadas(List<FavoritedListing> tramo) {
        List<Listing> conPortada = JdbcListingRepository.conPortadas(
                jdbc, tramo.stream().map(FavoritedListing::publicacion).toList());

        return IntStream.range(0, tramo.size())
                .mapToObj(posicion -> new FavoritedListing(
                        conPortada.get(posicion), tramo.get(posicion).marcadoEn()))
                .toList();
    }
}
