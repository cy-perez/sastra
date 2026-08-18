package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.UpdateProfileCommand;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.City;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.Phone;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Criterio 21, la parte que se guarda de una vez. */
@ExtendWith(MockitoExtension.class)
class UpdateProfileUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-18T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    private UpdateProfileUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new UpdateProfileUseCase(usuarios);

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 18),
                AHORA.minus(Duration.ofDays(30)));

        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
    }

    private User guardado() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(usuarios).actualizar(captor.capture());
        return captor.getValue();
    }

    @Test
    void deberia_guardar_nombre_ciudad_y_telefono() {
        caso.execute(new UpdateProfileCommand(usuario.id(), "Ana", "Medellin", "3001234567"));

        assertThat(guardado().displayName()).isEqualTo(new DisplayName("Ana"));
        assertThat(guardado().city()).isEqualTo(new City("Medellin"));
        assertThat(guardado().phone()).isEqualTo(new Phone("3001234567"));
    }

    /**
     * Vacio y ausente son lo mismo: la persona no quiere tener ese dato. Sin esto,
     * borrar la ciudad seria imposible desde un formulario.
     */
    @Test
    void deberia_dejar_quitar_la_ciudad_y_el_telefono() {
        caso.execute(new UpdateProfileCommand(usuario.id(), "Ana", "  ", null));

        assertThat(guardado().city()).isNull();
        assertThat(guardado().phone()).isNull();
    }

    // El telefono se normaliza al entrar: el mismo numero no puede existir escrito
    // de cinco formas distintas.
    @Test
    void deberia_normalizar_el_telefono() {
        caso.execute(new UpdateProfileCommand(usuario.id(), "Ana", null, "+57 (300) 123-4567"));

        assertThat(guardado().phone()).isEqualTo(new Phone("+573001234567"));
    }

    /**
     * El correo no se toca aqui. Cambiarlo exige verificar el nuevo antes de
     * reemplazar el anterior, y por eso tiene su propio camino.
     */
    @Test
    void nunca_deberia_cambiar_el_correo_criterio_21() {
        caso.execute(new UpdateProfileCommand(usuario.id(), "Ana", "Medellin", null));

        assertThat(guardado().email()).isEqualTo(new Email("ana@correo.co"));
    }

    @Test
    void deberia_rechazar_un_nombre_que_el_dominio_no_admite() {
        assertThatThrownBy(() -> caso.execute(new UpdateProfileCommand(usuario.id(), "A", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_telefono_que_no_lo_parece() {
        assertThatThrownBy(() -> caso.execute(new UpdateProfileCommand(usuario.id(), "Ana", null, "no-es-numero")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
