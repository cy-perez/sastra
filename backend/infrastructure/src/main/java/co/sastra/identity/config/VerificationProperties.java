package co.sastra.identity.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Verificacion de vendedor (HU-002).
 *
 * @param reviewDays dias habiles que se promete tardar en revisar una solicitud
 *     (criterio 6). Va por configuracion porque es una promesa que la pantalla y el
 *     correo dicen en voz alta, y cambiarla no puede exigir un despliegue de codigo.
 *     <p><strong>Nadie la hace cumplir.</strong> Una solicitud que tarda mas no cambia
 *     de estado sola ni avisa a nadie: si eso hace falta, es una regla nueva y hay que
 *     escribirla.
 *     <p>El tope es holgado a proposito. Lo que hay que evitar es el cero —prometer
 *     revisar "en cero dias habiles" no significa nada, y en Colombia lo anunciado es
 *     exigible— no acotar una decision de operacion.
 */
@Validated
@ConfigurationProperties(prefix = "sastra.verification")
public record VerificationProperties(@Positive @Max(20) int reviewDays) {}
