package co.sendik.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.exception.AccountLockedException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserCredentialsTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final UserId USUARIO = UserId.nuevo();

    private static UserCredentials limpias() {
        return UserCredentials.rehidratar(USUARIO, new PasswordHash("$argon2id$hash"), AHORA, 0, null);
    }

    private static UserCredentials conIntentosFallidos(int cuantos, Instant desde) {
        UserCredentials credenciales = limpias();
        for (int i = 0; i < cuantos; i++) {
            credenciales = credenciales.registrarFallo(desde.plusSeconds(i));
        }
        return credenciales;
    }

    @Test
    void deberia_bloquear_quince_minutos_al_quinto_intento_fallido_RN_006() {
        assertThat(UserCredentials.INTENTOS_MAXIMOS).isEqualTo(5);
        assertThat(UserCredentials.BLOQUEO).isEqualTo(Duration.ofMinutes(15));

        UserCredentials tras5 = conIntentosFallidos(5, AHORA);

        assertThat(tras5.failedAttempts()).isEqualTo(5);
        assertThat(tras5.estaBloqueada(AHORA.plusSeconds(5))).isTrue();
        assertThat(tras5.lockedUntil()).isEqualTo(AHORA.plusSeconds(4).plus(Duration.ofMinutes(15)));
    }

    @Test
    void no_deberia_bloquear_al_cuarto_intento_fallido_RN_006() {
        UserCredentials tras4 = conIntentosFallidos(4, AHORA);

        assertThat(tras4.failedAttempts()).isEqualTo(4);
        assertThat(tras4.lockedUntil()).isNull();
        assertThat(tras4.estaBloqueada(AHORA)).isFalse();
    }

    @Test
    void deberia_dejar_de_estar_bloqueada_pasados_los_quince_minutos_RN_006() {
        UserCredentials bloqueada = conIntentosFallidos(5, AHORA);
        Instant desbloqueo = bloqueada.lockedUntil();

        assertThat(bloqueada.estaBloqueada(desbloqueo.minusSeconds(1))).isTrue();
        assertThat(bloqueada.estaBloqueada(desbloqueo)).isFalse();
    }

    // RN-006 cuenta el bloqueo desde el ultimo intento, no desde el quinto: quien
    // sigue probando no consigue una ventana nueva cada cuarto de hora.
    @Test
    void deberia_empujar_el_desbloqueo_con_cada_intento_estando_bloqueada_RN_006() {
        UserCredentials bloqueada = conIntentosFallidos(5, AHORA);
        Instant masTarde = AHORA.plus(Duration.ofMinutes(10));

        UserCredentials insistiendo = bloqueada.registrarFallo(masTarde);

        assertThat(insistiendo.failedAttempts()).isEqualTo(6);
        assertThat(insistiendo.lockedUntil()).isEqualTo(masTarde.plus(Duration.ofMinutes(15)));
        assertThat(insistiendo.estaBloqueada(bloqueada.lockedUntil())).isTrue();
    }

    // Quien se equivoco cinco veces hace una hora tiene otros cinco intentos, no
    // uno: si no, la cuenta queda bloqueada de por vida tras un mal dia.
    @Test
    void deberia_reiniciar_el_contador_cuando_el_bloqueo_ya_vencio_RN_006() {
        UserCredentials bloqueada = conIntentosFallidos(5, AHORA);
        Instant unaHoraDespues = AHORA.plus(Duration.ofHours(1));

        UserCredentials reintento = bloqueada.registrarFallo(unaHoraDespues);

        assertThat(reintento.failedAttempts()).isEqualTo(1);
        assertThat(reintento.lockedUntil()).isNull();
        assertThat(reintento.estaBloqueada(unaHoraDespues)).isFalse();
    }

    @Test
    void deberia_limpiar_el_contador_y_el_bloqueo_al_entrar_correctamente() {
        UserCredentials bloqueada = conIntentosFallidos(5, AHORA);

        UserCredentials tras = bloqueada.registrarExito();

        assertThat(tras.failedAttempts()).isZero();
        assertThat(tras.lockedUntil()).isNull();
    }

    @Test
    void no_deberia_crear_una_instancia_nueva_si_ya_estaba_limpia() {
        UserCredentials credenciales = limpias();

        assertThat(credenciales.registrarExito()).isSameAs(credenciales);
    }

    @Test
    void deberia_lanzar_con_el_instante_de_desbloqueo_cuando_esta_bloqueada_RN_006() {
        UserCredentials bloqueada = conIntentosFallidos(5, AHORA);
        Instant dentroDelBloqueo = AHORA.plus(Duration.ofMinutes(1));

        assertThatExceptionOfType(AccountLockedException.class)
                .isThrownBy(() -> bloqueada.verificarQueNoEstaBloqueada(dentroDelBloqueo))
                .satisfies(fallo -> assertThat(fallo.desbloqueoEn()).isEqualTo(bloqueada.lockedUntil()));
    }

    @Test
    void no_deberia_lanzar_cuando_no_hay_bloqueo_vigente() {
        limpias().verificarQueNoEstaBloqueada(AHORA);
        conIntentosFallidos(4, AHORA).verificarQueNoEstaBloqueada(AHORA);
        conIntentosFallidos(5, AHORA).verificarQueNoEstaBloqueada(AHORA.plus(Duration.ofHours(1)));
    }

    @Test
    void deberia_devolver_una_instancia_nueva_sin_tocar_la_original() {
        UserCredentials original = limpias();

        original.registrarFallo(AHORA);

        assertThat(original.failedAttempts()).isZero();
    }

    @Test
    void deberia_rechazar_un_contador_negativo() {
        PasswordHash hash = new PasswordHash("$argon2id$hash");

        assertThatThrownBy(() -> UserCredentials.rehidratar(USUARIO, hash, AHORA, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_exigir_el_instante_al_registrar_un_intento() {
        UserCredentials credenciales = limpias();

        assertThatThrownBy(() -> credenciales.registrarFallo(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> credenciales.estaBloqueada(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void no_deberia_exponer_el_hash_al_imprimirse() {
        assertThat(limpias().toString()).doesNotContain("argon2id");
    }

    @Test
    void deberia_conservar_los_datos_al_rehidratar() {
        UserCredentials credenciales = UserCredentials.rehidratar(
                USUARIO, new PasswordHash("$argon2id$hash"), AHORA, 3, AHORA.plus(Duration.ofMinutes(15)));

        assertThat(credenciales.userId()).isEqualTo(USUARIO);
        assertThat(credenciales.passwordHash().value()).isEqualTo("$argon2id$hash");
        assertThat(credenciales.passwordUpdatedAt()).isEqualTo(AHORA);
        assertThat(credenciales.failedAttempts()).isEqualTo(3);
    }
}
