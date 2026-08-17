package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConsentTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Test
    void deberia_guardar_la_version_exacta_del_documento_aceptado() {
        Consent consentimiento = Consent.otorgar(UserId.nuevo(), ConsentDocument.TERMS, "2026-08-01", AHORA, "hash-ip");

        assertThat(consentimiento.version()).isEqualTo("2026-08-01");
        assertThat(consentimiento.document()).isEqualTo(ConsentDocument.TERMS);
        assertThat(consentimiento.acceptedAt()).isEqualTo(AHORA);
    }

    // Sin version el consentimiento no se puede demostrar: dentro de dos anos
    // nadie sabra a que texto dijo que si.
    @Test
    void deberia_rechazar_un_consentimiento_sin_version() {
        UserId usuario = UserId.nuevo();

        assertThatThrownBy(() -> Consent.otorgar(usuario, ConsentDocument.PRIVACY, "   ", AHORA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_admitir_que_no_haya_ip() {
        Consent sinIp = Consent.otorgar(UserId.nuevo(), ConsentDocument.PRIVACY, "1.0", AHORA, null);

        assertThat(sinIp.ipHash()).isNull();
    }

    @Test
    void deberia_exigir_los_datos_obligatorios() {
        UserId usuario = UserId.nuevo();

        assertThatThrownBy(() -> Consent.otorgar(usuario, ConsentDocument.TERMS, null, AHORA, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Consent.otorgar(usuario, null, "1.0", AHORA, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Consent.otorgar(usuario, ConsentDocument.TERMS, "1.0", null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
