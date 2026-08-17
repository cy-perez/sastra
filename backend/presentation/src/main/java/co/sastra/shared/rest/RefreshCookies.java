package co.sastra.shared.rest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseCookie;

/**
 * La cookie que transporta el token de refresco (ADR-0003).
 *
 * <p>Los cuatro atributos son la proteccion, no un adorno:
 *
 * <ul>
 *   <li>{@code HttpOnly} para que ningun script de la pagina pueda leer una
 *       credencial de 30 dias.
 *   <li>{@code Secure} para que no viaje por HTTP sin cifrar.
 *   <li>{@code SameSite=Strict} para que otro sitio no pueda provocar un refresco
 *       desde el navegador de quien tiene la sesion abierta. Es tambien lo que
 *       sostiene que la API pueda estar sin CSRF.
 *   <li>{@code Path} limitado a las rutas de sesion, para que la cookie no se envie
 *       en ninguna otra peticion.
 * </ul>
 *
 * <p>No lleva {@code @Component}: sus valores salen de la configuracion, que vive en
 * {@code infrastructure}, una capa que este modulo no ve. La construye
 * {@code bootstrap}, igual que el origen de CORS.
 */
public final class RefreshCookies {

    private final String nombre;
    private final String ruta;
    private final boolean segura;
    private final Duration vigencia;

    public RefreshCookies(String nombre, String ruta, boolean segura, Duration vigencia) {
        this.nombre = Objects.requireNonNull(nombre);
        this.ruta = Objects.requireNonNull(ruta);
        this.segura = segura;
        this.vigencia = Objects.requireNonNull(vigencia);
    }

    /** La cookie con el token recien emitido. */
    public ResponseCookie con(String tokenDeRefresco) {
        return base(tokenDeRefresco).maxAge(vigencia).build();
    }

    /**
     * La cookie que borra la anterior: mismo nombre y misma ruta, valor vacio y
     * caducidad cero.
     *
     * <p>Los atributos tienen que coincidir con los de la original o el navegador la
     * trata como otra cookie y la vieja se queda. Es la razon de que las dos se
     * construyan aqui y no en el controlador.
     */
    public ResponseCookie vacia() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /** Lee la cookie de la peticion. El nombre es configuracion, asi que no cabe un {@code @CookieValue}. */
    public Optional<String> leerDe(HttpServletRequest peticion) {
        Cookie[] galletas = peticion.getCookies();
        if (galletas == null) {
            return Optional.empty();
        }

        return Arrays.stream(galletas)
                .filter(galleta -> nombre.equals(galleta.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(segura)
                .sameSite("Strict")
                .path(ruta);
    }
}
