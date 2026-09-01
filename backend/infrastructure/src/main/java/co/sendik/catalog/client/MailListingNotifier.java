package co.sendik.catalog.client;

import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.usecase.ReadProfileUseCase;
import co.sendik.shared.port.out.MailTransport;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Avisa al vendedor por correo de lo que decidio el moderador. Criterio 26.
 *
 * <p><strong>Arma el correo aqui y lo manda por {@link MailTransport}.</strong> Lo que
 * dice un correo de publicacion es vocabulario del catalogo, y el transporte no sabe de
 * publicaciones: recibe destinatario, asunto y cuerpo. Antes esto pasaba por el
 * {@code MailSender} de identidad, que acabo con tres metodos que solo llamaba el
 * catalogo; ADR-0023 explica por que se separo.
 *
 * <p>Del caso de uso publico {@code ReadProfileUseCase} salen las dos cosas que este
 * adaptador no tiene y necesita: a que direccion escribe y en que idioma. Es la misma via
 * que usa {@code VerifiedSellerEligibility}, y la unica que
 * {@code docs/arquitectura/vision-tecnica.md} admite entre contextos.
 *
 * <p>Aqui es tambien donde se traduce entre {@code SellerId} y {@code UserId}, que
 * envuelven el mismo UUID y son tipos distintos a proposito. El sitio para pasar de uno a
 * otro es el borde entre contextos, que es este.
 */
@Component
public class MailListingNotifier implements ListingNotifier {

    private final ReadProfileUseCase perfiles;
    private final MailTransport correo;

    public MailListingNotifier(ReadProfileUseCase perfiles, MailTransport correo) {
        this.perfiles = perfiles;
        this.correo = correo;
    }

    @Override
    public void publicacionAprobada(Listing publicacion) {
        User vendedor = vendedorDe(publicacion);
        UserLocale idioma = vendedor.locale();

        correo.enviar(
                vendedor.email().value(),
                ListingMailTexts.asuntoDeAprobada(idioma),
                ListingMailTexts.cuerpoDeAprobada(idioma, tituloDe(publicacion)));
    }

    @Override
    public void publicacionRechazada(Listing publicacion, @Nullable String nota) {
        User vendedor = vendedorDe(publicacion);
        UserLocale idioma = vendedor.locale();

        correo.enviar(
                vendedor.email().value(),
                ListingMailTexts.asuntoDeRechazada(idioma),
                ListingMailTexts.cuerpoDeRechazada(idioma, tituloDe(publicacion), motivoDe(publicacion, idioma), nota));
    }

    @Override
    public void publicacionRetirada(Listing publicacion, ListingRejectionReason motivo, @Nullable String nota) {
        User vendedor = vendedorDe(publicacion);
        UserLocale idioma = vendedor.locale();

        correo.enviar(
                vendedor.email().value(),
                ListingMailTexts.asuntoDeRetirada(idioma),
                ListingMailTexts.cuerpoDeRetirada(
                        idioma, tituloDe(publicacion), ListingRejectionTexts.de(idioma, motivo), nota));
    }

    private User vendedorDe(Listing publicacion) {
        return perfiles.execute(new UserId(publicacion.sellerId().value()));
    }

    /**
     * El titulo. Una publicacion sobre la que un moderador decide siempre paso por
     * revision, y para pasar por revision el titulo es obligatorio.
     *
     * <p>Si faltara, el correo no se manda: es preferible a inventar aqui un texto
     * visible, que CLAUDE.md prohibe, y a escribir «null» en el buzon de alguien. Que el
     * caso no se puede dar lo garantiza el dominio; que si se diera no pase inadvertido lo
     * garantiza esta excepcion.
     */
    private static String tituloDe(Listing publicacion) {
        Title titulo = publicacion.product().title();

        if (titulo == null) {
            throw new IllegalStateException("La publicacion " + publicacion.id() + " llego a moderacion sin titulo");
        }
        return titulo.value();
    }

    /**
     * El motivo del rechazo, ya traducido.
     *
     * <p>Solo del rechazo: ahi el dominio si lo guarda en la publicacion, porque el
     * vendedor tiene que poder leer que corregir (RN-022). El del retiro llega por
     * argumento, y el porque esta en {@code ListingNotifier#publicacionRetirada}.
     */
    private static String motivoDe(Listing publicacion, UserLocale idioma) {
        ListingRejectionReason motivo = publicacion.rejectionReason();

        if (motivo == null) {
            throw new IllegalStateException(
                    "La publicacion " + publicacion.id() + " se rechazo o se retiro sin motivo");
        }
        return ListingRejectionTexts.de(idioma, motivo);
    }
}
