package co.sastra.identity.dto;

import co.sastra.identity.model.BankAccountType;
import co.sastra.identity.model.UserId;

/**
 * Registrar la cuenta donde el vendedor recibe. Criterio 4 de HU-002.
 *
 * @param entidad el codigo de la tabla de entidades, no su nombre
 * @param titular tiene que coincidir con el del documento (RN-012)
 */
public record SubmitBankAccountCommand(
        UserId usuario, String entidad, BankAccountType tipo, String numero, String titular) {}
