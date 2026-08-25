package co.sendik.identity.dto;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.model.Role;
import co.sendik.identity.model.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Lo que estos objetos imprimen acaba en el registro del servidor en cuanto
 * alguien los pase a un {@code LOG.debug}. El perfil local ya trae
 * {@code co.sendik: DEBUG} (docs/operacion/datos-personales.md).
 */
class SessionResultTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    private static SessionResult unaSesion() {
        return new SessionResult(
                "el-token-de-acceso",
                AHORA.plus(Duration.ofMinutes(15)),
                "el-token-de-refresco",
                AHORA.plus(Duration.ofDays(30)),
                new AuthenticatedUser(UserId.nuevo(), "ana@correo.co", "Ana Maria", true, Set.of(Role.BUYER)));
    }

    @Test
    void no_deberia_imprimir_ninguno_de_los_dos_tokens() {
        String texto = unaSesion().toString();

        assertThat(texto).doesNotContain("el-token-de-acceso").doesNotContain("el-token-de-refresco");
    }

    // Sigue siendo util para investigar: dice hasta cuando valia cada cosa.
    @Test
    void deberia_conservar_las_caducidades_al_imprimirse() {
        assertThat(unaSesion().toString()).contains("2026-08-17T15:15:00Z").contains("2026-09-16T15:00:00Z");
    }

    @Test
    void no_deberia_imprimir_el_correo_ni_el_nombre_del_titular() {
        UserId id = UserId.nuevo();
        AuthenticatedUser usuario = new AuthenticatedUser(id, "ana@correo.co", "Ana Maria", true, Set.of(Role.BUYER));

        assertThat(usuario.toString())
                .doesNotContain("ana@correo.co")
                .doesNotContain("Ana Maria")
                // El identificador si: sirve para investigar sin identificar a nadie.
                .contains(id.toString());
    }
}
