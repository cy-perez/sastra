package co.sendik.catalog.rest.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * El objeto de dinero del contrato, en las dos direcciones.
 *
 * <p>Siempre objeto explicito y nunca un numero suelto: contrato-api.md lo exige para
 * que nunca haya duda de si 185000 son pesos o centavos.
 *
 * <p>{@code amount} llega como {@link BigDecimal} y no como {@code long} a proposito. Con
 * un {@code long}, un precio con decimales lo redondearia el deserializador sin que nadie
 * se enterara; asi llega tal cual y lo rechaza {@code Money}, que es quien sabe que el
 * peso no usa decimales (criterio 13).
 */
public record MoneyPayload(@NotNull BigDecimal amount, String currency) {}
