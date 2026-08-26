package co.sendik.shared.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

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
// Activa @PreAuthorize. Sin esta anotacion, Spring **ignora** esas reglas y no avisa de
// nada: el metodo queda anotado, se lee como protegido y no lo esta. Se enciende aqui
// porque las rutas de revision de HU-002 la usan como segunda cerradura, ademas de la
// regla por ruta de mas abajo.
@EnableMethodSecurity
public class SecurityConfig {

    /** La forma de un UUID. Un identificador de publicacion, y nada mas. */
    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    @Bean
    SecurityFilterChain cadenaDeFiltros(HttpSecurity http, ExposedFeatures expuestas) throws Exception {
        // Con FEATURE_PUBLISHING apagada no se declara la regla de rol de las rutas de
        // moderacion. Sin esto respondian 403 con la bandera apagada, y un 403 confirma
        // que la funcionalidad esta ahi: el criterio 3 pide 404. Ver ExposedFeatures.
        boolean catalogoExpuesto = expuestas.publishing();
        boolean verificacionExpuesta = expuestas.sellerVerification();

        http
                // El origen permitido lo aporta un bean CorsConfigurationSource que
                // vive en bootstrap: la lista sale de la configuracion, y la
                // configuracion es de infrastructure, una capa que este modulo no ve.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> {
                    rutas
                            // Registro, verificacion y las tres rutas de sesion: publicas
                            // por definicion. Quien las usa no tiene token de acceso
                            // todavia, y presenta otra credencial (la contrasena, o la
                            // cookie de refresco).
                            .requestMatchers("/api/v1/auth/**")
                            .permitAll()
                            // El catalogo de entidades financieras. Token si, rol no: son
                            // veintiocho nombres de bancos, iguales para todo el mundo, y no
                            // hay nada personal que proteger. Se pide token porque solo lo
                            // necesita quien se esta verificando.
                            .requestMatchers("/api/v1/financial-institutions")
                            .authenticated()
                            // Leer una publicacion es publico, y es deliberado: es la ruta
                            // que va a usar el catalogo. **Quien decide que se ve no es esta
                            // regla sino el caso de uso**, que responde vacio tanto si no
                            // existe como si no es para quien pregunta, y sale como 404 y
                            // nunca 403 (criterio 33). Con "authenticated" aqui, un 401
                            // delataria que la publicacion existe.
                            //
                            // **Casa un identificador, no cualquier segmento.** Con
                            // `/api/v1/listings/*` esta regla se tragaria tambien la
                            // bandeja del moderador el dia que exista —`/pending`,
                            // `/queue`, el nombre que sea es un segmento igual que un
                            // id— y la dejaria publica, con el motivo del rechazo y la
                            // nota de publicaciones ajenas dentro. Y el denyAll del
                            // final no salva nada: permitAll casa primero y gana.
                            .requestMatchers(
                                    RegexRequestMatcher.regexMatcher(HttpMethod.GET, "/api/v1/listings/" + UUID))
                            .permitAll();

                    // Revision de verificaciones: ver la cedula de otra persona y decidir
                    // sobre su solicitud. **Rol, no solo token.**
                    //
                    // Vive en su propia ruta y no bajo /users/** precisamente por esto:
                    // alli la regla es "autenticado", y cualquiera con token podria
                    // aprobar su propia verificacion. Los metodos llevan ademas
                    // @PreAuthorize, que es redundante a proposito: mover un endpoint de
                    // sitio no se lleva su autorizacion por delante.
                    //
                    // **Solo se declara si la verificacion esta expuesta**, por lo mismo
                    // que las del catalogo: con FEATURE_SELLER_VERIFICATION apagada, el
                    // controlador no se crea y esta regla contestaba 403 en el filtro,
                    // antes de que nadie buscara un manejador. HU-002 pide 404 con la
                    // bandera apagada, igual que HU-007.
                    if (verificacionExpuesta) {
                        rutas.requestMatchers("/api/v1/verifications/**").hasRole("MODERATOR");
                    } else {
                        // Con la bandera apagada hace falta igualmente una regla, y no vale
                        // omitirla: sin ninguna, la peticion cae en el denyAll del final y
                        // vuelve a salir 403, que es lo que se queria evitar. Con
                        // "authenticated" atraviesa la cadena, no encuentra manejador
                        // —el controlador no existe— y sale el 404 que corresponde.
                        //
                        // Sigue exigiendo token a proposito: si algun dia apareciera un
                        // manejador bajo esta ruta sin actualizar esto, quedaria detras de
                        // una sesion y no abierto.
                        rutas.requestMatchers("/api/v1/verifications/**").authenticated();
                    }

                    // Decision del moderador sobre una publicacion. **Rol, no solo
                    // token**, y por metodo y patron en lugar de por prefijo: la historia
                    // pone estas rutas bajo /listings/{id}, que es donde tambien escribe
                    // el vendedor, asi que no hay prefijo que las separe. Van antes que la
                    // regla generica de /listings/**, que si no se las tragaria como
                    // "autenticado" y cualquiera con sesion aprobaria su propia
                    // publicacion.
                    //
                    // Los metodos llevan ademas @PreAuthorize, redundante a proposito,
                    // igual que en la revision de verificaciones.
                    //
                    // **Solo se declara si el catalogo esta expuesto.** Con la bandera
                    // apagada no hay controlador que proteger, y esta regla respondia 403
                    // en el filtro antes de que nadie buscara un manejador. El criterio 3
                    // pide 404: la funcionalidad no esta, y un 403 diria que si.
                    if (catalogoExpuesto) {
                        rutas.requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/listings/*/approval",
                                        "/api/v1/listings/*/rejection",
                                        "/api/v1/listings/*/removal")
                                .hasRole("MODERATOR");
                    }

                    if (catalogoExpuesto) {
                        // El arbol de categorias. Publico y sin token: son treinta y siete
                        // nombres iguales para todo el mundo, y el catalogo publico pedira
                        // esto mismo. Solo se declara con la bandera encendida, por lo
                        // mismo que las de moderacion.
                        rutas.requestMatchers(HttpMethod.GET, "/api/v1/categories")
                                .permitAll();
                    } else {
                        // Con la bandera apagada hace falta una regla igualmente: sin
                        // ninguna, la peticion cae en el denyAll del final y sale 403,
                        // que es lo que el criterio 3 no quiere. Con "authenticated"
                        // atraviesa la cadena, no encuentra manejador y sale el 404.
                        rutas.requestMatchers("/api/v1/categories").authenticated();
                    }

                    rutas
                            // Todo lo demas del catalogo lo hace el vendedor sobre lo suyo.
                            // Que sea suyo lo comprueba el repositorio, que solo devuelve la
                            // publicacion si es de quien pregunta.
                            .requestMatchers("/api/v1/listings/**")
                            .authenticated()
                            // Todo lo que actua sobre la propia cuenta exige token. Aqui
                            // entra tambien la verificacion de vendedor
                            // (/api/v1/users/me/verification), que es de quien la pide.
                            //
                            // **Las rutas del moderador no van a caber aqui.** Aprobar o
                            // rechazar la verificacion de OTRA persona, y ver su cedula,
                            // exige `hasRole("MODERATOR")` y no solo estar autenticado: iran
                            // en su propia regla, antes de esta, cuando existan. Con la
                            // regla generica y una ruta bajo /users/**, cualquier persona
                            // con token podria aprobarse a si misma.
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
                            // Los archivos del almacen publico: fotos de perfil hoy, tomas
                            // de producto en Fase 2. Publicas por definicion, igual que
                            // cualquier imagen de un catalogo.
                            //
                            // **Solo existen con el almacen local.** En la nube las sirve
                            // Cloud Storage y esta ruta no responde nada (ADR-0018), que es
                            // lo que se quiere: el backend no es un servidor de archivos.
                            // Lo reservado —cedula y selfie— no se sirve por ninguna ruta,
                            // ni aqui ni alli (RN-046).
                            .requestMatchers("/archivos/**")
                            .permitAll()
                            .anyRequest()
                            .denyAll();
                })
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
