package co.sastra.identity.dto;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Datos de un registro, ya extraidos del HTTP.
 *
 * <p>Llegan como tipos simples y no como objetos de dominio: convertirlos es
 * parte del caso de uso, y ahi es donde una fecha imposible o un correo mal
 * escrito se convierten en un error con codigo en vez de en una excepcion cruda.
 *
 * @param acceptsTerms y {@code acceptsPrivacy} son dos casillas separadas. Una
 *     sola para ambas cosas no es consentimiento valido segun la Ley 1581
 *     (docs/operacion/datos-personales.md).
 * @param ipHash la IP ya hasheada por el borde. Aqui nunca llega en claro.
 */
public record RegisterUserCommand(
        String email,
        String password,
        String displayName,
        LocalDate birthDate,
        @Nullable String locale,
        boolean acceptsTerms,
        boolean acceptsPrivacy,
        @Nullable String ipHash) {}
