package co.sastra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API de Sastra.
 *
 * <p>Este modulo solo arranca y cablea. Aqui no hay reglas de negocio: viven en
 * {@code domain} y se orquestan desde {@code application}.
 */
@SpringBootApplication(scanBasePackages = "co.sastra")
@ConfigurationPropertiesScan("co.sastra")
public class SastraApplication {

    public static void main(String[] args) {
        SpringApplication.run(SastraApplication.class, args);
    }
}
