package co.sastra.shared.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Almacenamiento de archivos (ADR-0018).
 *
 * @param provider {@code local} guarda en el sistema de archivos y sirve para
 *     desarrollo y pruebas; {@code gcs} es Cloud Storage. Lo elige el perfil, no el
 *     despliegue
 * @param localPath raiz de los dos almacenes cuando el proveedor es {@code local}.
 *     Dentro se crean dos carpetas separadas, publica y reservada: son dos almacenes
 *     tambien aqui, no una convencion de nombres
 * @param publicBaseUrl desde donde se sirven los archivos publicos. La direccion se
 *     compone aqui y no se guarda en la base: guardarla ataria cada fila al dominio
 *     de hoy y cambiar de CDN obligaria a reescribir la tabla
 * @param maxImageBytes tope de tamano por imagen. Cloud Run no acepta peticiones de
 *     mas de 32MB, asi que subirlo por encima de eso no sirve de nada
 * @param avatarMinWidth minimo de la foto de perfil. <strong>No es RN-019</strong>:
 *     esa regla fija 900x1200 para las tomas de producto (HU-003), y aplicarsela al
 *     avatar rechazaria casi cualquier foto que alguien tenga a mano. Nadie ha
 *     decidido un minimo para el avatar, asi que el valor por omision es de
 *     arranque y no una regla: evita que se suba un icono de 16px
 * @param avatarMinHeight lo mismo
 */
@Validated
@ConfigurationProperties(prefix = "sastra.storage")
public record StorageProperties(
        @NotBlank String provider,
        @NotNull Path localPath,
        @NotNull URI publicBaseUrl,
        @Positive long maxImageBytes,
        @Positive int avatarMinWidth,
        @Positive int avatarMinHeight) {}
