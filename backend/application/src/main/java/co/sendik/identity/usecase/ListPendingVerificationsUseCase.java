package co.sendik.identity.usecase;

import co.sendik.identity.dto.ListPendingVerificationsQuery;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.port.out.SellerVerificationRepository;
import java.util.List;

/**
 * La bandeja del moderador: lo que espera revision, lo mas viejo primero. HU-006.
 *
 * <p><strong>Aqui no se anota nada en la bitacora.</strong> Listar no es acceder a un
 * dato sensible: lo que sale de aqui son estados, fechas y cuatro digitos. Lo que si se
 * anota es abrir una imagen y decidir sobre una solicitud, que es cuando alguien mira lo
 * que hay dentro.
 *
 * <p>Anotar tambien el listado convertiria la bitacora en un registro de navegacion, y
 * una bitacora que crece con cada refresco de pantalla es una que nadie lee.
 *
 * <p>El tope y el rango los acota {@link ListPendingVerificationsQuery} y no este metodo:
 * escrito aqui protegeria a quien pase por este caso de uso y a nadie mas. Es la misma
 * reparticion que {@code ListPendingListingsUseCase}.
 */
public class ListPendingVerificationsUseCase {

    private final SellerVerificationRepository verificaciones;

    public ListPendingVerificationsUseCase(SellerVerificationRepository verificaciones) {
        this.verificaciones = verificaciones;
    }

    public List<SellerVerification> execute(ListPendingVerificationsQuery consulta) {
        return verificaciones.pendientesDeRevision(consulta.pagina(), consulta.tamano());
    }
}
