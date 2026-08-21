package co.sastra.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.config.SessionProperties;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.TokenFamilyId;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.UserStatus;
import co.sastra.identity.port.out.AccessTokenIssuer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Emision del token de acceso (ADR-0003).
 *
 * <p>Se verifica decodificando con la misma clave, no leyendo la cadena: lo que
 * importa es lo que el borde va a leer del token, y el borde lo lee con un
 * decodificador. Con aserciones sobre texto, un cambio de algoritmo pasaria
 * inadvertido.
 *
 * <p>Al decodificador se le quita la validacion de fechas a proposito. El momento
 * de emision esta fijado en una constante, asi que con la validacion puesta la
 * suite empezaria a fallar sola en cuanto ese instante quedara en el pasado: seria
 * una prueba con fecha de caducidad. Lo que se comprueba aqui es que la caducidad
 * se calcula bien, y eso se afirma sobre el claim.
 */
class JwtAccessTokenIssuerTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");
    private static final String SECRETO = "un-secreto-de-mas-de-treinta-y-dos-caracteres";
    private static final String CORREO = "ana@correo.co";

    private final SessionProperties propiedades = propiedadesCon(SECRETO);

    private final JwtAccessTokenIssuer emisor = new JwtAccessTokenIssuer(propiedades);

    private final NimbusJwtDecoder decodificador = decodificadorCon(SECRETO);

    private static SessionProperties propiedadesCon(String secreto) {
        return new SessionProperties(
                URI.create("https://sastra.co"),
                secreto,
                Duration.ofMinutes(15),
                Duration.ofDays(30),
                Duration.ofSeconds(10),
                new SessionProperties.Cookie("sastra_refresh", "/api/v1/auth", true));
    }

    private static NimbusJwtDecoder decodificadorCon(String secreto) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(JwtAccessTokenIssuer.claveDeFirma(secreto))
                .build();
        decodificador.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decodificador;
    }

    private User cuenta(UserId id, boolean correoVerificado) {
        return User.rehidratar(
                id,
                new Email(CORREO),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                // Sin foto de perfil: esta prueba no trata de eso.
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                correoVerificado ? AHORA.minus(Duration.ofDays(1)) : null,
                EnumSet.of(Role.BUYER),
                AHORA.minus(Duration.ofDays(30)));
    }

    private Jwt emitirYDecodificar(User usuario, TokenFamilyId sesion) {
        return decodificador.decode(emisor.emitir(usuario, sesion, AHORA).value());
    }

    @Test
    void deberia_firmar_un_token_que_el_borde_puede_verificar_con_la_misma_clave() {
        UserId id = UserId.nuevo();

        Jwt token = emitirYDecodificar(cuenta(id, true), TokenFamilyId.nueva());

        assertThat(token.getSubject()).isEqualTo(id.toString());
        assertThat(token.getIssuer()).hasToString("https://sastra.co");
    }

    /** RN-007: quince minutos, contados desde el momento que entra, no desde el reloj del sistema. */
    @Test
    void deberia_caducar_a_los_quince_minutos_del_momento_recibido() {
        AccessTokenIssuer.IssuedAccessToken emitido =
                emisor.emitir(cuenta(UserId.nuevo(), true), TokenFamilyId.nueva(), AHORA);

        assertThat(emitido.expiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
        assertThat(decodificador.decode(emitido.value()).getExpiresAt()).isEqualTo(emitido.expiresAt());
    }

    /**
     * El claim que decide si la cuenta puede operar. Que viaje en el token es lo que
     * evita ir a la base de datos en cada peticion, y por eso tiene que reflejar el
     * estado real de la cuenta en el momento de emitir.
     */
    @Test
    void deberia_declarar_si_el_correo_esta_verificado() {
        assertThat(emitirYDecodificar(cuenta(UserId.nuevo(), true), TokenFamilyId.nueva())
                        .getClaimAsBoolean("email_verified"))
                .isTrue();

        assertThat(emitirYDecodificar(cuenta(UserId.nuevo(), false), TokenFamilyId.nueva())
                        .getClaimAsBoolean("email_verified"))
                .isFalse();
    }

    @Test
    void deberia_llevar_los_roles_de_la_cuenta() {
        assertThat(emitirYDecodificar(cuenta(UserId.nuevo(), true), TokenFamilyId.nueva())
                        .getClaimAsStringList("roles"))
                .containsExactly("BUYER");
    }

    /**
     * El {@code sid} es lo que permite al criterio 17 senalar cual de las sesiones
     * activas es la que se esta usando, porque la cookie de refresco no llega a
     * /users/me. Sin el, la lista de sesiones no puede marcar la actual.
     */
    @Test
    void deberia_llevar_la_sesion_a_la_que_pertenece_el_token() {
        TokenFamilyId sesion = TokenFamilyId.nueva();

        assertThat(emitirYDecodificar(cuenta(UserId.nuevo(), true), sesion).getClaimAsString("sid"))
                .isEqualTo(sesion.toString());
    }

    /**
     * Ni el correo, ni el nombre, ni la fecha de nacimiento. Un token de acceso viaja
     * en cada peticion y se queda en registros y en caches intermedias: lo que lleve
     * dentro se filtra por todas partes (docs/operacion/datos-personales.md).
     *
     * <p>Se mira el contenido decodificado y no solo las claves esperadas, porque el
     * fallo que se quiere atrapar es que alguien anada un claim nuevo con el correo
     * dentro, con cualquier nombre.
     */
    @Test
    void nunca_deberia_llevar_datos_personales_dentro() {
        Jwt token = emitirYDecodificar(cuenta(UserId.nuevo(), true), TokenFamilyId.nueva());

        assertThat(token.getClaims()).doesNotContainKeys("email", "name", "birth_date", "phone", "city");
        assertThat(token.getClaims().values().toString()).doesNotContain(CORREO).doesNotContain("Ana Maria");
    }

    /**
     * Un token firmado con otro secreto no se acepta. Parece obvio, y es la unica
     * prueba que demuestra que la firma sirve de algo: sin ella, un cambio a un
     * algoritmo sin firma seguiria pasando todas las demas.
     */
    @Test
    void no_deberia_aceptarse_un_token_firmado_con_otro_secreto() {
        JwtAccessTokenIssuer otroEmisor =
                new JwtAccessTokenIssuer(propiedadesCon("otro-secreto-de-mas-de-treinta-y-dos-caracteres"));

        String ajeno = otroEmisor
                .emitir(cuenta(UserId.nuevo(), true), TokenFamilyId.nueva(), AHORA)
                .value();

        assertThatThrownBy(() -> decodificador.decode(ajeno)).isInstanceOf(JwtException.class);
    }
}
