package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cuerpo de {@code PUT /api/v1/users/me}. Criterio 21.
 *
 * <p>PUT y no PATCH: se manda el perfil entero y se guarda entero. Con PATCH
 * habria que distinguir "no mande este campo" de "lo deje vacio", y esa
 * distincion es justo donde se pierde el borrado de un dato.
 *
 * <p>El correo no esta aqui: cambiarlo tiene su propio endpoint porque exige
 * verificar el nuevo antes de reemplazar el anterior.
 *
 * @param city nula o vacia para quitarla. El formato lo juzga el dominio
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 80) String displayName,
        @Nullable @Size(max = 80) String city,
        @Nullable @Size(max = 30) String phone) {}
