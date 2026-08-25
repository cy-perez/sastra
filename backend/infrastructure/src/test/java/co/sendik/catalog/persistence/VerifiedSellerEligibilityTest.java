package co.sendik.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.sendik.catalog.model.SellerId;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.VerificationStatus;
import co.sendik.identity.usecase.ReadSellerVerificationUseCase;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/**
 * El borde entre catalog e identity: quien puede publicar hoy. RN-011 y RN-013.
 *
 * <p>Sin base de datos a proposito. Esta clase no consulta ninguna tabla: pregunta por
 * un caso de uso publico, y lo unico suyo que hay que comprobar es a que estados les
 * dice que si y que hace con los dos identificadores. Montar PostgreSQL para eso seria
 * probar el repositorio de otro contexto.
 */
class VerifiedSellerEligibilityTest {

    private static final Instant AHORA = Instant.parse("2026-08-25T10:00:00Z");

    private final ReadSellerVerificationUseCase verificaciones = mock(ReadSellerVerificationUseCase.class);
    private final VerifiedSellerEligibility elegibilidad = new VerifiedSellerEligibility(verificaciones);

    @Test
    void deberia_dejar_publicar_al_vendedor_verificado_RN_011() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        responderCon(vendedor, VerificationStatus.VERIFIED);

        assertThat(elegibilidad.puedePublicar(vendedor)).isTrue();
    }

    /**
     * RN-013: se le quito el sello. Sus publicaciones siguen visibles pero no puede
     * crear nuevas, que es exactamente lo que este booleano decide.
     */
    @Test
    void no_deberia_dejar_publicar_a_quien_le_revocaron_el_sello_RN_013() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        responderCon(vendedor, VerificationStatus.REVOKED);

        assertThat(elegibilidad.puedePublicar(vendedor)).isFalse();
    }

    /**
     * Ningun estado que no sea VERIFIED abre la puerta. Va por enumeracion y no por
     * una lista escrita a mano: un estado nuevo entra aqui solo, y si alguien lo
     * agrega dando por hecho que puede publicar, esta prueba lo dice.
     */
    @ParameterizedTest
    @EnumSource(
            value = VerificationStatus.class,
            names = {"VERIFIED"},
            mode = EnumSource.Mode.EXCLUDE)
    void no_deberia_dejar_publicar_en_ningun_otro_estado(VerificationStatus estado) {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        responderCon(vendedor, estado);

        assertThat(elegibilidad.puedePublicar(vendedor)).isFalse();
    }

    /** Nunca empezo. No es una fila: es la ausencia de una, y da lo mismo que REVOKED. */
    @Test
    void no_deberia_dejar_publicar_a_quien_nunca_se_verifico() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        when(verificaciones.execute(new UserId(vendedor.value()))).thenReturn(Optional.empty());

        assertThat(elegibilidad.puedePublicar(vendedor)).isFalse();
    }

    /**
     * {@code SellerId} y {@code UserId} envuelven el mismo UUID y son tipos distintos a
     * proposito. Si la traduccion se perdiera —o generara otro UUID— la consulta
     * preguntaria por alguien que no es, y devolver vacio para todo el mundo se veria
     * como «nadie esta verificado», que es un fallo silencioso.
     */
    @Test
    void deberia_preguntar_por_el_mismo_uuid_al_cruzar_de_contexto() {
        UUID identificador = UUID.randomUUID();
        SellerId vendedor = new SellerId(identificador);
        responderCon(vendedor, VerificationStatus.VERIFIED);

        elegibilidad.puedePublicar(vendedor);

        ArgumentCaptor<UserId> preguntado = ArgumentCaptor.forClass(UserId.class);
        org.mockito.Mockito.verify(verificaciones).execute(preguntado.capture());
        assertThat(preguntado.getValue().value()).isEqualTo(identificador);
    }

    private void responderCon(SellerId vendedor, VerificationStatus estado) {
        SellerVerification verificacion = SellerVerification.existente(
                SellerVerificationId.nuevo(),
                new UserId(vendedor.value()),
                estado,
                null,
                null,
                null,
                0,
                null,
                null,
                AHORA,
                AHORA);

        when(verificaciones.execute(new UserId(vendedor.value()))).thenReturn(Optional.of(verificacion));
    }
}
