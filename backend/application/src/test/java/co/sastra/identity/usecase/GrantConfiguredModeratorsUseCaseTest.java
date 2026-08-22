package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.UserStatus;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** HU-006: el rol de moderador que se otorga por configuracion al arrancar. */
class GrantConfiguredModeratorsUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-22T15:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private final UserRepository usuarios = mock(UserRepository.class);

    private final GrantConfiguredModeratorsUseCase caso = new GrantConfiguredModeratorsUseCase(usuarios, RELOJ);

    private User cuenta(String correo) {
        return User.rehidratar(
                UserId.nuevo(),
                new Email(correo),
                new DisplayName("Quien Modera"),
                new BirthDate(LocalDate.of(1990, 1, 1)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                AHORA,
                Set.of(Role.BUYER),
                AHORA);
    }

    /**
     * El caso normal, y el que importa que no haga nada: sin lista configurada no se
     * consulta ni una cuenta.
     */
    @Test
    void deberia_no_tocar_nada_con_la_lista_vacia() {
        GrantConfiguredModeratorsUseCase.Resultado resultado = caso.execute(List.of());

        assertThat(resultado.huboAlgoQueDecir()).isFalse();
        verify(usuarios, never()).buscarPorCorreo(any());
        verify(usuarios, never()).otorgarRol(any(), any(), any());
    }

    @Test
    void deberia_otorgar_el_rol_de_moderador_a_la_cuenta_configurada() {
        User quien = cuenta("moderadora@sastra.co");
        when(usuarios.buscarPorCorreo(new Email("moderadora@sastra.co"))).thenReturn(Optional.of(quien));

        GrantConfiguredModeratorsUseCase.Resultado resultado = caso.execute(List.of("moderadora@sastra.co"));

        verify(usuarios).otorgarRol(quien.id(), Role.MODERATOR, AHORA);
        assertThat(resultado.otorgados()).containsExactly("moderadora@sastra.co");
    }

    /**
     * El correo llega como lo escribio quien configuro el entorno, y ahi cabe cualquier
     * mayuscula. Sin normalizar, `Moderadora@Sastra.co` no encontraria la cuenta y la
     * persona se quedaria sin acceso sin que nada fallara. Lo normaliza el propio
     * objeto de valor (RN-001), y esta prueba fija que se pasa por el.
     */
    @Test
    void deberia_encontrar_la_cuenta_aunque_el_correo_venga_con_mayusculas() {
        User quien = cuenta("moderadora@sastra.co");
        when(usuarios.buscarPorCorreo(new Email("moderadora@sastra.co"))).thenReturn(Optional.of(quien));

        caso.execute(List.of("  Moderadora@Sastra.CO  "));

        verify(usuarios).otorgarRol(quien.id(), Role.MODERATOR, AHORA);
    }

    /**
     * <strong>No crea cuentas.</strong> Es la mitad importante del mecanismo: si de aqui
     * pudiera salir una cuenta nueva con rol de moderador, esto seria una puerta trasera
     * y no una forma de conceder un rol.
     */
    @Test
    void deberia_no_crear_la_cuenta_cuando_el_correo_configurado_no_existe() {
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.empty());

        GrantConfiguredModeratorsUseCase.Resultado resultado = caso.execute(List.of("fantasma@sastra.co"));

        verify(usuarios, never()).otorgarRol(any(), any(), any());
        assertThat(resultado.sinCuenta()).containsExactly("fantasma@sastra.co");
        assertThat(resultado.otorgados()).isEmpty();
    }

    /**
     * Una errata no tumba el arranque: lo que esta en juego es que una persona se quede
     * sin un rol, no que el servicio funcione. Y se distingue del correo sin cuenta
     * porque se corrigen en sitios distintos.
     */
    @Test
    void deberia_ignorar_una_entrada_que_no_es_un_correo_sin_reventar() {
        GrantConfiguredModeratorsUseCase.Resultado resultado = caso.execute(List.of("esto-no-es-un-correo"));

        assertThat(resultado.invalidos()).containsExactly("esto-no-es-un-correo");
        assertThat(resultado.sinCuenta()).isEmpty();
        verify(usuarios, never()).otorgarRol(any(), any(), any());
    }

    /** Una entrada mala no puede impedir que las buenas se apliquen. */
    @Test
    void deberia_seguir_con_el_resto_despues_de_una_entrada_invalida() {
        User quien = cuenta("buena@sastra.co");
        when(usuarios.buscarPorCorreo(new Email("buena@sastra.co"))).thenReturn(Optional.of(quien));

        GrantConfiguredModeratorsUseCase.Resultado resultado = caso.execute(List.of("@@@", "buena@sastra.co"));

        assertThat(resultado.otorgados()).containsExactly("buena@sastra.co");
        verify(usuarios).otorgarRol(quien.id(), Role.MODERATOR, AHORA);
    }

    /**
     * Solo {@code MODERATOR}. Que de aqui pudiera salir un {@code ADMIN} convertiria una
     * variable de entorno en el control total de la operacion.
     */
    @Test
    void deberia_otorgar_solo_el_rol_de_moderador() {
        User quien = cuenta("moderadora@sastra.co");
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.of(quien));

        caso.execute(List.of("moderadora@sastra.co"));

        verify(usuarios, never()).otorgarRol(any(), eq(Role.ADMIN), any());
        verify(usuarios, never()).otorgarRol(any(), eq(Role.SELLER), any());
    }

    /** No revoca nunca: quitar un correo de la lista no le quita el rol a nadie. */
    @Test
    void deberia_no_revocar_ningun_rol() {
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.of(cuenta("moderadora@sastra.co")));

        caso.execute(List.of("moderadora@sastra.co"));

        verify(usuarios, never()).revocarRol(any(), any());
    }
}
