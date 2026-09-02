package co.sendik.catalog.rest.dto;

/**
 * Si una publicacion esta guardada, y si se puede guardar. HU-011, criterios 1 y 5.
 *
 * <p><strong>Responde 200 con {@code favorite: false} y no 404 cuando no esta
 * guardada.</strong> Un 404 seria lo ortodoxo si el recurso fuera «el favorito», pero lo
 * que esta ruta responde es el estado del control que la ficha va a pintar, y ese estado
 * existe siempre. Con 404, la pantalla tendria que tratar como error el caso mas comun de
 * todos: abrir una publicacion que todavia no se ha guardado.
 *
 * <p>{@code eligible} es falso sobre la publicacion propia (RN-072) y sobre la que ya no
 * esta publicada. La ficha lo usa para no ofrecer un control que el servidor va a
 * rechazar; la regla se sigue comprobando al marcar.
 */
public record FavoriteStateResponse(boolean favorite, boolean eligible) {}
