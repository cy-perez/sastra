package co.sastra.config;

import co.sastra.shared.config.AppProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Origenes con permiso para llamar a la API.
 *
 * <p>Vive en {@code bootstrap} porque necesita dos cosas que ningun otro modulo
 * ve a la vez: la configuracion tipada, que es de {@code infrastructure}, y el
 * tipo de Spring Web que consume la cadena de seguridad, que es de
 * {@code presentation}. Spring Security recoge este bean por si solo.
 *
 * <p>La lista sale de {@code CORS_ALLOWED_ORIGINS} y no lleva comodin: un
 * {@code *} aqui permitiria a cualquier sitio hacer peticiones en nombre de
 * quien tenga la sesion abierta.
 */
@Configuration
public class CorsWiring {

    @Bean
    // El nombre del bean importa: Spring Security lo busca por el nombre
    // convencional corsConfigurationSource antes que por tipo.
    CorsConfigurationSource corsConfigurationSource(AppProperties app) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(List.copyOf(app.corsAllowedOrigins()));
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "Idempotency-Key"));
        // Necesario para la cookie del token de refresco, que llega con la
        // rebanada B. Sin esto, el navegador no la enviaria ni la aceptaria.
        configuracion.setAllowCredentials(true);
        configuracion.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }
}
