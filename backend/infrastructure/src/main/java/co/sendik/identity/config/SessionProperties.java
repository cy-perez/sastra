package co.sendik.identity.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Todo lo que define una sesion: la firma del token de acceso, la vigencia de los
 * dos tokens y la cookie que transporta el de refresco (ADR-0003).
 *
 * <p>Las variables de entorno se llaman {@code JWT_*} y no {@code SESSION_*}
 * porque asi estan publicadas en {@code docs/operacion/configuracion.md} y en
 * {@code .env.example} desde antes de existir este codigo. Renombrarlas obligaria
 * a cambiar el gestor de secretos del entorno de despliegue, que es mucho mas caro
 * que aceptar un nombre imperfecto.
 *
 * @param issuer quien firma. Va dentro del token y se valida al leerlo: un token
 *     emitido por otro entorno no sirve aqui
 * @param secret clave de firma HS256. El minimo de 32 caracteres no es decorativo:
 *     HS256 usa una clave de 256 bits y una mas corta la debilita sin avisar
 * @param accessTtl RN-007: 15 minutos
 * @param refreshTtl RN-007: 30 dias
 * @param refreshGrace ventana de gracia de RN-007. Cuanto tiempo despues de rotar
 *     un token se sigue admitiendo que vuelva a llegar sin tratarlo como
 *     reutilizacion. Se mide en segundos y no en minutos a proposito: cubre una
 *     carrera entre dos peticiones del mismo navegador, que dura lo que dura una
 *     ida y vuelta, y cada segundo de mas es tiempo en el que un token robado se
 *     puede reproducir sin levantar la alarma
 */
@Validated
@ConfigurationProperties(prefix = "sendik.session")
public record SessionProperties(
        @NotNull URI issuer,
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration accessTtl,
        @NotNull Duration refreshTtl,
        @NotNull Duration refreshGrace,
        @NotNull @Valid Cookie cookie) {

    /**
     * La cookie del token de refresco.
     *
     * @param name nombre parametrizable para poder convivir con otra instancia en
     *     el mismo dominio
     * @param path limitada a las rutas de sesion (ADR-0003). Con esto el navegador
     *     no manda la cookie en ninguna otra peticion, lo que reduce a la vez la
     *     superficie de CSRF y la posibilidad de que aparezca en un registro
     * @param secure solo por HTTPS. Se puede apagar unicamente para desarrollo
     *     detras de HTTP sin cifrar; en la nube nunca
     */
    public record Cookie(@NotBlank String name, @NotBlank String path, boolean secure) {}
}
