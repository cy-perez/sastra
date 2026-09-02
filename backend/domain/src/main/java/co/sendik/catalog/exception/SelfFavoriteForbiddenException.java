package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-072: nadie marca como favorita su propia publicacion.
 *
 * <p>No significa nada —quien la publico no necesita guardarla para volver a ella, la
 * tiene en su panel— y el dia que exista cualquier senal derivada de los favoritos seria
 * la forma mas barata de inflarla.
 *
 * <p><strong>Se comprueba en el servidor y no escondiendo el control.</strong> Es lo mismo
 * que dicen RN-060 y RN-063 de sus propias reglas: la peticion se puede mandar sin pasar
 * por la interfaz, y una regla que solo vive en una pantalla no es una regla.
 *
 * <p>A diferencia de {@link SelfModerationForbiddenException}, esta comprobacion si vive
 * en el dominio: {@link co.sendik.catalog.model.Favorite} recibe la publicacion entera al
 * construirse, asi que tiene delante a quien marca y a quien vende sin que nadie se los
 * tenga que pasar por separado.
 */
public final class SelfFavoriteForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public SelfFavoriteForbiddenException() {
        super(ErrorCode.CATALOG_SELF_FAVORITE_FORBIDDEN, "Nadie marca como favorita su propia publicacion (RN-072)");
    }
}
