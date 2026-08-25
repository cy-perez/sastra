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
                hasherDeIp,
                reloj);
    }

    /**
     * Se acota a {@code /api/v1/auth}: el interceptor ya decide por ruta, pero
     * limitar el patron aqui evita que corra en cada peticion del catalogo el dia
     * que exista.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(limite).addPathPatterns("/api/v1/auth/**");
    }
}
