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
 * <p><strong>Lo que si esta, esta entero.</strong> Todo dato personal que la
 * persona puede editar en su perfil tiene que salir aqui: ciudad y telefono son
 * datos suyos —el telefono clasificado como interno en
 * docs/operacion/datos-personales.md— y omitirlos convierte el derecho a conocer
 * en un resumen. La regla para decidir si un campo entra no es si parece
 * interesante, sino si es de la persona o del sistema.
 *
 * @param generado cuando se produjo este archivo, para que quien lo lea sepa a que
 *     momento corresponde
 */
public record UserDataExport(
        Instant generado, Cuenta cuenta, List<Consentimiento> consentimientos, List<Sesion> sesiones) {

    /**
     * @param ciudad nula si nunca se puso o si se quito. Se emite igual con valor
     *     nulo en lugar de omitirse: "no tenemos tu ciudad" es una respuesta al
     *     derecho a conocer, y una clave ausente no la da
     * @param telefono lo mismo. Es dato interno, no publico
     *     (docs/operacion/datos-personales.md), pero interno significa que solo lo
     *     ve su titular, y este archivo es justamente para su titular
     */
    public record Cuenta(
            String id,
            String correo,
            String nombre,
            LocalDate fechaDeNacimiento,
            @Nullable String ciudad,
            @Nullable String telefono,
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
