package co.sastra.identity.usecase;

import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.port.out.SellerVerificationRepository;
import java.util.List;

/**
 * La bandeja del moderador: lo que espera revision, lo mas viejo primero.
 *
 * <p><strong>Aqui no se anota nada en la bitacora.</strong> Listar no es acceder a un
 * dato sensible: lo que sale de aqui son estados, fechas y cuatro digitos. Lo que si se
 * anota es abrir una imagen y decidir sobre una solicitud, que es cuando alguien mira lo
 * que hay dentro.
 *
 * <p>Anotar tambien el listado convertiria la bitacora en un registro de navegacion, y
 * una bitacora que crece con cada refresco de pantalla es una que nadie lee.
 */
public class ListPendingVerificationsUseCase {

    /**
     * Tope por consulta. No es configuracion: es un techo para que un cliente no pueda
     * pedir la tabla entera, y la pantalla pagina pidiendo otra vez.
     */
    private static final int MAXIMO = 50;

    private final SellerVerificationRepository verificaciones;

    public ListPendingVerificationsUseCase(SellerVerificationRepository verificaciones) {
        this.verificaciones = verificaciones;
    }

    public List<SellerVerification> execute(int limite) {
        return verificaciones.pendientesDeRevision(Math.clamp(limite, 1, MAXIMO));
    }
}
