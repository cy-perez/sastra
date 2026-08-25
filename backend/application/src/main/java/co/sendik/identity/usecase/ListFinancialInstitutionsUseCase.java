package co.sendik.identity.usecase;

import co.sendik.identity.port.out.FinancialInstitutions;
import co.sendik.identity.port.out.FinancialInstitutions.FinancialInstitution;
import java.util.List;

/**
 * Las entidades donde un vendedor puede recibir, para que el formulario las ofrezca.
 *
 * <p>Existe aunque solo delegue, por lo mismo que {@code ReadSellerVerificationUseCase}:
 * entre el controlador y el repositorio va el caso de uso, y {@code ArchitectureTest} lo
 * comprueba.
 */
public class ListFinancialInstitutionsUseCase {

    private final FinancialInstitutions entidades;

    public ListFinancialInstitutionsUseCase(FinancialInstitutions entidades) {
        this.entidades = entidades;
    }

    public List<FinancialInstitution> execute() {
        return entidades.activas();
    }
}
