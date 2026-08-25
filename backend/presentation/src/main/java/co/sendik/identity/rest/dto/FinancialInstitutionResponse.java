package co.sendik.identity.rest.dto;

/**
 * Una entidad del catalogo, tal como la ofrece el formulario.
 *
 * @param name nombre propio, sin traducir: es el mismo en los dos idiomas
 * @param wallet si es billetera o deposito electronico. La pantalla lo usa para saber
 *     que tipos de cuenta ofrecer, porque una billetera no tiene cuenta de ahorros
 */
public record FinancialInstitutionResponse(String code, String name, boolean wallet) {}
