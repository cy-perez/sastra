package co.sendik.shared.file;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El cliente de Cloud Storage, solo cuando el proveedor es {@code gcs}.
 *
 * <p>Vive en {@code infrastructure} y no en {@code bootstrap} porque no necesita ver
 * dos capas a la vez, que es el unico motivo por el que algo se cablea alli
 * (LocalFilesWiring, CorsWiring). Necesita configuracion, y la configuracion es de
 * esta capa.
 *
 * <p><strong>Credenciales por omision, nunca una clave en el repositorio.</strong>
 * {@code StorageOptions} las busca en este orden: la variable
 * {@code GOOGLE_APPLICATION_CREDENTIALS}, las credenciales de aplicacion que deja
 * {@code gcloud auth application-default login}, y la identidad de la maquina cuando
 * corre dentro de Google. En Cloud Run es lo ultimo: la cuenta de servicio del
 * servicio, sin ningun secreto que rotar. En una maquina de desarrollo es lo segundo.
 * Un archivo de clave JSON descargado no hace falta en ninguno de los dos casos, y es
 * justo el que acaba subido a un repositorio por accidente.
 *
 * <p>El proyecto se fija si esta configurado. Sin el, la libreria toma el de las
 * credenciales, que es lo correcto dentro de Cloud Run y es una fuente de sorpresas
 * en una maquina donde {@code gcloud} apunte a otra parte.
 */
@Configuration
@ConditionalOnProperty(prefix = "sendik.storage", name = "provider", havingValue = "gcs")
public class GcsWiring {

    /**
     * {@code @ConditionalOnMissingBean} para que una prueba de integracion pueda
     * poner un cliente falso sin que este intente resolver credenciales que no
     * existen.
     */
    @Bean
    @ConditionalOnMissingBean
    Storage storage(StorageProperties propiedades) {
        StorageOptions.Builder opciones = StorageOptions.newBuilder();

        String proyecto = propiedades.projectId();
        if (proyecto != null && !proyecto.isBlank()) {
            opciones.setProjectId(proyecto);
        }

        return opciones.build().getService();
    }
}
