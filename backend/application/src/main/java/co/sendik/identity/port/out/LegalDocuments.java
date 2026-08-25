package co.sendik.identity.port.out;

import co.sendik.identity.model.ConsentDocument;

/**
 * Puerto de salida hacia la version vigente de cada documento legal.
 *
 * <p>Es un puerto y no una constante porque la version cambia cada vez que se
 * publica un texto nuevo, y eso no puede exigir un despliegue de codigo
 * (docs/operacion/configuracion.md).
 */
public interface LegalDocuments {

    /** Identificador de la version que la persona esta aceptando ahora mismo. */
    String versionVigente(ConsentDocument documento);
}
