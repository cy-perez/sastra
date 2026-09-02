package co.sendik.catalog.model;

import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfFavoriteForbiddenException;
import java.time.Instant;
import java.util.Objects;

/**
 * Una publicacion que alguien guardo para volver a ella. HU-011.
 *
 * <p><strong>Su identidad es el par</strong> —quien y cual—, no un identificador propio:
 * la misma persona no puede tener dos veces guardada la misma publicacion, y de eso vive
 * la idempotencia del criterio 4. La tabla lo dice igual, con clave primaria compuesta.
 *
 * <p><strong>Es una clase y no un {@code record}, y esa es la decision de diseno.</strong>
 * Un record expone su constructor canonico, y con el a la vista cualquiera puede fabricar
 * un favorito sobre su propia publicacion o sobre un borrador sin pasar por ninguna regla.
 * Aqui el constructor es privado y las dos formas de llegar a un favorito son
 * {@link #de} —que las comprueba— y {@link #reconstruir} —que lee lo que ya se guardo—.
 *
 * <p><strong>La factoria recibe la publicacion entera y no su identificador.</strong> Es
 * lo que hace que RN-072 no sea representable en vez de ser algo que alguien recuerda
 * comprobar: con solo un {@link ListingId} delante, el dominio no tendria como saber de
 * quien es ni en que estado esta, y las dos reglas tendrian que vivir en el caso de uso.
 */
public final class Favorite {

    private final BuyerId quien;
    private final ListingId publicacion;
    private final Instant marcadoEn;

    private Favorite(BuyerId quien, ListingId publicacion, Instant marcadoEn) {
        this.quien = Objects.requireNonNull(quien, "Quien marca es obligatorio");
        this.publicacion = Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
        this.marcadoEn = Objects.requireNonNull(marcadoEn, "La fecha en que se marco es obligatoria");
    }

    /**
     * Marca una publicacion como favorita, si se puede.
     *
     * <p><strong>El orden de las dos comprobaciones importa y no es intercambiable.</strong>
     * Primero el estado y despues el dueno: al reves, pedir un favorito sobre el borrador
     * de otra persona responderia 403 y con eso confirmaria que ese identificador es una
     * publicacion real que existe y no es suya. Con el estado delante, todo lo que no esta
     * publicado responde igual que lo que no existe, que es RN-068 y lo que ya hace
     * {@code ReadListingUseCase}.
     *
     * <p>Sobre lo propio y publicado si sale el 403, y ahi es lo correcto: no se revela
     * nada que quien pregunta no sepa ya, y necesita saber por que no se guardo
     * (criterios 5 y 10).
     *
     * @throws ListingNotFoundException si la publicacion no esta visible. RN-071 y RN-068:
     *     no se guarda lo que no se puede ver
     * @throws SelfFavoriteForbiddenException si la publicacion es de quien la marca (RN-072)
     */
    public static Favorite de(BuyerId quien, Listing publicacion, Instant ahora) {
        Objects.requireNonNull(quien, "Quien marca es obligatorio");
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");

        if (!publicacion.esVisible()) {
            throw new ListingNotFoundException(publicacion.id());
        }
        if (esSuya(quien, publicacion)) {
            throw new SelfFavoriteForbiddenException();
        }

        return new Favorite(quien, publicacion.id(), ahora);
    }

    /**
     * Un favorito que ya estaba guardado.
     *
     * <p>No vuelve a comprobar las reglas, y es a proposito: lo que se guardo cumplia RN-072
     * el dia que se guardo, y una publicacion cambia de estado sin que el favorito tenga
     * nada que ver. Aplicar {@link #de} aqui haria que leer la lista fallara en cuanto
     * alguien pausara una de las publicaciones guardadas, que es justo lo contrario de lo
     * que RN-071 manda hacer: no se ve, pero sigue ahi.
     *
     * <p>Solo lo usa la persistencia al leer una fila. Ninguna otra capa tiene por que
     * construir un favorito sin pasar por la factoria.
     */
    public static Favorite reconstruir(BuyerId quien, ListingId publicacion, Instant marcadoEn) {
        return new Favorite(quien, publicacion, marcadoEn);
    }

    /**
     * Si la publicacion es de quien la esta marcando.
     *
     * <p>Compara los UUID y no los objetos: {@link BuyerId} y {@link SellerId} son tipos
     * distintos a proposito —una persona no es «un comprador» ni «un vendedor», lo es
     * segun lo que este haciendo— asi que {@code equals} entre ellos seria siempre falso y
     * la regla no protegeria de nada. Este es el unico sitio del dominio donde los dos se
     * ponen uno al lado del otro.
     */
    private static boolean esSuya(BuyerId quien, Listing publicacion) {
        return quien.value().equals(publicacion.sellerId().value());
    }

    public BuyerId quien() {
        return quien;
    }

    public ListingId publicacion() {
        return publicacion;
    }

    public Instant marcadoEn() {
        return marcadoEn;
    }

    /**
     * Dos favoritos son el mismo si son de la misma persona sobre la misma publicacion.
     *
     * <p><strong>La fecha no entra.</strong> Es la identidad de la fila, y volver a marcar
     * lo que ya estaba marcado tiene que dar el mismo favorito y no uno nuevo con otra
     * hora: es el criterio 4 —dos pestanas, un reintento— dicho en el dominio, y lo mismo
     * que la clave primaria compuesta dice en la tabla.
     */
    @Override
    public boolean equals(Object otro) {
        return otro instanceof Favorite favorito
                && quien.equals(favorito.quien)
                && publicacion.equals(favorito.publicacion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quien, publicacion);
    }

    @Override
    public String toString() {
        return "Favorite[quien=" + quien + ", publicacion=" + publicacion + "]";
    }
}
