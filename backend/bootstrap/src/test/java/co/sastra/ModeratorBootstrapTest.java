package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;

import co.sastra.identity.config.ModeratorBootstrapProperties;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.usecase.GrantConfiguredModeratorsUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El rol de moderador otorgado por configuracion (HU-006), contra la base de verdad.
 *
 * <p>Existe sobre todo por una cosa que una prueba con dobles no puede ver: <strong>como
 * enlaza Spring la variable vacia</strong>. En {@code application.yaml} la propiedad es
 * {@code ${SECURITY_BOOTSTRAP_MODERATORS:}}, y si una cadena vacia se convirtiera en una
 * lista con un elemento vacio en vez de en una lista vacia, cada arranque del proyecto
 * —el caso normal, con nadie configurado— dejaria un aviso de "esto no es un correo" en
 * el registro. Un aviso que sale siempre es un aviso que nadie lee.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ModeratorBootstrapTest {

    private final ModeratorBootstrapProperties propiedades;

    private final GrantConfiguredModeratorsUseCase caso;

    private final UserRepository usuarios;

    private final JdbcClient jdbc;

    private final PasswordHasher hasher;

    private final Clock reloj;

    ModeratorBootstrapTest(
            ModeratorBootstrapProperties propiedades,
            GrantConfiguredModeratorsUseCase caso,
            UserRepository usuarios,
            JdbcClient jdbc,
            PasswordHasher hasher,
            Clock reloj) {
        this.propiedades = propiedades;
        this.caso = caso;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.reloj = reloj;
    }

    /** Por omision no hay ninguno, y la lista tiene que estar vacia de verdad. */
    @Test
    void deberia_enlazar_la_variable_vacia_como_una_lista_sin_elementos() {
        assertThat(propiedades.moderators()).isEmpty();
    }

    private UserId cuentaNueva(String correo) {
        User quien = User.registrar(
                UserId.nuevo(),
                new Email(correo),
                new DisplayName("Quien Modera"),
                new BirthDate(LocalDate.of(1990, 1, 1)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());

        usuarios.crear(quien, hasher.hashear(new RawPassword("una-contrasena-larga")));

        return quien.id();
    }

    private List<String> rolesDe(UserId usuario) {
        return jdbc.sql("SELECT role FROM user_roles WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .query(String.class)
                .list();
    }

    @Test
    void deberia_otorgar_el_rol_a_la_cuenta_configurada_y_no_a_otras() {
        String correo = "moderadora-" + UserId.nuevo().value() + "@sastra.co";
        UserId quien = cuentaNueva(correo);
        UserId ajena = cuentaNueva("ajena-" + UserId.nuevo().value() + "@sastra.co");

        caso.execute(List.of(correo));

        assertThat(rolesDe(quien)).contains("MODERATOR");
        assertThat(rolesDe(ajena)).doesNotContain("MODERATOR");
    }

    /**
     * Idempotente: arrancar dos veces deja lo mismo que arrancar una. Sin el
     * {@code ON CONFLICT DO NOTHING} del repositorio, el segundo arranque reventaria por
     * clave duplicada y el servicio no levantaria.
     */
    @Test
    void deberia_poder_otorgarse_dos_veces_sin_romper_nada() {
        String correo = "repetida-" + UserId.nuevo().value() + "@sastra.co";
        UserId quien = cuentaNueva(correo);

        caso.execute(List.of(correo));
        caso.execute(List.of(correo));

        assertThat(rolesDe(quien)).filteredOn("MODERATOR"::equals).hasSize(1);
    }

    /** No crea cuentas: es lo que separa esto de una puerta trasera. */
    @Test
    void deberia_no_crear_ninguna_cuenta_para_un_correo_desconocido() {
        String correo = "fantasma-" + UserId.nuevo().value() + "@sastra.co";

        GrantConfiguredModeratorsUseCase.Resultado resultado = caso.execute(List.of(correo));

        assertThat(resultado.sinCuenta()).containsExactly(correo);
        assertThat(usuarios.buscarPorCorreo(new Email(correo))).isEmpty();
    }
}
