package co.sendik.identity.usecase;

import co.sendik.identity.dto.SubmitBankAccountCommand;
import co.sendik.identity.exception.InvalidVerificationTransitionException;
import co.sendik.identity.exception.UnknownFinancialInstitutionException;
import co.sendik.identity.model.BankAccount;
import co.sendik.identity.model.BankAccountNumber;
import co.sendik.identity.model.BankCode;
import co.sendik.identity.model.LegalName;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.VerificationStatus;
import co.sendik.identity.port.out.FinancialInstitutions;
import co.sendik.identity.port.out.SellerVerificationRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la cuenta donde el vendedor recibe. Criterio 4 de HU-002.
 *
 * <p>La coincidencia del titular con el documento (RN-012) no se comprueba aqui: la
 * comprueba el agregado, que es quien tiene los dos nombres delante. Aqui solo se
 * valida que la entidad exista, porque eso exige mirar una tabla y el dominio no
 * conoce la lista.
 *
 * <p>La entidad se valida antes de construir la cuenta para que el fallo llegue como
 * error de negocio. La clave ajena de la base tambien lo impide, pero por ahi sale un
 * 500 y la persona no sabria que su banco no esta en la lista.
 */
public class SubmitBankAccountUseCase {

    private final SellerVerificationRepository verificaciones;
    private final FinancialInstitutions entidades;
    private final Clock reloj;

    public SubmitBankAccountUseCase(
            SellerVerificationRepository verificaciones, FinancialInstitutions entidades, Clock reloj) {
        this.verificaciones = verificaciones;
        this.entidades = entidades;
        this.reloj = reloj;
    }

    @Transactional
    public SellerVerification execute(SubmitBankAccountCommand comando) {
        SellerVerification actual = verificaciones
                .buscarPorUsuario(comando.usuario())
                .orElseThrow(() -> new InvalidVerificationTransitionException(
                        VerificationStatus.NOT_STARTED, VerificationStatus.IN_PROGRESS));

        BankCode entidad = new BankCode(comando.entidad());
        if (!entidades.estaActiva(entidad)) {
            throw new UnknownFinancialInstitutionException(comando.entidad());
        }

        BankAccount cuenta = new BankAccount(
                entidad, comando.tipo(), new BankAccountNumber(comando.numero()), new LegalName(comando.titular()));

        SellerVerification actualizada = actual.conCuentaBancaria(cuenta, reloj.instant());
        verificaciones.guardar(actualizada);

        return actualizada;
    }
}
