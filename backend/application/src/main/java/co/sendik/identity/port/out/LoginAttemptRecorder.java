package co.sendik.identity.port.out;

import co.sendik.identity.model.Email;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Puerto de salida hacia el registro de intentos de acceso.
 *
 * <p>Existe para poder investigar despues: reconocer un ataque de fuerza bruta
 * distribuido, o explicarle a alguien desde donde entraron a su cuenta. El
 * bloqueo de RN-006 no se calcula desde aqui, sino desde el contador de
 * {@link co.sendik.identity.model.UserCredentials}, que es una regla de negocio y
 * no una consulta de auditoria.
 *
 * <p>Recibe el correo y no su hash: hashearlo es tarea del adaptador. La tabla
 * guarda {@code email_hash} para no acumular direcciones en claro en un registro
 * de auditoria que se conserva 90 dias
 * (docs/arquitectura/modelo-datos.md, docs/operacion/datos-personales.md).
 */
public interface LoginAttemptRecorder {

    void registrar(Email correo, @Nullable String ipHash, boolean exitoso, Instant ahora);
}
