package co.sendik.identity.usecase;

import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ConsentRepository;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;

/**
 * Reune todo lo que Sendik guarda de una persona. Criterio 22.
 *
 * <p>Es el derecho a conocer de la Ley 1581, y por eso se sirve entero y de una
 * vez en lugar de repartido por pantallas: lo que la ley reconoce no es poder
 * mirar el perfil, es poder llevarse lo que hay.
 *
 * <p>El identificador viene del token, nunca de la peticion. Un endpoint que
 * aceptara el identificador de quien exportar seria una forma de descargarse los
 * datos de cualquiera.
 */
public class ExportUserDataUseCase {

    private final UserRepository usuarios;
    private final ConsentRepository consentimientos;
    private final RefreshTokenRepository refrescos;
    private final Clock reloj;

    public ExportUserDataUseCase(
            UserRepository usuarios, ConsentRepository consentimientos, RefreshTokenRepository refrescos, Clock reloj) {
        this.usuarios = usuarios;
        this.consentimientos = consentimientos;
        this.refrescos = refrescos;
        this.reloj = reloj;
    }

    public UserDataExport execute(UserId usuario) {
        Instant ahora = reloj.instant();

        // Puede no existir: cerrar la cuenta no invalida el token de acceso que ya
        // estaba emitido, y ese sigue sirviendo hasta quince minutos (ADR-0003).
        User cuenta = usuarios.buscarPorId(usuario).orElseThrow(AccountNoLongerExistsException::new);

        return new UserDataExport(
                ahora,
                new UserDataExport.Cuenta(
                        cuenta.id().toString(),
                        cuenta.email().value(),
                        cuenta.displayName().value(),
                        cuenta.birthDate().value(),
                        cuenta.city() == null ? null : cuenta.city().value(),
                        cuenta.phone() == null ? null : cuenta.phone().value(),
                        cuenta.locale().name().toLowerCase(java.util.Locale.ROOT),
                        cuenta.status().name(),
                        cuenta.tieneElCorreoVerificado(),
                        cuenta.emailVerifiedAt(),
                        cuenta.roles().stream().map(Role::name).sorted().toList(),
                        cuenta.createdAt()),
                consentimientos.listarDe(usuario).stream()
                        .map(consentimiento -> new UserDataExport.Consentimiento(
                                consentimiento.document().name(),
                                consentimiento.version(),
                                consentimiento.acceptedAt()))
                        .toList(),
                refrescos.listarSesionesActivasDe(usuario, ahora).stream()
                        .map(token ->
                                new UserDataExport.Sesion(token.userAgent(), token.createdAt(), token.expiresAt()))
                        .toList());
    }
}
