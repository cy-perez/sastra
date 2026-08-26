package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ReadListingQuery;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee una publicacion, si quien pregunta puede verla. Criterio 33.
 *
 * <p><strong>Devuelve vacio y no lanza una excepcion de permiso.</strong> El criterio 33
 * es explicito: quien pide una publicacion que no esta visible recibe 404 y nunca 403,
 * porque un 403 confirmaria que existe. Con un {@code Optional} vacio para los dos casos
 * —no existe, y existe pero no es para ti— el borde no tiene forma de distinguirlos ni
 * aunque quisiera.
 *
 * <p>Es tambien la razon de que la comprobacion viva aqui y no en la cadena de filtros:
 * la ruta es publica y quien decide es este caso de uso, que es el unico que sabe en que
 * estado esta la publicacion y de quien es.
 */
public class ReadListingUseCase {

    private final ListingRepository publicaciones;

    public ReadListingUseCase(ListingRepository publicaciones) {
        this.publicaciones = publicaciones;
    }

    @Transactional(readOnly = true)
    public Optional<Listing> execute(ReadListingQuery consulta) {
        return publicaciones.buscar(consulta.publicacion()).filter(publicacion -> puedeVerla(publicacion, consulta));
    }

    /**
     * Visible para cualquiera, o del dueno, o de un moderador.
     *
     * <p>El moderador ve cualquiera aunque no sea suya: es su trabajo, y lo que decide
     * sobre ella lo audita {@code ModerationLog} cuando de verdad decide algo.
     */
    private static boolean puedeVerla(Listing publicacion, ReadListingQuery consulta) {
        return publicacion.esVisible()
                || consulta.esModerador()
                || publicacion.sellerId().equals(consulta.quienMira());
    }
}
