package co.sendik.identity.usecase;

import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.SellerVerificationRepository;
import java.util.Optional;

/**
 * El estado de la propia solicitud de verificacion. Criterio 11 de HU-002.
 *
 * <p>Existe aunque solo delegue, y el motivo no es simetria: entre el controlador y el
 * repositorio va el caso de uso, porque es quien abre la transaccion y quien marca la
 * frontera de la capa. {@code ArchitectureTest} lo comprueba, y con razon: el dia que
 * esta lectura necesite decidir algo —descifrar solo para quien puede ver, o esconder un
 * campo segun el rol— ya hay donde ponerlo, y no hay que mover una llamada del borde.
 *
 * <p>Es el mismo motivo por el que existe {@code ReadProfileUseCase}.
 *
 * <p>Devuelve vacio cuando la persona no ha empezado. No lanza: no haber empezado no es
 * un error, y el borde lo traduce a 404 porque el recurso todavia no existe.
 */
public class ReadSellerVerificationUseCase {

    private final SellerVerificationRepository verificaciones;

    public ReadSellerVerificationUseCase(SellerVerificationRepository verificaciones) {
        this.verificaciones = verificaciones;
    }

    public Optional<SellerVerification> execute(UserId usuario) {
        return verificaciones.buscarPorUsuario(usuario);
    }
}
