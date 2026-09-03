package co.sendik.identity.usecase;

import co.sendik.identity.dto.ListPendingVerificationsQuery;
import co.sendik.identity.dto.PendingVerificationsResult;
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
 * <p><strong>Dice tambien si detras hay mas.</strong> No es un dato de adorno: sin el, la
 * pantalla solo puede deducirlo de que la pagina venga llena, y esa deduccion se equivoca
 * cuando el total es multiplo exacto del tamano. Ver {@link PendingVerificationsResult}.
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

    public PendingVerificationsResult execute(ListPendingVerificationsQuery consulta) {
        // Se pide una fila mas de las que caben en la pagina. Si vuelve, es que detras hay
        // algo; y si no, esta era la ultima. Es lo que permite contestar «hay mas» sin
        // contar la tabla en cada carga, y sin dejar que lo adivine la pantalla.
        //
        // El techo de TAMANO_MAXIMO no se rompe por esto: acota lo que un cliente puede
        // pedir que se le muestre, y la sonda es una fila que nunca sale de aqui.
        //
        // El salto se calcula aqui y se pasa aparte del limite. Tiene que ser asi: si el
        // repositorio lo dedujera de cuantas filas se le piden, la fila de sonda correria
        // tambien el arranque y se perderia una fila por pagina.
        long salto = (long) consulta.pagina() * consulta.tamano();

        List<SellerVerification> conSonda = verificaciones.pendientesDeRevision(salto, consulta.tamano() + 1);

        boolean hayMas = conSonda.size() > consulta.tamano();
        List<SellerVerification> pagina = hayMas ? conSonda.subList(0, consulta.tamano()) : conSonda;

        return new PendingVerificationsResult(pagina, hayMas);
    }
}
