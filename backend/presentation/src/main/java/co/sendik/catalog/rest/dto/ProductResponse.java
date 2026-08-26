package co.sendik.catalog.rest.dto;

import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** El producto tal como sale de la API. Todo opcional, porque un borrador lo es. */
public record ProductResponse(
        String categoryId,
        @Nullable String title,
        @Nullable String description,
        @Nullable String brand,
        @Nullable String condition,
        @Nullable SizePayload size,
        Map<String, BigDecimal> measurements,
        @Nullable String color,
        @Nullable MoneyPayload price,
        @Nullable ShippingPayload shipping,
        @Nullable Boolean isSealed,
        @Nullable Integer warrantyMonths) {}
