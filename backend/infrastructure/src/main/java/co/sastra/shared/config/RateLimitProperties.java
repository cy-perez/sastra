package co.sastra.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cuantas peticiones se aceptan de cada origen en las rutas de cuenta.
 *
 * <p>Son dos grupos porque las rutas no se parecen. Escribir credenciales es un
 * acto humano y poco frecuente; renovar la sesion lo hace el navegador solo y con
 * varias pestanas abiertas se dispara mas veces sin que nadie haga nada raro. Un
 * limite unico tendria que ser el mas flojo de los dos, y entonces no defenderia
 * del primero.
 *
 * @param credentials rutas donde se escriben o se piden credenciales: ingreso,
 *     registro, verificacion y recuperacion
 * @param session el resto de {@code /api/v1/auth}: refresco y cierre
 * @param maxTrackedKeys techo de origenes vivos en memoria, comun a los dos
 *     grupos. Sin techo, quien varie su IP a voluntad haria crecer el mapa hasta
 *     agotar la memoria y la defensa seria la via de ataque
 */
@Validated
@ConfigurationProperties(prefix = "sastra.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Grupo credentials,
        @NotNull @Valid Grupo session,
        @Min(1) int maxTrackedKeys) {

    /**
     * @param maxRequests peticiones permitidas dentro de una ventana
     * @param window cuanto dura la ventana
     */
    public record Grupo(@Min(1) int maxRequests, @NotNull Duration window) {}
}
