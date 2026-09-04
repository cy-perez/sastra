package co.sendik.shared.rest;

import java.time.Clock;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enchufa {@link RateLimitInterceptor} al despachador.
 *
 * <p>Esta mitad vive en {@code presentation} y no en {@code bootstrap} porque
 * {@code WebMvcConfigurer} es de Spring MVC, que solo esta en el classpath de este
 * modulo. La otra mitad, los numeros, llega en {@link RateLimitSettings} desde
 * {@code bootstrap}, que es quien ve la configuracion.
 */
@Configuration
public class RateLimitRegistration implements WebMvcConfigurer {

    private final RateLimitInterceptor limite;

    public RateLimitRegistration(RateLimitSettings ajustes, ClientIpHasher hasherDeIp, Clock reloj) {
        this.limite = new RateLimitInterceptor(
                new RateLimiter(ajustes.maxDeCredenciales(), ajustes.ventanaDeCredenciales(), ajustes.maxDeOrigenes()),
                new RateLimiter(ajustes.maxDeSesion(), ajustes.ventanaDeSesion(), ajustes.maxDeOrigenes()),
                new RateLimiter(ajustes.maxDeCuenta(), ajustes.ventanaDeCuenta(), ajustes.maxDeOrigenes()),
                hasherDeIp,
                reloj);
    }

    /**
     * Se acota a los dos prefijos que el interceptor conoce: el interceptor ya decide por
     * ruta, pero limitar el patron aqui evita que corra en cada peticion del catalogo, que
     * es publico y de lectura y no tiene a quien contar.
     *
     * <p>{@code /api/v1/users} entra desde HU-012. Hasta entonces ninguna ruta autenticada
     * tenia tope, asi que cualquier cuenta registrada podia repetir sin freno una lectura
     * que ejecuta un agregado.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(limite).addPathPatterns("/api/v1/auth/**", "/api/v1/users/**");
    }
}
