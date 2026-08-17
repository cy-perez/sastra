package co.sastra.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Banderas de funcionalidad. Permiten desplegar codigo incompleto sin exponerlo,
 * en lugar de mantener ramas de Git de larga vida.
 *
 * <p>Todas apagadas en Fase 1 (docs/operacion/configuracion.md). Cada una se
 * enciende cuando su fase entra en produccion, no antes.
 *
 * @param sellerVerification verificacion de identidad del vendedor (Fase 2)
 * @param publishing publicacion de prendas (Fase 2)
 * @param checkout proceso de compra y pago (Fase 3)
 * @param search busqueda con Typesense (Fase 3)
 * @param spinViewer visor 360 en la ficha de producto (Fase 2)
 */
@ConfigurationProperties(prefix = "sastra.features")
public record FeatureFlags(
        boolean sellerVerification, boolean publishing, boolean checkout, boolean search, boolean spinViewer) {}
