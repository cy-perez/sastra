package co.sastra.catalog.port.out;

import co.sastra.catalog.model.Listing;
import org.jspecify.annotations.Nullable;

/**
 * Avisa al vendedor de lo que decidio el moderador. Criterio 26.
 *
 * <p>Puerto propio de catalog y no el {@code MailSender} de identity, por lo mismo que
 * {@link SellerEligibility}: un contexto no usa el puerto de otro. Ademas, lo que este
 * puerto promete no es "manda un correo" sino "avisale": el dia que haya notificaciones
 * push, entra por aqui sin tocar ningun caso de uso.
 */
public interface ListingNotifier {

    void publicacionAprobada(Listing publicacion);

    /** El motivo viaja al vendedor; la nota tambien, y nunca lleva datos de terceros. */
    void publicacionRechazada(Listing publicacion, @Nullable String nota);

    /** RN-024: el moderador bajo algo que ya era visible. */
    void publicacionRetirada(Listing publicacion, @Nullable String nota);
}
