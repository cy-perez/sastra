package co.sastra.shared.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
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
 * @param projectId proyecto de Google Cloud. Solo con {@code gcs}, y opcional: sin
 *     el, la libreria lo toma de las credenciales por omision, que es lo que pasa
 *     dentro de Cloud Run. Se declara para poder fijarlo en una maquina de
 *     desarrollo donde {@code gcloud} apunte a otro proyecto
 * @param publicBucket cubo de lo que cualquiera puede ver. Obligatorio con
 *     {@code gcs}, ignorado con {@code local}
 * @param restrictedBucket cubo de la cedula y la selfie. <strong>Nunca el mismo que
 *     el publico</strong>: el permiso de lectura para {@code allUsers} se concede por
 *     cubo, asi que un solo cubo para las dos cosas hace publica la cedula de alguien
 *     (RN-046, ADR-0018)
 */
@Validated
@ConfigurationProperties(prefix = "sastra.storage")
public record StorageProperties(
        @NotBlank String provider,
        @NotNull Path localPath,
        @NotNull URI publicBaseUrl,
        @Positive long maxImageBytes,
        @Positive int avatarMinWidth,
        @Positive int avatarMinHeight,
        @Nullable String projectId,
        @Nullable String publicBucket,
        @Nullable String restrictedBucket) {

    /**
     * Los dos cubos no pueden ser el mismo, y esto se comprueba al construir.
     *
     * <p>Es la unica invariante del almacenamiento que no se puede dejar para el
     * momento de usarla: si los dos apuntan al mismo sitio, la aplicacion arranca sin
     * ruido, todo funciona, y la cedula de la primera persona que se verifique queda
     * en un cubo que {@code allUsers} puede leer. No hay ninguna prueba de
     * comportamiento que note eso, porque el comportamiento es correcto.
     */
    public StorageProperties {
        // Se comparan solo si hay algo que comparar. Con `local` los dos llegan
        // vacios desde el YAML, y dos vacios iguales no son el mismo cubo: no hay
        // ninguno.
        if (publicBucket != null && !publicBucket.isBlank() && publicBucket.equals(restrictedBucket)) {
            throw new IllegalArgumentException("El cubo publico y el reservado no pueden ser el mismo: " + publicBucket
                    + ". El permiso de lectura publica se concede por cubo (RN-046, ADR-0018)");
        }
    }

    /**
     * El cubo publico, o un fallo al arrancar si no esta configurado.
     *
     * <p>Falla aqui y no al guardar la primera imagen. Con {@code gcs} y sin cubo, la
     * alternativa es una aplicacion que levanta bien y falla en la primera subida de
     * alguien, que es la peor forma de descubrir una variable que falta
     * (docs/operacion/configuracion.md).
     */
    public String exigirCuboPublico() {
        return exigir(publicBucket, "STORAGE_PUBLIC_BUCKET");
    }

    /** El cubo reservado, con la misma exigencia. */
    public String exigirCuboReservado() {
        return exigir(restrictedBucket, "STORAGE_RESTRICTED_BUCKET");
    }

    private String exigir(@Nullable String cubo, String variable) {
        if (cubo == null || cubo.isBlank()) {
            throw new IllegalStateException(
                    "Falta " + variable + ", que es obligatoria con sastra.storage.provider=gcs");
        }
        return cubo;
    }
}
