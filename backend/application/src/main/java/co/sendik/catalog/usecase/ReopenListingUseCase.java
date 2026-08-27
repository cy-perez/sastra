package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retoma una publicacion rechazada y la devuelve a borrador. Criterio 23, RN-022.
 *
 * <p><strong>Sin esto, un rechazo por fotos no tiene salida.</strong>
 * {@code ListingStatus} no admite {@code REJECTED -> PENDING_REVIEW}, y subir una toma
 * nueva no cambia el estado: el vendedor que solo tenia que reemplazar una foto se
 * quedaba sin forma de reenviar. Editando texto si salia, porque
 * {@code destinoTrasEditar} lleva lo rechazado a borrador, pero eso le obliga a tocar un
 * campo que no tenia nada malo.
 *
 * <p>Conserva datos y tomas, que es justo lo que el criterio 23 pide: quien corrige no
 * vuelve a empezar.
 */
public class ReopenListingUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public ReopenListingUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        return publicaciones.guardar(actual.retomar(Instant.now(reloj)));
    }
}
