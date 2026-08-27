package co.sendik.catalog.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Cuerpo del cambio de precio. RN-030 y criterio 28. */
public record ChangePriceRequest(@Valid @NotNull MoneyPayload price) {}
