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
 * @param providerApiKey clave de Resend. En el perfil local no se usa: alli el
 *     adaptador de consola imprime el enlace y no sale ningun correo
 * @param apiUrl direccion de la API del proveedor, parametrizable para poder
 *     apuntar a un servidor simulado en las pruebas
 * @param verificationPath ruta del frontend que recibe el enlace de verificacion.
 *     Se concatena a sastra.app.base-url; no se quema porque la ruta publica
 *     puede cambiar sin que cambie nada del backend
 * @param passwordResetPath lo mismo para el enlace de restablecimiento. Es otra
 *     ruta y no la misma con un parametro: son dos pantallas distintas y dos
 *     tokens con vigencias distintas, 24 horas y 30 minutos
 */
@Validated
@ConfigurationProperties(prefix = "sastra.mail")
public record MailProperties(
        @NotBlank @Email String from,
        @NotBlank String providerApiKey,
        URI apiUrl,
        @NotBlank String verificationPath,
        @NotBlank String passwordResetPath) {}
