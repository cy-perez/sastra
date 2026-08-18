package co.sastra.identity.rest.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Una sesion abierta, tal como la ve su dueno. Criterio 17.
 *
 * <p>Sin la IP, ni siquiera hasheada: no le dice nada a quien mira la lista y
 * sacarla del servidor convertiria un dato guardado con cuidado en uno que viaja
 * (docs/operacion/datos-personales.md).
 *
 * @param current si es la sesion desde la que se esta mirando. Sin esto, cerrar
 *     la propia parece un fallo en vez de una decision
 */
public record ActiveSessionResponse(
        String id, @Nullable String userAgent, Instant startedAt, Instant expiresAt, boolean current) {}
