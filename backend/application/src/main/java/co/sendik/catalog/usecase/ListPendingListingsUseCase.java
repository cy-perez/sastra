package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ListPendingListingsQuery;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * La bandeja del moderador: lo que espera revision, lo mas viejo primero. HU-008.
 *
 * <p><strong>Aqui no se anota nada en la bitacora.</strong> Listar no es decidir. Lo que
 * {@code ModerationLog} registra son las decisiones, que es lo que RN-045 exige que no se
 * pierda; anotar tambien el listado convertiria ese rastro en un registro de navegacion, y
 * uno que crece con cada refresco de pantalla es uno que nadie lee.
 *
 * <p>Es el mismo razonamiento de {@code ListPendingVerificationsUseCase}, y ademas aqui
 * pesa menos: una publicacion no es un dato personal.
 */
public class ListPendingListingsUseCase {

    private final ListingRepository publicaciones;

    public ListPendingListingsUseCase(ListingRepository publicaciones) {
        this.publicaciones = publicaciones;
    }

    /**
     * Sin {@code readOnly}: presentation no declara spring-tx, y leer ese atributo al
     * inyectar la clase es un aviso que -Werror convierte en error. Lo aprendio
     * {@code ReadListingUseCase}.
     */
    @Transactional
    public List<Listing> execute(ListPendingListingsQuery consulta) {
        return publicaciones.pendientesDeRevision(consulta.pagina(), consulta.tamano());
    }
}
