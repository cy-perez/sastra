package co.sendik.catalog.client;

import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.usecase.ReadProfileUseCase;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Avisa al vendedor por correo de lo que decidio el moderador. Criterio 26.
 *
 * <p><strong>Pregunta por un caso de uso publico de {@code identity}, no por sus
 * tablas</strong>, igual que {@code VerifiedSellerEligibility}: es lo unico que
 * {@code docs/arquitectura/vision-tecnica.md} permite entre contextos. De ahi sale lo que
 * este adaptador no tiene y necesita, que son dos cosas: a que direccion escribe y en que
 * idioma.
 *
 * <p>Aqui es tambien donde se traduce entre {@code SellerId} y {@code UserId}, que
 * envuelven el mismo UUID y son tipos distintos a proposito. El sitio para pasar de uno a
 * otro es el borde entre contextos, que es este.
 *
 * <p><strong>El motivo del rechazo se traduce aqui.</strong> La enumeracion es de
 * {@code catalog} y el puerto de correo es de {@code identity}: si el motivo viajara como
 * enumeracion, identidad quedaria atada al modelo del catalogo. Va como texto ya en el
 * idioma de quien lo recibe, y {@link ListingRejectionTexts} es la fuente.
 */
@Component
public class MailListingNotifier implements ListingNotifier {

    private final ReadProfileUseCase perfiles;
    private final MailSender correo;

    public MailListingNotifier(ReadProfileUseCase perfiles, MailSender correo) {
        this.perfiles = perfiles;
        this.correo = correo;
    }

    @Override
    public void publicacionAprobada(Listing publicacion) {
        User vendedor = vendedorDe(publicacion);
        correo.enviarAvisoDePublicacionAprobada(vendedor, tituloDe(publicacion));
    }

    @Override
    public void publicacionRechazada(Listing publicacion, @Nullable String nota) {
        User vendedor = vendedorDe(publicacion);
        correo.enviarAvisoDePublicacionRechazada(
                vendedor, tituloDe(publicacion), motivoDe(publicacion, vendedor), nota);
    }

    @Override
    public void publicacionRetirada(Listing publicacion, @Nullable String nota) {
        User vendedor = vendedorDe(publicacion);
        correo.enviarAvisoDePublicacionRetirada(vendedor, tituloDe(publicacion), motivoDe(publicacion, vendedor), nota);
    }

    private User vendedorDe(Listing publicacion) {
        return perfiles.execute(new UserId(publicacion.sellerId().value()));
    }

    /**
     * El titulo, o una etiqueta neutra si no lo tiene.
     *
     * <p>Una publicacion sobre la que un moderador decide siempre paso por revision, y
     * para pasar por revision el titulo es obligatorio. La alternativa no es que falte,
     * es que el correo diga «null» el dia que algo cambie.
     */
    private static String tituloDe(Listing publicacion) {
        Title titulo = publicacion.product().title();
        return titulo == null ? "tu publicacion" : titulo.value();
    }

    /**
     * Sin motivo no se rechaza ni se retira: el dominio lo exige en las dos
     * transiciones, asi que aqui no puede ser nulo.
     */
    private static String motivoDe(Listing publicacion, User vendedor) {
        ListingRejectionReason motivo = publicacion.rejectionReason();
        return motivo == null ? "" : ListingRejectionTexts.de(vendedor.locale(), motivo);
    }
}
