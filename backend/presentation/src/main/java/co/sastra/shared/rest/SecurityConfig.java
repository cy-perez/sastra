package co.sastra.shared.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Entrada de Spring Security al proyecto (HU-001, ADR-0003).
 *
 * <p>Se declara con la DSL de lambdas y un bean {@link SecurityFilterChain}.
 * {@code WebSecurityConfigurerAdapter} no existe desde hace varias versiones.
 *
 * <p><strong>Lo ultimo es {@code denyAll} y no {@code authenticated}.</strong> Con
 * {@code authenticated}, un endpoint nuevo al que se olvide declararle su
 * autorizacion queda accesible para cualquiera con sesion, que en un marketplace es
 * cualquiera que se registre. Con {@code denyAll} devuelve 403 hasta que alguien lo
 * declare, y eso se nota en la primera prueba.
 *
 * <p><strong>Por que sigue sin CSRF con una cookie de por medio.</strong> La cookie
 * del refresco es {@code SameSite=Strict} y de ruta limitada a {@code /api/v1/auth},
 * asi que un formulario o un script de otro sitio no consigue que el navegador la
 * envie. El resto de la API se autentica con la cabecera {@code Authorization}, que
 * ningun sitio ajeno puede poner. Si algun dia la cookie tuviera que ser
 * {@code SameSite=Lax} para admitir un flujo de terceros, esta decision se cae y hay
 * que meter el token de CSRF.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http) throws Exception {
        http
                // El origen permitido lo aporta un bean CorsConfigurationSource que
                // vive en bootstrap: la lista sale de la configuracion, y la
                // configuracion es de infrastructure, una capa que este modulo no ve.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        // Registro, verificacion y las tres rutas de sesion: publicas
                        // por definicion. Quien las usa no tiene token de acceso
                        // todavia, y presenta otra credencial (la contrasena, o la
                        // cookie de refresco).
                        .requestMatchers("/api/v1/auth/**")
                        .permitAll()
                        // Todo lo que actua sobre la propia cuenta exige token.
                        .requestMatchers("/api/v1/users/**")
                        .authenticated()
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
                // El decodificador lo aporta infrastructure, que es quien tiene el
                // secreto de firma. Aqui solo se declara que la cadena lo use.
                .oauth2ResourceServer(
                        recursos -> recursos.jwt(jwt -> jwt.jwtAuthenticationConverter(deJwtAAutoridades())))
                // Ni formulario de acceso ni autenticacion basica: esto es una API.
                .httpBasic(basica -> basica.disable())
                .formLogin(formulario -> formulario.disable());

        return http.build();
    }

    /**
     * Traduce el claim {@code roles} del token en autoridades de Spring Security.
     *
     * <p>Hay que declararlo porque el convertidor por omision lee {@code scope} y
     * {@code scp}, que son de OAuth2 y este sistema no emite: sin esto, todo token
     * llegaria sin ninguna autoridad y cualquier regla por rol quedaria muerta sin
     * dar error.
     *
     * <p>El prefijo {@code ROLE_} es el que espera {@code hasRole}. Los nombres en el
     * token van sin el, porque un token no tiene por que hablar el dialecto de un
     * framework concreto.
     */
    private static JwtAuthenticationConverter deJwtAAutoridades() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("roles");
        autoridades.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }
}
