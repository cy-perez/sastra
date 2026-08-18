package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.CloseAccountCommand;
import co.sastra.identity.exception.CloseConfirmationMismatchException;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Criterio 23 y derecho de supresion de la Ley 1581. */
@ExtendWith(MockitoExtension.class)
class CloseAccountUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private MailSender correo;

    private CloseAccountUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new CloseAccountUseCase(usuarios, refrescos, correo, Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(30)));
    }

    private void conCuenta() {
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
    }

    @Test
    void deberia_cerrar_anonimizar_y_cortar_las_sesiones() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "ana@correo.co"));

        verify(refrescos).revocarTodasDe(usuario.id(), AHORA);
        verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }

    /**
     * El aviso sale antes de anonimizar. Despues ya no hay direccion a la que
     * escribir, y ese correo es lo que permite a la persona reaccionar si no fue
     * ella quien lo pidio.
     */
    @Test
    void deberia_avisar_antes_de_borrar_el_correo() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "ana@correo.co"));

        InOrder orden = inOrder(correo, usuarios);
        orden.verify(correo).enviarAvisoDeCuentaCerrada(usuario);
        orden.verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }

    /**
     * Cerrar no se deshace: la confirmacion escrita es lo unico que separa un clic
     * mal dado de perder el acceso.
     */
    @Test
    void no_deberia_cerrar_si_la_confirmacion_no_coincide_criterio_23() {
        conCuenta();

        assertThatThrownBy(() -> caso.execute(new CloseAccountCommand(usuario.id(), "otra@correo.co")))
                .isInstanceOf(CloseConfirmationMismatchException.class);

        verify(usuarios, never()).cerrarYAnonimizar(usuario.id(), AHORA);
        verifyNoInteractions(refrescos, correo);
    }

    @Test
    void no_deberia_cerrar_con_la_confirmacion_vacia() {
        conCuenta();

        assertThatThrownBy(() -> caso.execute(new CloseAccountCommand(usuario.id(), "   ")))
                .isInstanceOf(CloseConfirmationMismatchException.class);
    }

    // Quien escribe su correo con mayusculas no se esta equivocando de cuenta: se
    // compara normalizado, igual que en el ingreso.
    @Test
    void deberia_aceptar_la_confirmacion_con_otras_mayusculas_y_espacios() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "  ANA@Correo.CO  "));

        verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }
}
