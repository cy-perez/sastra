package co.sastra.identity.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Correo transaccional (ADR-0012).
 *
 * @param from remitente de todos los mensajes del sistema
 * @param providerApiKey clave de Resend. <strong>No lleva {@code @NotBlank}</strong>:
 *     solo la necesita {@link co.sastra.identity.client.ResendMailSender}, y con
 *     {@code sastra.mail.provider=console} —lo que usa el perfil local— no se
 *     lee nunca. Exigirla aqui obligaba a inventar una clave de Resend para
 *     arrancar en local, y bastaba con que el {@code .env} de la raiz trajera la
 *     linea vacia para que el arranque fallara: una variable definida en blanco
 *     esta presente, asi que el valor por omision del YAML no llega a aplicarse.
 *     Quien la exige es el adaptador que la usa, y lo hace al construirse, que
 *     sigue siendo antes de atender a nadie
 * @param apiUrl direccion de la API del proveedor, parametrizable para poder
 *     apuntar a un servidor simulado en las pruebas
 * @param verificationPath ruta del frontend que recibe el enlace de verificacion.
 *     Se concatena a sastra.app.base-url; no se quema porque la ruta publica
 *     puede cambiar sin que cambie nada del backend
 * @param passwordResetPath lo mismo para el enlace de restablecimiento. Es otra
 *     ruta y no la misma con un parametro: son dos pantallas distintas y dos
 *     tokens con vigencias distintas, 24 horas y 30 minutos
 * @param emailChangePath la pantalla que confirma un correo nuevo (criterio 21)
 */
@Validated
@ConfigurationProperties(prefix = "sastra.mail")
public record MailProperties(
        @NotBlank @Email String from,
        String providerApiKey,
        URI apiUrl,
        @NotBlank String verificationPath,
        @NotBlank String passwordResetPath,
        @NotBlank String emailChangePath) {}
