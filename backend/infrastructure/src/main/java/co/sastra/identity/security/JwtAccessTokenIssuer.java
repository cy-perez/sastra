package co.sastra.identity.security;

import co.sastra.identity.config.SessionProperties;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.User;
import co.sastra.identity.port.out.AccessTokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Emite el token de acceso como JWT firmado con HS256 (ADR-0003).
 *
 * <p>Firma simetrica y no un par de claves porque quien emite y quien valida son el
 * mismo servicio. El dia que otro necesite validar tokens sin poder emitirlos, se
 * pasa a RS256 con un JWKS publicado y este adaptador es lo unico que cambia: el
 * puerto no menciona el formato.
 *
 * <p><strong>Que va dentro y que no.</strong> Van el identificador, si el correo
 * esta verificado y los roles, que es lo que la autorizacion necesita en cada
 * peticion sin volver a consultar la base. No van el correo ni el nombre: un token
 * de acceso viaja en cada llamada y acaba en registros intermedios, asi que
 * cualquier dato personal que lleve se multiplica por todas partes
 * (docs/operacion/datos-personales.md). El cliente ya recibe esos datos en el
 * cuerpo de la respuesta de sesion.
 *
 * <p>Los 15 minutos de RN-007 son lo que hace tolerable no poder revocar un token
 * de acceso: si alguien pierde el rol de vendedor, el peor caso es que su token
 * siga diciendo que lo tiene durante ese cuarto de hora. Lo revocable es el
 * refresco, que si vive en la base de datos.
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final SessionProperties sesion;
    private final JwtEncoder encoder;

    public JwtAccessTokenIssuer(SessionProperties sesion) {
        this.sesion = sesion;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(claveDeFirma(sesion.secret())));
    }

    @Override
    public IssuedAccessToken emitir(User usuario, Instant ahora) {
        Instant caduca = ahora.plus(sesion.accessTtl());

        JwtClaimsSet contenido = JwtClaimsSet.builder()
                .issuer(sesion.issuer().toString())
                .subject(usuario.id().toString())
                .issuedAt(ahora)
                .expiresAt(caduca)
                .claim("email_verified", usuario.tieneElCorreoVerificado())
                .claim("roles", rolesDe(usuario))
                .build();

        String valor = encoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), contenido))
                .getTokenValue();

        return new IssuedAccessToken(valor, caduca);
    }

    private static List<String> rolesDe(User usuario) {
        return usuario.roles().stream().map(Role::name).sorted().toList();
    }

    /**
     * La clave de firma, compartida con el decodificador.
     *
     * <p>Se derivan los bytes del secreto en UTF-8 tal cual, sin funcion de
     * derivacion: el secreto es aleatorio y de al menos 256 bits, no una contrasena
     * que alguien pueda adivinar. La validacion de {@link SessionProperties} es lo
     * que sostiene esa suposicion, y por eso su minimo no es negociable.
     */
    public static SecretKey claveDeFirma(String secreto) {
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
