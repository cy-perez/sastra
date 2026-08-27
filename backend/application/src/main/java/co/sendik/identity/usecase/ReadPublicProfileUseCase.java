package co.sendik.identity.usecase;

import co.sendik.identity.dto.PublicProfileView;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.UserRepository;
import java.util.Optional;

/**
 * La identidad publica de alguien. HU-009, criterios 18 y 19.
 *
 * <p>Existe aparte de {@link ReadProfileUseCase} y no como un parametro suyo. Aquel dice
 * en su javadoc que su razon de ser es que «el identificador sale del token, asi que nadie
 * puede leer el perfil de otra persona»; abrirlo para que lea el de cualquiera seria
 * quitarle justo lo que lo hace seguro, y ademas devuelve el {@link User} entero, con el
 * correo y la fecha de nacimiento dentro.
 *
 * <p>Este devuelve {@link PublicProfileView}, que solo tiene lo que el sitio dice en voz
 * alta. Es la frontera: lo que no quepa en ese tipo no sale de {@code identity} hacia una
 * pantalla publica.
 *
 * <p><strong>Una cuenta cerrada no tiene perfil publico.</strong> Cerrar anonimiza y no
 * borra (RN-009), asi que la fila sigue ahi con un nombre que ya no es de nadie. Servirlo
 * como perfil seria exhibir el rastro de una persona que pidio irse, y ademas mostraria
 * un nombre anonimizado como si fuera el de un vendedor. Responde vacio, que el borde
 * convierte en 404.
 */
public class ReadPublicProfileUseCase {

    private final UserRepository usuarios;

    public ReadPublicProfileUseCase(UserRepository usuarios) {
        this.usuarios = usuarios;
    }

    public Optional<PublicProfileView> execute(UserId usuario) {
        return usuarios.buscarPorId(usuario)
                .filter(persona -> persona.status() != UserStatus.CLOSED)
                .map(persona -> new PublicProfileView(
                        persona.id(), persona.displayName().value(), persona.avatarKey()));
    }
}
