package co.sastra.catalog.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Los siete estados de una publicacion y sus transiciones validas. RN-061.
 *
 * <p>La tabla vive aqui y no en un servicio ni en un controlador porque es lo que
 * backend/CLAUDE.md exige: las transiciones se definen en el propio enum. Y vive en
 * un solo sitio porque una tabla de transiciones duplicada acaba diciendo dos cosas
 * distintas.
 *
 * <p><strong>De {@code PENDING_REVIEW} si se vuelve atras</strong>, al reves que en
 * la verificacion de vendedor (RN-059). Alli no se puede porque una cedula ya vista
 * no se retira; aqui lo unico que se retira es la foto de un producto.
 */
public enum ListingStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    PAUSED,
    SOLD,
    ARCHIVED;

    private static final Map<ListingStatus, Set<ListingStatus>> DESTINOS = Map.of(
            DRAFT, EnumSet.of(DRAFT, PENDING_REVIEW, ARCHIVED),
            PENDING_REVIEW, EnumSet.of(DRAFT, PUBLISHED, REJECTED),
            PUBLISHED, EnumSet.of(PENDING_REVIEW, PAUSED, SOLD, ARCHIVED),
            REJECTED, EnumSet.of(DRAFT, ARCHIVED),
            PAUSED, EnumSet.of(PUBLISHED, PENDING_REVIEW, ARCHIVED),
            SOLD, EnumSet.noneOf(ListingStatus.class),
            ARCHIVED, EnumSet.noneOf(ListingStatus.class));

    public boolean puedePasarA(ListingStatus destino) {
        return DESTINOS.get(this).contains(destino);
    }

    /** {@code SOLD} por RN-023; {@code ARCHIVED} porque archivar es retirar para siempre. */
    public boolean esTerminal() {
        return DESTINOS.get(this).isEmpty();
    }

    /** Lo unico que el catalogo muestra. Pausada no se ve, y rechazada tampoco. */
    public boolean esVisible() {
        return this == PUBLISHED;
    }

    /**
     * Donde el vendedor escribe libremente.
     *
     * <p>Solo el borrador. Una publicacion viva tambien se puede cambiar, pero eso no
     * es escribir en ella: es {@link Listing#editarContenido} o
     * {@link Listing#cambiarPrecio}, que deciden si vuelve a moderacion (RN-062).
     */
    public boolean admiteEdicionLibre() {
        return this == DRAFT;
    }

    /** Los destinos validos desde aqui. Copia: nadie modifica la tabla desde fuera. */
    public Set<ListingStatus> destinos() {
        return EnumSet.copyOf(DESTINOS.get(this));
    }
}
