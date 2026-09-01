package co.sendik.identity.model;

/**
 * Por que se le quita el sello a quien ya lo tenia. RN-069.
 *
 * <p><strong>No es {@link RejectionReason} y no se mezclan.</strong> Aquella juzga una
 * solicitud que todavia no se aprobo: cuatro de sus cinco valores hablan de lo que se
 * entrego —fotos ilegibles, documento vencido, titular que no coincide, documento ya
 * verificado— y el quinto es generico a proposito. Reutilizarla para revocar significa
 * decirle "fotos ilegibles" a quien pierde el sello por otra cosa, y ese texto va en el
 * correo que la persona recibe.
 *
 * <p><strong>Los valores describen hechos, no delitos.</strong> Ninguno dice fraude ni
 * suplantacion, y no es un eufemismo: es la misma decision que ya tomo el motivo generico
 * de rechazo en HU-002. Sendik no consulta ninguna fuente judicial, {@code
 * docs/operacion/datos-personales.md} no tiene categoria para una calificacion asi, y el
 * motivo se guarda y viaja a la persona. Decir lo que se comprobo se puede sostener;
 * nombrar el delito es una acusacion que este sistema no esta en condiciones de hacer.
 */
public enum RevocationReason {

    /**
     * Se comprueba, despues de otorgado el sello, que la persona del documento no es la de
     * la cuenta.
     */
    DOCUMENT_NOT_ITS_HOLDER,

    /** RN-012, detectado despues de aprobar. */
    BANK_ACCOUNT_NOT_HOLDER,

    /**
     * RN-024, y mas de una vez.
     *
     * <p>Una sola publicacion prohibida se baja y no cuesta el sello: para eso esta el
     * retiro, que es la otra mitad de HU-010.
     */
    REPEATED_PROHIBITED_LISTINGS,

    /**
     * Lo pidio la propia persona.
     *
     * <p>No es cerrar la cuenta, que es RN-009 y se hace sin pedirle permiso a nadie. Es
     * dejar de vender conservandola.
     */
    HOLDER_REQUEST,

    /**
     * Ya no cumple los requisitos.
     *
     * <p><strong>Ultimo recurso y no el primero.</strong> Existe porque una lista cerrada
     * sin salida obliga a mentir cuando lo ocurrido no esta en ella. Si acaba siendo el
     * motivo de la mayoria de las revocaciones, lo que falta es un motivo y se agrega a
     * RN-069: la nota no lo sustituye, porque no se traduce ni se puede medir.
     */
    REQUIREMENTS_NO_LONGER_MET
}
