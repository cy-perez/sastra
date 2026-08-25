package co.sendik;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API de Sendik.
 *
 * <p>Este modulo solo arranca y cablea. Aqui no hay reglas de negocio: viven en
 * {@code domain} y se orquestan desde {@code application}.
 */
@SpringBootApplication(scanBasePackages = "co.sendik")
@ConfigurationPropertiesScan("co.sendik")
public class SendikApplication {

    public static void main(String[] args) {
        SpringApplication.run(SendikApplication.class, args);
    }
}
