package co.sastra.identity.port.out;

import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.VerificationAccess;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Bitacora de quien mira o decide sobre los datos sensibles de una verificacion
 * (HU-002, RN-046).
 *
 * <p><strong>Escribe y no lee.</strong> Consultarla es una funcion de auditoria que
 * todavia no tiene quien la pida, y un metodo de lectura sin nadie que lo llame es
 * una puerta abierta a los datos que esta bitacora existe para vigilar.
 *
 * <p>A diferencia de {@code PublicFileStore.borrar}, esto <strong>si falla</strong> si
 * no puede escribir, y la transaccion del caso de uso se va con ella. Es deliberado:
 * una aprobacion que se guarda sin dejar rastro de quien la hizo es exactamente lo que
 * la bitacora tiene que impedir. Entre perder la operacion y perder el registro de la
 * operacion, se pierde la operacion.
 */
public interface VerificationAccessLog {

    /**
     * Anota un acceso o una decision.
     *
     * @param actor quien lo hizo. Sale del token, nunca de la peticion
     * @param motivo lo que declaro quien accede. <strong>Nunca contiene el dato al que
     *     se accedio</strong> ni informacion judicial de nadie
     *     (docs/operacion/datos-personales.md)
     */
    void registrar(
            SellerVerificationId verificacion,
            UserId actor,
            VerificationAccess accion,
            @Nullable String motivo,
            Instant ahora);
}
