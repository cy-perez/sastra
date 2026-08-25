package co.sendik.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.exception.VerificationTokenExpiredException;
import co.sendik.identity.exception.VerificationTokenInvalidException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VerificationTokenTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final UserId USUARIO = UserId.nuevo();

    private static VerificationToken emitido() {
        return VerificationToken.emitir(
                USUARIO,
                TokenPurpose.EMAIL_VERIFICATION,
                "hash-del-token",
                AHORA,
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO);
    }

    @Test
    void deberia_caducar_a_las_veinticuatro_horas_RN_003() {
        assertThat(VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO).isEqualTo(Duration.ofHours(24));
        assertThat(emitido().expiresAt()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    @Test
    void deberia_caducar_el_restablecimiento_a_los_treinta_minutos_RN_004() {
        assertThat(VerificationToken.VIGENCIA_RESTABLECIMIENTO).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void deberia_seguir_siendo_utilizable_un_minuto_antes_de_caducar_RN_003() {
        Instant casiCaducado = AHORA.plus(Duration.ofHours(24)).minusSeconds(60);

        assertThat(emitido().estaCaducado(casiCaducado)).isFalse();
    }

    @Test
    void deberia_estar_caducado_en_el_instante_exacto_de_la_caducidad_RN_003() {
        assertThat(emitido().estaCaducado(AHORA.plus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    void deberia_rechazar_un_token_caducado_RN_003() {
        VerificationToken token = emitido();
        Instant tardisimo = AHORA.plus(Duration.ofHours(25));

        assertThatThrownBy(() -> token.verificarUtilizable(tardisimo))
                .isInstanceOf(VerificationTokenExpiredException.class);
    }

    @Test
    void deberia_funcionar_una_sola_vez_RN_003() {
        VerificationToken usado = emitido().marcarUsado(AHORA.plusSeconds(10));
        Instant despues = AHORA.plusSeconds(20);

        assertThat(usado.yaSeUso()).isTrue();
        assertThatThrownBy(() -> usado.verificarUtilizable(despues))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    // Un token ya usado y ademas caducado debe decir "no sirve", no "caducado":
    // ofrecerle reenviar lo mandaria a repetir algo que ya hizo.
    @Test
    void deberia_reportar_como_invalido_y_no_como_caducado_un_token_usado_y_vencido() {
        VerificationToken usado = emitido().marcarUsado(AHORA.plusSeconds(10));
        Instant muchoDespues = AHORA.plus(Duration.ofHours(48));

        assertThatThrownBy(() -> usado.verificarUtilizable(muchoDespues))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    @Test
    void deberia_aceptar_un_token_vigente_y_sin_usar() {
        VerificationToken token = emitido();

        token.verificarUtilizable(AHORA.plusSeconds(60));

        assertThat(token.yaSeUso()).isFalse();
    }

    @Test
    void deberia_devolver_una_instancia_nueva_al_marcarse_usado() {
        VerificationToken token = emitido();
        VerificationToken usado = token.marcarUsado(AHORA);

        assertThat(usado).isNotSameAs(token);
        assertThat(token.yaSeUso()).isFalse();
        assertThat(usado.id()).isEqualTo(token.id());
    }

    @Test
    void deberia_rechazar_un_hash_vacio() {
        assertThatThrownBy(() -> VerificationToken.emitir(
                        USUARIO, TokenPurpose.EMAIL_VERIFICATION, "  ", AHORA, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_exigir_instante_y_vigencia() {
        assertThatThrownBy(() -> VerificationToken.emitir(
                        USUARIO, TokenPurpose.EMAIL_VERIFICATION, "hash", null, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () -> VerificationToken.emitir(USUARIO, TokenPurpose.EMAIL_VERIFICATION, "hash", AHORA, null))
                .isInstanceOf(NullPointerException.class);
    }

    // Quien lea un registro no debe encontrar con que verificar una cuenta ajena.
    @Test
    void no_deberia_exponer_el_hash_al_imprimirse() {
        assertThat(emitido().toString()).doesNotContain("hash-del-token");
    }

    @Test
    void deberia_conservar_los_datos_al_rehidratar() {
        VerificationToken token = VerificationToken.rehidratar(
                VerificationTokenId.nuevo(),
                USUARIO,
                TokenPurpose.PASSWORD_RESET,
                "hash",
                AHORA.plusSeconds(600),
                null,
                AHORA,
                null);

        assertThat(token.purpose()).isEqualTo(TokenPurpose.PASSWORD_RESET);
        assertThat(token.userId()).isEqualTo(USUARIO);
        assertThat(token.tokenHash()).isEqualTo("hash");
        assertThat(token.usedAt()).isNull();
        assertThat(token.createdAt()).isEqualTo(AHORA);
    }
    /**
     * Criterio 21: el correo pendiente viaja en el token y no en la fila del
     * usuario, asi caduca y se consume con el enlace.
     */
    @Test
    void deberia_llevar_el_correo_pendiente_en_un_cambio_de_correo_criterio_21() {
        VerificationToken token = VerificationToken.paraCambioDeCorreo(
                USUARIO, new Email("nueva@correo.co"), "hash", AHORA, Duration.ofHours(24));

        assertThat(token.purpose()).isEqualTo(TokenPurpose.EMAIL_CHANGE);
        assertThat(token.newEmail()).isEqualTo(new Email("nueva@correo.co"));
        assertThat(token.expiresAt()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    @Test
    void el_cambio_de_correo_deberia_conservar_el_destino_al_consumirse() {
        VerificationToken usado = VerificationToken.paraCambioDeCorreo(
                        USUARIO, new Email("nueva@correo.co"), "hash", AHORA, Duration.ofHours(24))
                .marcarUsado(AHORA.plusSeconds(60));

        assertThat(usado.yaSeUso()).isTrue();
        assertThat(usado.newEmail()).isEqualTo(new Email("nueva@correo.co"));
    }

    @Test
    void el_cambio_de_correo_deberia_exigir_un_destino() {
        assertThatThrownBy(
                        () -> VerificationToken.paraCambioDeCorreo(USUARIO, null, "hash", AHORA, Duration.ofHours(24)))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Un token de cambio sin destino no significa nada, y cualquier otro proposito
     * con destino tampoco. La misma restriccion que la base de datos.
     */
    @Test
    void deberia_rechazar_una_combinacion_imposible_de_proposito_y_destino() {
        assertThatThrownBy(() -> VerificationToken.rehidratar(
                        VerificationTokenId.nuevo(),
                        USUARIO,
                        TokenPurpose.EMAIL_CHANGE,
                        "hash",
                        AHORA.plusSeconds(600),
                        null,
                        AHORA,
                        null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> VerificationToken.rehidratar(
                        VerificationTokenId.nuevo(),
                        USUARIO,
                        TokenPurpose.PASSWORD_RESET,
                        "hash",
                        AHORA.plusSeconds(600),
                        null,
                        AHORA,
                        new Email("nueva@correo.co")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Pedir el destino en un token que no es de cambio es un error de programacion,
    // y por eso falla en vez de devolver nulo.
    @Test
    void deberia_fallar_al_pedir_el_destino_de_un_token_de_otro_proposito() {
        assertThatThrownBy(() -> emitido().newEmail()).isInstanceOf(NullPointerException.class);
    }
}
