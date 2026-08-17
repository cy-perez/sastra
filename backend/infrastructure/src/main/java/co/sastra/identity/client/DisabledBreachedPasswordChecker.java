package co.sastra.identity.client;

import co.sastra.identity.model.RawPassword;
import co.sastra.identity.port.out.BreachedPasswordChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sustituto cuando la comprobacion esta apagada (ADR-0013).
 *
 * <p>Devuelve {@code NO_SE_PUDO_COMPROBAR} y no {@code LIMPIA} a proposito. Decir
 * "limpia" seria mentir: nadie la comprobo. Con este resultado el caso de uso
 * aplica su fallo abierto, deja pasar el registro y lo deja anotado en el
 * registro del servidor, que es exactamente lo que ocurre.
 *
 * <p>El minimo de diez caracteres de RN-005 se sigue aplicando: vive en el
 * dominio y no depende de esta bandera.
 */
@Component
@ConditionalOnProperty(prefix = "sastra.password", name = "breach-check-enabled", havingValue = "false")
public class DisabledBreachedPasswordChecker implements BreachedPasswordChecker {

    @Override
    public Resultado verificar(RawPassword contrasena) {
        return Resultado.NO_SE_PUDO_COMPROBAR;
    }
}
