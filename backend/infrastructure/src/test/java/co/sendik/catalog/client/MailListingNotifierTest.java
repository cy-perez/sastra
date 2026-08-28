package co.sendik.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.Title;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.usecase.ReadProfileUseCase;
import co.sendik.shared.port.out.MailTransport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/**
 * El otro borde entre catalog e identity: a quien se le escribe y en que idioma.
 *
 * <p>Sin base de datos y sin proveedor de correo. Lo que esta clase tiene propio son
 * tres cosas y son las que se comprueban: que traduce el identificador de vendedor al de
 * cuenta, que traduce el motivo al idioma de quien lo recibe, y que no manda una
 * enumeracion cruda a un buzon.
 */
class MailListingNotifierTest {

    private static final Instant AHORA = Instant.parse("2026-08-25T10:00:00Z");

    private static final String TITULO = "Camisa de lino color hueso";

    private final ReadProfileUseCase perfiles = mock(ReadProfileUseCase.class);
    private final MailTransport correo = mock(MailTransport.class);
    private final MailListingNotifier avisos = new MailListingNotifier(perfiles, correo);

    @Test
    void deberia_avisar_al_vendedor_cuando_se_aprueba_criterio_26() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        User cuenta = cuentaDe(vendedor, UserLocale.ES);

        avisos.publicacionAprobada(publicacion(vendedor, ListingStatus.PUBLISHED, null));

        verify(correo).enviar(eq(cuenta.email().value()), any(), contains(TITULO));
    }

    @Test
    void deberia_mandar_el_motivo_traducido_al_rechazar_criterios_22_y_26() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        cuentaDe(vendedor, UserLocale.ES);

        avisos.publicacionRechazada(
                publicacion(vendedor, ListingStatus.REJECTED, ListingRejectionReason.PHOTOS_UNUSABLE),
                "Se ven borrosas.");

        assertThat(cuerpoDelRechazo()).contains("las fotos no se pueden usar");
    }

    /**
     * El mismo motivo, otra persona, otro idioma. Sin esto el correo saldria en espanol
     * para todo el mundo y nadie lo notaria hasta tenerlo en el buzon.
     */
    @Test
    void deberia_escribir_en_el_idioma_de_quien_lo_recibe() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        cuentaDe(vendedor, UserLocale.EN);

        avisos.publicacionRechazada(
                publicacion(vendedor, ListingStatus.REJECTED, ListingRejectionReason.SUSPECTED_COUNTERFEIT), null);

        assertThat(cuerpoDelRechazo()).contains("we suspect it is not authentic");
    }

    /**
     * El motivo llega por argumento, y la publicacion va <strong>sin motivo dentro</strong>.
     *
     * <p>Asi es como sale del dominio: {@code archivar()} no guarda ninguno, a diferencia de
     * {@code rechazar()}. Esta prueba lo construia con {@code PROHIBITED_ITEM} puesto en la
     * publicacion —un estado que el dominio no produce nunca— y por eso pasaba en verde
     * mientras {@code POST /listings/&#123;id&#125;/removal} reventaba con un 500 en cada
     * llamada, deshaciendo el retiro entero por estar dentro de la transaccion. Lo encontro
     * el recorrido de extremo a extremo de HU-010, que fue el primero en llamar a esa ruta.
     */
    @Test
    void deberia_avisar_con_su_motivo_cuando_el_moderador_retira_criterio_31() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        User cuenta = cuentaDe(vendedor, UserLocale.ES);

        avisos.publicacionRetirada(
                publicacion(vendedor, ListingStatus.ARCHIVED, null),
                ListingRejectionReason.PROHIBITED_ITEM,
                "No se admite.");

        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(eq(cuenta.email().value()), any(), cuerpo.capture());

        assertThat(cuerpo.getValue())
                .contains(TITULO)
                .contains("el producto no se puede vender en Sendik")
                .contains("No se admite.");
    }

    /**
     * Ningun motivo llega al buzon como enumeracion.
     *
     * <p>Va por {@code EnumSource} y no por una lista escrita a mano: un motivo nuevo
     * entra en esta prueba solo. Sin ella, agregar el octavo y olvidar su texto se
     * descubre cuando alguien recibe el nombre de la constante por correo.
     */
    @ParameterizedTest
    @EnumSource(ListingRejectionReason.class)
    void ningun_motivo_deberia_salir_como_nombre_de_enumeracion(ListingRejectionReason motivo) {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        cuentaDe(vendedor, UserLocale.ES);

        avisos.publicacionRechazada(publicacion(vendedor, ListingStatus.REJECTED, motivo), null);

        assertThat(cuerpoDelRechazo())
                .doesNotContain(motivo.name())
                .doesNotContain("_")
                .isNotBlank();
    }

    /**
     * {@code SellerId} y {@code UserId} envuelven el mismo UUID. Si la traduccion se
     * perdiera, el correo iria a otra persona, que es peor que no enviarlo.
     */
    @Test
    void deberia_preguntar_por_el_mismo_uuid_al_cruzar_de_contexto() {
        UUID identificador = UUID.randomUUID();
        SellerId vendedor = new SellerId(identificador);
        cuentaDe(vendedor, UserLocale.ES);

        avisos.publicacionAprobada(publicacion(vendedor, ListingStatus.PUBLISHED, null));

        ArgumentCaptor<UserId> preguntado = ArgumentCaptor.forClass(UserId.class);
        verify(perfiles).execute(preguntado.capture());
        assertThat(preguntado.getValue().value()).isEqualTo(identificador);
    }

    /**
     * El cuerpo del correo de rechazo.
     *
     * <p>Se mira el cuerpo entero y no un parametro suelto porque desde ADR-0023 el
     * transporte recibe texto ya armado: lo que hay que comprobar es lo que de verdad le
     * llega a la persona.
     */
    private String cuerpoDelRechazo() {
        ArgumentCaptor<String> cuerpo = ArgumentCaptor.forClass(String.class);
        verify(correo).enviar(any(), any(), cuerpo.capture());
        return cuerpo.getValue();
    }

    private User cuentaDe(SellerId vendedor, UserLocale idioma) {
        User cuenta = User.rehidratar(
                new UserId(vendedor.value()),
                new Email("vendedora@sendik.co"),
                new DisplayName("Ana"),
                new BirthDate(LocalDate.of(1995, 4, 12)),
                null,
                null,
                null,
                idioma,
                UserStatus.ACTIVE,
                AHORA,
                Set.of(Role.SELLER),
                AHORA);

        when(perfiles.execute(new UserId(vendedor.value()))).thenReturn(cuenta);
        return cuenta;
    }

    private static Listing publicacion(SellerId vendedor, ListingStatus estado, ListingRejectionReason motivo) {
        Product producto = new Product(
                ProductId.nuevo(),
                vendedor,
                CategoryId.nuevo(),
                new Title(TITULO),
                null,
                null,
                null,
                null,
                new Measurements(Map.of()),
                null,
                null,
                null,
                null,
                null);

        return Listing.reconstruir()
                .id(ListingId.nuevo())
                .producto(producto)
                .estado(estado)
                .decididaPor(null, AHORA)
                .rechazadaPor(motivo, null)
                .version(1L)
                .creada(AHORA)
                .tocada(AHORA)
                .armar();
    }
}
