package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.port.out.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Criterio 21, lado de lectura.
 *
 * <p>El caso es de una linea y aun asi tiene prueba, porque lo que se comprueba no
 * es la linea sino la decision: una cuenta que ya no existe no se traduce en un
 * perfil vacio, se traduce en un error con nombre. Ese comportamiento es el que
 * separa una respuesta 404 honesta de un perfil fantasma con todos los campos en
 * blanco, y no lo garantiza el tipo de retorno.
 */
@ExtendWith(MockitoExtension.class)
class ReadProfileUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    private ReadProfileUseCase caso;
    private UserId usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new ReadProfileUseCase(usuarios);
        usuario = UserId.nuevo();
    }

    @Test
    void deberia_devolver_la_cuenta_de_quien_pregunta() {
        User cuenta = User.registrar(
                usuario,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 20),
                AHORA.minus(Duration.ofDays(30)));
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuenta));

        assertThat(caso.execute(usuario)).isSameAs(cuenta);
    }

    /**
     * El token de acceso vive quince minutos y no se invalida al cerrar la cuenta
     * (ADR-0003), asi que este caso llega con credencial valida y sujeto
     * inexistente. Es el unico camino de error que tiene, y tiene que tener nombre.
     */
    @Test
    void deberia_fallar_si_la_cuenta_ya_no_existe() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(usuario)).isInstanceOf(AccountNoLongerExistsException.class);
    }
}
