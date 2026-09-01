package co.sendik.catalog.port.out;

import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingRejectionReason;
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

    /**
     * RN-024: el moderador bajo algo que ya era visible.
     *
     * <p><strong>El motivo llega como argumento y no dentro de la publicacion</strong>, a
     * diferencia del rechazo. No es simetria perdida: es que archivar no guarda motivo, y
     * no debe guardarlo, porque archivar es tambien lo que hace un vendedor con lo suyo y
     * ahi no hay ninguno. El motivo es del acto de moderacion, no del estado en que queda
     * la publicacion, y quien lo conserva es la bitacora.
     *
     * <p>Hasta HU-010 esta firma no lo llevaba y quien avisaba lo sacaba de
     * {@code publicacion.rejectionReason()}, que en una publicacion archivada es nulo:
     * {@code POST /listings/&#123;id&#125;/removal} reventaba con un 500 y, por estar dentro
     * de la transaccion, deshacia el retiro entero. Nadie lo habia visto porque hasta
     * ahora no habia forma de llamar a esa ruta desde la interfaz.
     */
    void publicacionRetirada(Listing publicacion, ListingRejectionReason motivo, @Nullable String nota);
}
