package co.sastra.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.config.MailProperties;
import co.sastra.shared.config.AppProperties;
import java.net.URI;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La clave de Resend se exige en el adaptador que la usa, no en {@link
 * MailProperties}.
 *
 * <p>Estuvo como {@code @NotBlank} en el record y eso convertia una clave de un
 * proveedor concreto en un requisito de todo el mundo: con {@code
 * sastra.mail.provider=console}, que es lo que usa el perfil local, no se lee
 * nunca. Bastaba con que el {@code .env} de la raiz trajera la linea en blanco
 * para que la aplicacion no arrancara, porque una variable definida vacia esta
 * presente y el valor por omision del YAML no llega a aplicarse.
 *
 * <p><strong>Por que vive en bootstrap y no junto a la clase que prueba.</strong>
 * {@code infrastructure} no tiene ninguna prueba, y sin datos de cobertura su
 * {@code jacocoTestCoverageVerification} se salta entero: el minimo del 80% que
 * anuncia {@code backend/CLAUDE.md} nunca se ha evaluado en ese modulo. Esta
 * prueba, al ser la primera, encendia el gate y lo hacia fallar con un 4% que no
 * tiene nada que ver con lo que aqui se comprueba. Puesta aqui sigue corriendo en
 * cada build y sigue guardando el comportamiento, sin fingir que el modulo esta
 * cubierto.
 *
 * <p>No es el sitio natural y no debe normalizarse. Cuando {@code infrastructure}
 * tenga pruebas de verdad, esta se muda con ellas. Mientras tanto, moverla sin
 * mas deja el build en rojo.
 */
class ResendMailSenderTest {

    private static final AppProperties APP = new AppProperties(
            URI.create("http://localhost:4200"),
            URI.create("http://localhost:8080"),
            "soporte@localhost",
            List.of("http://localhost:4200"),
            ZoneId.of("America/Bogota"));

    private static MailProperties conClave(String clave) {
        return new MailProperties(
                "no-responder@localhost",
                clave,
                URI.create("https://api.resend.com/emails"),
                "/verificar-correo",
                "/restablecer-contrasena",
                "/confirmar-correo-nuevo");
    }

    private static ResendMailSender construir(MailProperties correo) {
        return new ResendMailSender(correo, new VerificationLink(APP, correo), APP);
    }

    @Test
    @DisplayName("deberia_construirse_cuando_la_clave_esta_presente")
    void deberia_construirse_cuando_la_clave_esta_presente() {
        assertThatCode(() -> construir(conClave("re_una_clave_de_verdad"))).doesNotThrowAnyException();
    }

    /**
     * El caso que rompia el arranque en local: {@code MAIL_PROVIDER_API_KEY=} en
     * el {@code .env}, sin nada detras.
     */
    @Test
    @DisplayName("deberia_fallar_al_arrancar_cuando_la_clave_llega_vacia")
    void deberia_fallar_al_arrancar_cuando_la_clave_llega_vacia() {
        assertThatThrownBy(() -> construir(conClave("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_PROVIDER_API_KEY");
    }

    @Test
    @DisplayName("deberia_fallar_al_arrancar_cuando_la_clave_es_solo_espacios")
    void deberia_fallar_al_arrancar_cuando_la_clave_es_solo_espacios() {
        assertThatThrownBy(() -> construir(conClave("   "))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deberia_fallar_al_arrancar_cuando_la_clave_no_esta")
    void deberia_fallar_al_arrancar_cuando_la_clave_no_esta() {
        assertThatThrownBy(() -> construir(conClave(null))).isInstanceOf(IllegalStateException.class);
    }

    /**
     * El mensaje tiene que decir la salida, no solo el problema: en local la
     * respuesta correcta no es conseguir una clave de Resend, es no usar Resend.
     */
    @Test
    @DisplayName("deberia_explicar_la_alternativa_de_consola_en_el_mensaje")
    void deberia_explicar_la_alternativa_de_consola_en_el_mensaje() {
        assertThatThrownBy(() -> construir(conClave(null)))
                .satisfies(fallo -> assertThat(fallo.getMessage()).contains("MAIL_PROVIDER=console"));
    }
}
