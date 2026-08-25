package co.sendik.identity.model;

/**
 * Documento legal que la persona acepta al registrarse.
 *
 * <p>Son dos y se aceptan por separado: una sola casilla para ambos no es
 * consentimiento valido segun la Ley 1581 de 2012
 * (docs/operacion/datos-personales.md).
 */
public enum ConsentDocument {
    TERMS,
    PRIVACY
}
