package co.sendik.catalog.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Los datos del producto que manda el vendedor, al crear y al editar.
 *
 * <p><strong>Casi todo es opcional y no es un descuido</strong>: el criterio 5 dice que un
 * borrador se guarda a medias y que salir y volver retoma donde iba. Lo obligatorio se
 * exige al enviar a revision, no al escribir.
 *
 * <p>La categoria es la excepcion porque sin ella no se sabe que condiciones admite la
 * publicacion ni en que escala se mide (RN-064).
 *
 * <p>Las listas cerradas viajan como cadenas y se convierten en el mapeador. Con la
 * enumeracion en la firma, un valor desconocido produce un fallo de conversion de Spring
 * que nadie mapea y sale como 500; convertido a mano, sale como el 400 de validacion que
 * es (criterios 8 y 9).
 */
public record ProductRequest(
        @NotBlank String categoryId,
        @Nullable String title,
        @Nullable String description,
        @Nullable String brand,
        @Nullable String condition,
        @Nullable SizePayload size,
        @Nullable Map<String, BigDecimal> measurements,
        @Nullable String color,
        @Valid @Nullable MoneyPayload price,
        @Nullable ShippingPayload shipping,
        @Nullable Boolean isSealed,
        @Nullable Integer warrantyMonths) {}
