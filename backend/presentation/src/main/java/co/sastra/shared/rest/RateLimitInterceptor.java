package co.sastra.shared.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limita cuantas peticiones acepta cada origen en las rutas de cuenta.
 *
 * <p>RN-006 protege <em>una</em> cuenta, no el endpoint. Sin este limite, cinco
 * intentos por cuenta no impiden probar una contrasena comun contra todas las
 * cuentas que se quiera, ni usar el registro como emisor de correo gratuito
 * contra la cuota del proveedor, ni mantener bloqueada indefinidamente la cuenta
 * de alguien cuyo correo se conozca.
 *
 * <p>Son dos limites y no uno porque las rutas no se parecen. Escribir
 * credenciales es un acto humano y poco frecuente; renovar la sesion lo hace el
 * navegador solo, y con varias pestanas abiertas se dispara mas veces sin que
 * nadie haga nada raro. Un limite unico tendria que ser el mas flojo de los dos.
 *
 * <p>Se cuenta por IP y no por cuenta: por cuenta ya cuenta RN-006, y en el
 * registro no hay cuenta todavia que contar. La IP llega hasheada, asi que este
 * limite no conserva ningun dato de localizacion
 * (docs/operacion/datos-personales.md).
 *
 * <p>Es un {@link HandlerInterceptor} y no un filtro de servlet para que la
 * excepcion pase por {@link ApiExceptionHandler} y el 429 salga con el mismo
 * {@code ProblemDetail} que los demas errores. Un filtro corre fuera del
 * despachador y tendria que escribir ese cuerpo a mano.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Rutas donde se escriben o se piden credenciales. */
    private static final String[] RUTAS_DE_CREDENCIALES = {
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/resend-verification",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
    };

    private static final String PREFIJO_DE_SESION = "/api/v1/auth/";

    private final RateLimiter credenciales;
    private final RateLimiter sesion;
    private final ClientIpHasher hasherDeIp;
    private final Clock reloj;

    public RateLimitInterceptor(RateLimiter credenciales, RateLimiter sesion, ClientIpHasher hasherDeIp, Clock reloj) {
        this.credenciales = credenciales;
        this.sesion = sesion;
        this.hasherDeIp = hasherDeIp;
        this.reloj = reloj;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
        RateLimiter limite = limiteDe(peticion.getRequestURI());
        if (limite == null) {
            return true;
        }

        // Sin IP no hay a quien contar. Ocurre en pruebas y en llamadas internas;
        // dejar pasar es preferible a rechazar a todo el que no traiga direccion.
        String origen = hasherDeIp.hashear(peticion);
        if (origen == null) {
            return true;
        }

        Instant ahora = reloj.instant();
        Optional<Duration> espera = limite.registrar(clave(origen, peticion), ahora);
        if (espera.isPresent()) {
            throw new RateLimitExceededException(espera.get());
        }
        return true;
    }

    /**
     * Cada ruta cuenta por separado dentro de su grupo.
     *
     * <p>Si compartieran cuenta, agotar el limite entrando mal dejaria sin poder
     * registrarse a todo el que salga por la misma IP, que en una oficina o detras
     * de un operador movil es mucha gente que no ha hecho nada.
     */
    private static String clave(String origen, HttpServletRequest peticion) {
        return origen + " " + peticion.getRequestURI();
    }

    private @Nullable RateLimiter limiteDe(String ruta) {
        for (String credencial : RUTAS_DE_CREDENCIALES) {
            if (ruta.equals(credencial)) {
                return credenciales;
            }
        }
        return ruta.startsWith(PREFIJO_DE_SESION) ? sesion : null;
    }
}
