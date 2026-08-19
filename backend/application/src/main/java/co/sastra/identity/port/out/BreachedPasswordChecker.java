package co.sastra.identity.port.out;

import co.sastra.identity.model.RawPassword;

/**
 * Puerto de salida hacia la lista de contrasenas filtradas (ADR-0013).
 *
 * <p>El puerto devuelve tres resultados y no un booleano a proposito. "No se
 * pudo comprobar" es un caso distinto de "no esta filtrada", y quien decide que
 * hacer con el es el caso de uso, no el adaptador: la politica de fallo abierto
 * es una regla de negocio y tiene que poder probarse sin red.
 */
public interface BreachedPasswordChecker {

    enum Resultado {
        /** Se consulto y no aparece en ninguna filtracion conocida. */
        LIMPIA,
        /** Se consulto y aparece. RN-005 la rechaza. */
        FILTRADA,
        /** No se pudo consultar: sin red, tiempo agotado o el servicio caido. */
        NO_SE_PUDO_COMPROBAR
    }

    Resultado verificar(RawPassword contrasena);
}
