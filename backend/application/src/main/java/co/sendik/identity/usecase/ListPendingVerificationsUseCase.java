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
 * <p>Lo pregunta con {@code hayPendientesDesde} y no trayendo una fila de mas: traerla
 * obliga a descifrar la cedula y la cuenta de alguien para no enseñarlas nunca.
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
        long salto = (long) consulta.pagina() * consulta.tamano();

        // Se trae la pagina exacta, ni una fila mas. Se probo pidiendo una de mas y usando
        // su presencia como señal, y funciona, pero `pendientesDeRevision` devuelve el
        // agregado entero: descifraba la cedula y la cuenta de una persona solo para
        // comprobar que su fila existia, y las tiraba. Ver `hayPendientesDesde`.
        List<SellerVerification> pagina = verificaciones.pendientesDeRevision(salto, consulta.tamano());

        // Son dos consultas y no una, asi que entre ellas cabe que alguien decida la
        // ultima pendiente y esto diga que hay otra pagina cuando ya no la hay. La ventana
        // es de microsegundos dentro de la misma peticion, y lo peor que produce es un
        // «Siguiente» que lleva a la pantalla de bandeja vacia, que se explica sola. Se
        // acepta a cambio de no descifrar datos de identidad que nadie va a mirar.
        boolean hayMas = verificaciones.hayPendientesDesde(salto + consulta.tamano());

        return new PendingVerificationsResult(pagina, hayMas);
    }
}
