package co.sendik.identity.model;

/**
 * Documentos de identidad que se aceptan para verificar a un vendedor.
 *
 * <p>Los tres del glosario, y solo esos tres. **Sin pasaporte** a proposito: no se
 * pidio, y agregarlo seria decidir por nadie quien puede vender.
 *
 * <p>El PPT es el Permiso por Proteccion Temporal. Esta aqui porque una parte de la
 * poblacion que vende en Colombia se identifica con el y no con una cedula:
 * dejarlo fuera seria excluirla del marketplace sin haberlo decidido.
 */
public enum IdentityDocumentType {

    /** Cedula de ciudadania. */
    CC,

    /** Cedula de extranjeria. */
    CE,

    /** Permiso por Proteccion Temporal. */
    PPT
}
