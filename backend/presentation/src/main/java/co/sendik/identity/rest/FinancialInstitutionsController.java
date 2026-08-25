package co.sendik.identity.rest;

import co.sendik.identity.rest.dto.FinancialInstitutionResponse;
import co.sendik.identity.usecase.ListFinancialInstitutionsUseCase;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catalogo de entidades financieras. Lo pide el formulario de la cuenta bancaria
 * (criterio 4 de HU-002).
 *
 * <p><strong>Exige token pero no rol.</strong> No hay nada personal aqui: son
 * veintiocho nombres de bancos, los mismos para todo el mundo. Se pide token igual
 * porque solo lo necesita quien esta verificandose, y una lista abierta es una ruta mas
 * que responder sin motivo.
 *
 * <p>Ruta propia y no bajo la verificacion: el catalogo no es parte de la solicitud de
 * nadie. Cuando la Fase 3 lo necesite para el desembolso, lo pedira aqui mismo.
 */
@RestController
@RequestMapping("/api/v1/financial-institutions")
@ConditionalOnProperty(prefix = "sendik.features", name = "seller-verification", havingValue = "true")
public class FinancialInstitutionsController {

    private final ListFinancialInstitutionsUseCase caso;

    public FinancialInstitutionsController(ListFinancialInstitutionsUseCase caso) {
        this.caso = caso;
    }

    @GetMapping
    public List<FinancialInstitutionResponse> activas() {
        return caso.execute().stream()
                .map(entidad -> new FinancialInstitutionResponse(entidad.code(), entidad.name(), entidad.wallet()))
                .toList();
    }
}
