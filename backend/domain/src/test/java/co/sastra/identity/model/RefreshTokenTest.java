package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.exception.RefreshTokenInvalidException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final UserId USUARIO = UserId.nuevo();

    private static RefreshToken abierto() {
        return RefreshToken.abrirSesion(USUARIO, "hash-inicial", AHORA, RefreshToken.VIGENCIA, "Firefox", "ip-hash");
    }

    @Test
    void deberia_durar_treinta_dias_RN_007() {
        assertThat(RefreshToken.VIGENCIA).isEqualTo(Duration.ofDays(30));
        assertThat(abierto().expiresAt()).isEqualTo(AHORA.plus(Duration.ofDays(30)));
    }

    @Test
    void deberia_abrir_una_familia_propia_por_sesion() {
        RefreshToken primera = abierto();
        RefreshToken segunda = abierto();

        assertThat(primera.familyId()).isNotEqualTo(segunda.familyId());
    }

    @Test
    void deberia_nacer_utilizable_y_sin_reemplazo() {
        RefreshToken token = abierto();

        assertThat(token.esUtilizable(AHORA)).isTrue();
        assertThat(token.fueReemplazado()).isFalse();
        assertThat(token.estaRevocado()).isFalse();
        assertThat(token.replacedBy()).isNull();
    }

    @Test
    void deberia_estar_caducado_en_el_instante_exacto_de_la_caducidad() {
        RefreshToken token = abierto();

        assertThat(token.estaCaducado(AHORA.plus(Duration.ofDays(30)).minusSeconds(1)))
                .isFalse();
        assertThat(token.estaCaducado(AHORA.plus(Duration.ofDays(30)))).isTrue();
    }

    // RN-007: rotar en cada uso, y el anterior queda invalido en el mismo
    // movimiento. Que las dos caras salgan juntas es lo que impide guardar una
    // sin la otra.
    @Test
    void deberia_invalidar_el_anterior_al_rotar_RN_007() {
        RefreshToken original = abierto();
        Instant despues = AHORA.plus(Duration.ofDays(1));

        RefreshToken.Rotacion rotacion = original.rotar("hash-nuevo", despues, RefreshToken.VIGENCIA, "Chrome", "otra");

        assertThat(rotacion.consumido().fueReemplazado()).isTrue();
        assertThat(rotacion.consumido().estaRevocado()).isTrue();
        assertThat(rotacion.consumido().esUtilizable(despues)).isFalse();
        assertThat(rotacion.consumido().replacedBy())
                .isEqualTo(rotacion.emitido().id());
        assertThat(rotacion.emitido().esUtilizable(despues)).isTrue();
    }

    @Test
    void deberia_conservar_la_familia_al_rotar() {
        RefreshToken original = abierto();

        RefreshToken.Rotacion rotacion =
                original.rotar("hash-nuevo", AHORA.plusSeconds(60), RefreshToken.VIGENCIA, null, null);

        assertThat(rotacion.emitido().familyId()).isEqualTo(original.familyId());
        assertThat(rotacion.emitido().userId()).isEqualTo(original.userId());
        assertThat(rotacion.emitido().id()).isNotEqualTo(original.id());
    }

    // Una sesion que se usa se mantiene viva; una que se abandona caduca a los 30
    // dias de la ultima vez que se uso.
    @Test
    void deberia_reiniciar_la_vigencia_en_cada_rotacion() {
        Instant aLosDiezDias = AHORA.plus(Duration.ofDays(10));

        RefreshToken.Rotacion rotacion = abierto().rotar("hash-nuevo", aLosDiezDias, RefreshToken.VIGENCIA, null, null);

        assertThat(rotacion.emitido().expiresAt()).isEqualTo(aLosDiezDias.plus(Duration.ofDays(30)));
    }

    @Test
    void no_deberia_dejarse_rotar_dos_veces_RN_007() {
        RefreshToken original = abierto();
        RefreshToken consumido = original.rotar("hash-nuevo", AHORA.plusSeconds(60), RefreshToken.VIGENCIA, null, null)
                .consumido();
        Instant masTarde = AHORA.plusSeconds(120);

        assertThatThrownBy(() -> consumido.rotar("otro", masTarde, RefreshToken.VIGENCIA, null, null))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void deberia_rechazar_la_rotacion_de_un_token_caducado() {
        RefreshToken token = abierto();
        Instant tardisimo = AHORA.plus(Duration.ofDays(31));

        assertThatThrownBy(() -> token.rotar("otro", tardisimo, RefreshToken.VIGENCIA, null, null))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void deberia_rechazar_la_rotacion_de_un_token_revocado() {
        RefreshToken revocado = abierto().revocar(AHORA.plusSeconds(10));
        Instant despues = AHORA.plusSeconds(20);

        assertThatThrownBy(() -> revocado.rotar("otro", despues, RefreshToken.VIGENCIA, null, null))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    // Criterio 16: cerrar sesion revoca en el servidor, no solo en el navegador.
    @Test
    void deberia_quedar_inutilizable_al_revocarse() {
        RefreshToken revocado = abierto().revocar(AHORA.plusSeconds(10));

        assertThat(revocado.estaRevocado()).isTrue();
        assertThat(revocado.esUtilizable(AHORA.plusSeconds(20))).isFalse();
        assertThat(revocado.revokedAt()).isEqualTo(AHORA.plusSeconds(10));
    }

    @Test
    void deberia_conservar_la_primera_fecha_al_revocarse_dos_veces() {
        RefreshToken revocado = abierto().revocar(AHORA.plusSeconds(10));

        assertThat(revocado.revocar(AHORA.plusSeconds(99))).isSameAs(revocado);
    }

    @Test
    void deberia_devolver_instancias_nuevas_sin_tocar_la_original() {
        RefreshToken original = abierto();

        original.revocar(AHORA.plusSeconds(10));
        original.rotar("otro", AHORA.plusSeconds(10), RefreshToken.VIGENCIA, null, null);

        assertThat(original.estaRevocado()).isFalse();
        assertThat(original.fueReemplazado()).isFalse();
    }

    @Test
    void deberia_rechazar_un_hash_vacio() {
        assertThatThrownBy(() -> RefreshToken.abrirSesion(USUARIO, "  ", AHORA, RefreshToken.VIGENCIA, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_exigir_instante_y_vigencia() {
        assertThatThrownBy(() -> RefreshToken.abrirSesion(USUARIO, "hash", null, RefreshToken.VIGENCIA, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshToken.abrirSesion(USUARIO, "hash", AHORA, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deberia_admitir_una_sesion_sin_agente_ni_ip() {
        RefreshToken token = RefreshToken.abrirSesion(USUARIO, "hash", AHORA, RefreshToken.VIGENCIA, null, null);

        assertThat(token.userAgent()).isNull();
        assertThat(token.ipHash()).isNull();
    }

    // Ni el hash ni la IP: este texto acaba en los registros del servidor.
    @Test
    void no_deberia_exponer_el_hash_ni_la_ip_al_imprimirse() {
        String texto = abierto().toString();

        assertThat(texto).doesNotContain("hash-inicial").doesNotContain("ip-hash");
    }

    @Test
    void deberia_identificarse_por_su_identificador() {
        RefreshToken token = abierto();
        RefreshToken rotado = token.rotar("otro", AHORA.plusSeconds(1), RefreshToken.VIGENCIA, null, null)
                .consumido();

        assertThat(token).isEqualTo(rotado).hasSameHashCodeAs(rotado);
        assertThat(token).isNotEqualTo(abierto());
    }

    @Test
    void deberia_conservar_los_datos_al_rehidratar() {
        RefreshTokenId id = RefreshTokenId.nuevo();
        TokenFamilyId familia = TokenFamilyId.nueva();
        RefreshTokenId reemplazo = RefreshTokenId.nuevo();

        RefreshToken token = RefreshToken.rehidratar(
                id,
                USUARIO,
                familia,
                "hash",
                AHORA.plus(Duration.ofDays(30)),
                AHORA.plusSeconds(5),
                reemplazo,
                "Firefox",
                "ip",
                AHORA);

        assertThat(token.id()).isEqualTo(id);
        assertThat(token.familyId()).isEqualTo(familia);
        assertThat(token.replacedBy()).isEqualTo(reemplazo);
        assertThat(token.fueReemplazado()).isTrue();
        assertThat(token.estaRevocado()).isTrue();
        assertThat(token.userAgent()).isEqualTo("Firefox");
        assertThat(token.ipHash()).isEqualTo("ip");
        assertThat(token.createdAt()).isEqualTo(AHORA);
    }

    // Ventana de gracia de RN-007: la mitad temporal.
    @Test
    void deberia_reconocer_que_se_consumio_hace_muy_poco() {
        Instant rotacion = AHORA.plusSeconds(60);
        RefreshToken consumido = abierto()
                .rotar("hash-nuevo", rotacion, RefreshToken.VIGENCIA, null, null)
                .consumido();

        assertThat(consumido.seConsumioDentroDe(Duration.ofSeconds(10), rotacion.plusSeconds(3)))
                .isTrue();
        // El limite es inclusivo: justo en el borde todavia cuenta como carrera.
        assertThat(consumido.seConsumioDentroDe(Duration.ofSeconds(10), rotacion.plusSeconds(10)))
                .isTrue();
        assertThat(consumido.seConsumioDentroDe(Duration.ofSeconds(10), rotacion.plusSeconds(11)))
                .isFalse();
    }

    /**
     * Un token que nunca se roto no puede estar en una carrera: no hay reemplazo
     * con el que competir. Importa porque la ventana solo se consulta para tokens
     * ya consumidos y una respuesta afirmativa aqui abriria un hueco donde no lo
     * hay.
     */
    @Test
    void no_deberia_haber_carrera_en_un_token_que_nunca_se_uso() {
        assertThat(abierto().seConsumioDentroDe(Duration.ofSeconds(10), AHORA)).isFalse();
    }

    // Revocado no es consumido: cerrar sesion no abre ninguna ventana de gracia.
    @Test
    void no_deberia_haber_carrera_en_un_token_solo_revocado() {
        RefreshToken revocado = abierto().revocar(AHORA.plusSeconds(1));

        assertThat(revocado.seConsumioDentroDe(Duration.ofSeconds(10), AHORA.plusSeconds(2)))
                .isFalse();
    }
}
