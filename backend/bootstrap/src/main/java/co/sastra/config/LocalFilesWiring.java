package co.sastra.config;

import co.sastra.shared.file.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve por HTTP los archivos del almacen publico local.
 *
 * <p><strong>Solo con el almacen local.</strong> En la nube los sirve Cloud Storage
 * y esta ruta no existe (ADR-0018): el backend no es un servidor de archivos, y
 * dejarlo servir imagenes en produccion las haria pasar por Cloud Run pagando
 * computo por cada miniatura del catalogo.
 *
 * <p>Existe porque sin ella el adaptador local estaria mintiendo: compone una
 * direccion publica para cada archivo que guarda, y si nadie sirve esa direccion, la
 * foto de perfil da 404 en desarrollo. El contrato de la API seria distinto en local
 * y en la nube, que es la clase de diferencia que hace que algo funcione en la
 * maquina de quien programa y falle al desplegar.
 *
 * <p>Vive en {@code bootstrap} por el mismo motivo que {@link CorsWiring}: necesita
 * la configuracion tipada, que es de {@code infrastructure}, y un tipo de Spring Web,
 * que es de {@code presentation}, y ningun otro modulo ve las dos cosas.
 *
 * <p><strong>Solo la carpeta publica.</strong> La reservada —cedula y selfie— no se
 * expone por ninguna ruta, ni aqui ni en la nube (RN-046). Que sean dos carpetas
 * distintas es lo que permite servir una sin servir la otra por descuido.
 */
@Configuration
@ConditionalOnProperty(prefix = "sastra.storage", name = "provider", havingValue = "local")
public class LocalFilesWiring implements WebMvcConfigurer {

    private final StorageProperties propiedades;

    public LocalFilesWiring(StorageProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        // toUri() y no la ruta a secas: `file:` mas una ruta relativa se resuelve
        // contra el directorio de trabajo de forma distinta segun quien arranque el
        // proceso, y entonces las imagenes aparecen o no segun desde donde se lance.
        String carpeta = propiedades.localPath().resolve("publico").toUri().toString();

        registro.addResourceHandler("/archivos/**").addResourceLocations(carpeta);
    }
}
