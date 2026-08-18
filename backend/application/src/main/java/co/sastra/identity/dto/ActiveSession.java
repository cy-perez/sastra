package co.sastra.identity.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Una sesion abierta, tal como se le muestra a su dueno. Criterio 17.
 *
 * <p><strong>No lleva la IP, ni siquiera hasheada.</strong> Un hash no le dice
 * nada a la persona que mira la lista, asi que no aporta a lo que esta pantalla
 * existe para hacer, que es reconocer las propias sesiones. Y sacarlo del
 * servidor convertiria un dato guardado con cuidado en uno que viaja
 * (docs/operacion/datos-personales.md).
 *
 * @param id el de la familia. Es el identificador estable de la sesion, el que
 *     sobrevive a las rotaciones, y el que hay que enviar para cerrarla
 * @param userAgent lo que dijo el navegador. Puede faltar: una peticion sin esa
 *     cabecera es rara pero no invalida
 * @param actual si es la sesion desde la que se esta mirando la lista. Sin esto,
 *     cerrar la propia parece un fallo en vez de una decision
 */
public record ActiveSession(String id, @Nullable String userAgent, Instant iniciada, Instant expira, boolean actual) {}
