package co.sastra.identity.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Todo lo que Sastra guarda de una persona, para que se lo lleve. Criterio 22.
 *
 * <p><strong>Lo que no esta aqui importa tanto como lo que esta.</strong> No lleva
 * el hash de la contrasena ni el de ningun token: son secretos del sistema, no
 * datos de la persona, y entregarlos no le sirve de nada mientras que exponerlos
 * si tiene coste (docs/operacion/datos-personales.md). Tampoco la IP hasheada de
 * los consentimientos, por lo mismo: un hash no le dice nada a quien lo recibe.
 *
 * <p>Si lleva la evidencia de consentimiento con su version y su fecha, que es
 * justamente lo que le permite comprobar a que dijo que si.
 *
 * @param generado cuando se produjo este archivo, para que quien lo lea sepa a que
 *     momento corresponde
 */
public record UserDataExport(
        Instant generado, Cuenta cuenta, List<Consentimiento> consentimientos, List<Sesion> sesiones) {

    public record Cuenta(
            String id,
            String correo,
            String nombre,
            LocalDate fechaDeNacimiento,
            String idioma,
            String estado,
            boolean correoVerificado,
            @Nullable Instant correoVerificadoEl,
            List<String> roles,
            Instant creadaEl) {}

    public record Consentimiento(String documento, String version, Instant aceptadoEl) {}

    /** Las mismas que muestra el criterio 17, y por el mismo motivo sin la IP. */
    public record Sesion(@Nullable String navegador, Instant iniciada, Instant expira) {}
}
