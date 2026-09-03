package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.ListPendingVerificationsQuery;
import co.sendik.identity.dto.PendingVerificationsResult;
import co.sendik.identity.model.BankAccount;
import co.sendik.identity.model.BankAccountNumber;
import co.sendik.identity.model.BankAccountType;
import co.sendik.identity.model.BankCode;
import co.sendik.identity.model.IdentityDocument;
import co.sendik.identity.model.IdentityDocumentNumber;
import co.sendik.identity.model.IdentityDocumentType;
import co.sendik.identity.model.LegalName;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.shared.file.FileKey;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * La bandeja paginada, y sobre todo: si detras hay mas. HU-006.
 *
 * <p>Lo que se prueba aqui no es que la pagina venga llena —eso lo hace la base de datos—
 * sino la unica decision que toma este caso de uso: pedir una fila de mas, contestar con
 * ella y no entregarla. El caso que importa es el que antes no se podia distinguir: una
 * pagina llena que ademas es la ultima.
 */
class ListPendingVerificationsUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");

    private static final LegalName TITULAR = new LegalName("Ana Maria Garcia");

    private final SellerVerificationRepository verificaciones = mock(SellerVerificationRepository.class);

    private final ListPendingVerificationsUseCase caso = new ListPendingVerificationsUseCase(verificaciones);

    private SellerVerification enRevision() {
        return SellerVerification.iniciar(SellerVerificationId.nuevo(), UserId.nuevo(), AHORA)
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber("1053812947"),
                                TITULAR,
                                new FileKey("documentos/frente.png"),
                                new FileKey("documentos/reverso.png")),
                        AHORA)
                .conSelfie(new FileKey("selfies/abc.png"), AHORA)
                .conCuentaBancaria(
                        new BankAccount(
                                new BankCode("bancolombia"),
                                BankAccountType.SAVINGS,
                                new BankAccountNumber("91500123456"),
                                TITULAR),
                        AHORA)
                .enviarARevision(AHORA);
    }

    private List<SellerVerification> cuantas(int cuantas) {
        return IntStream.range(0, cuantas).mapToObj(i -> enRevision()).toList();
    }

    /**
     * <strong>La fila de sonda no puede mover el arranque.</strong> Es el defecto que casi
     * se cuela: pidiendo la pagina por numero, el repositorio derivaba el desplazamiento
     * del mismo argumento que el limite, asi que pedir una de mas saltaba una de mas. La
     * fila 21 no salia en ninguna pagina y la bandeja la escondia en silencio, que es peor
     * que el «Siguiente» hacia una pagina vacia que esto vino a arreglar.
     *
     * <p>Por eso el salto se calcula aqui y viaja aparte: la pagina 1 de tamano 20 empieza
     * en la fila 20, pida las filas que pida.
     */
    @Test
    void deberia_saltar_por_el_tamano_de_la_pagina_y_no_por_lo_que_pide_de_mas() {
        when(verificaciones.pendientesDeRevision(20L, 21)).thenReturn(cuantas(21));

        caso.execute(new ListPendingVerificationsQuery(1, 20));

        verify(verificaciones).pendientesDeRevision(20L, 21);
    }

    @Test
    void deberia_decir_que_hay_mas_y_no_entregar_la_fila_de_sonda() {
        when(verificaciones.pendientesDeRevision(0L, 21)).thenReturn(cuantas(21));

        PendingVerificationsResult resultado = caso.execute(new ListPendingVerificationsQuery(0, 20));

        assertThat(resultado.hayMas()).isTrue();
        assertThat(resultado.items()).hasSize(20);
    }

    /**
     * El defecto que motivo todo esto: con el total multiplo exacto del tamano, deducir el
     * «hay mas» de que la pagina venga llena ofrece un «Siguiente» hacia una pagina vacia.
     */
    @Test
    void deberia_decir_que_no_hay_mas_cuando_la_pagina_llena_es_la_ultima() {
        when(verificaciones.pendientesDeRevision(0L, 21)).thenReturn(cuantas(20));

        PendingVerificationsResult resultado = caso.execute(new ListPendingVerificationsQuery(0, 20));

        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.items()).hasSize(20);
    }

    @Test
    void deberia_decir_que_no_hay_mas_con_la_pagina_a_medias() {
        when(verificaciones.pendientesDeRevision(0L, 21)).thenReturn(cuantas(3));

        PendingVerificationsResult resultado = caso.execute(new ListPendingVerificationsQuery(0, 20));

        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.items()).hasSize(3);
    }

    @Test
    void deberia_sostenerlo_tambien_en_el_tamano_maximo() {
        int tope = ListPendingVerificationsQuery.TAMANO_MAXIMO;
        when(verificaciones.pendientesDeRevision(2L * tope, tope + 1)).thenReturn(cuantas(tope + 1));

        PendingVerificationsResult resultado = caso.execute(new ListPendingVerificationsQuery(2, tope));

        assertThat(resultado.hayMas()).isTrue();
        assertThat(resultado.items()).hasSize(tope);
    }

    @Test
    void deberia_contestar_con_la_bandeja_vacia_sin_inventarse_una_pagina_siguiente() {
        when(verificaciones.pendientesDeRevision(0L, 21)).thenReturn(List.of());

        PendingVerificationsResult resultado = caso.execute(new ListPendingVerificationsQuery(0, 20));

        assertThat(resultado.hayMas()).isFalse();
        assertThat(resultado.items()).isEmpty();
    }
}
