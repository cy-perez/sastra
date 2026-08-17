package co.sastra.shared.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Entrada de Spring Security al proyecto (HU-001, ADR-0003).
 *
 * <p>Se declara con la DSL de lambdas y un bean {@link SecurityFilterChain}.
 * {@code WebSecurityConfigurerAdapter} no existe desde hace varias versiones.
 *
 * <p><strong>Lo ultimo es {@code denyAll} y no {@code authenticated}.</strong> En
 * esta rebanada no hay todavia ninguna ruta autenticada, asi que cualquier
 * endpoint que aparezca sin declarar su autorizacion queda cerrado y se nota de
 * inmediato. Con {@code authenticated} quedaria a merced de que alguien monte la
 * autenticacion mas adelante, que es como se abren rutas sin querer.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        http
                // Sin CSRF porque no hay sesion ni cookie de autenticacion todavia:
                // la API es sin estado y el token de acceso viaja en la cabecera.
                // Cuando la rebanada B agregue la cookie de refresco, esta llega
                // con SameSite=Strict y ruta limitada a /api/v1/auth (ADR-0003).
                // El origen permitido lo aporta un bean CorsConfigurationSource que
                // vive en bootstrap: la lista sale de la configuracion, y la
                // configuracion es de infrastructure, una capa que este modulo no ve.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        // Registro y verificacion: publicas por definicion, quien
                        // las usa todavia no tiene cuenta.
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        // Sondas de estado. Que responda /actuator/flyway o no lo
                        // decide management.endpoints.web.exposure.include, que en
                        // prod solo deja health e info.
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/flyway")
                        .permitAll()
                        // Documentacion de la API. En prod springdoc esta apagado,
                        // asi que estas rutas ni existen.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                // Ni formulario de acceso ni autenticacion basica: esto es una API.
                .httpBasic(basica -> basica.disable())
                .formLogin(formulario -> formulario.disable());

        return http.build();
    }
}
