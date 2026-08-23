package co.sastra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.model.Email;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** La lista de quien arranca siendo moderador y su lectura (HU-006). */
class ModeratorBootstrapPropertiesTest {

    /**
     * La propiedad ausente del todo llega como nulo, y eso no puede reventar el arranque
     * ni obligar a comprobar nulos en cada sitio que la lea. Es el caso normal: nadie
     * configurado.
     */
    @Test
    void deberia_tratar_la_ausencia_de_la_propiedad_como_lista_vacia() {
        assertThat(new ModeratorBootstrapProperties(null).moderators()).isEmpty();
    }

    @Test
    void deberia_reconocer_el_correo_configurado() {
        ModeratorBootstrapProperties propiedades = new ModeratorBootstrapProperties(List.of("moderadora@sastra.co"));

        assertThat(new ConfiguredModeratorEmails(propiedades).incluye(new Email("moderadora@sastra.co")))
                .isTrue();
        assertThat(new ConfiguredModeratorEmails(propiedades).incluye(new Email("otra@sastra.co")))
                .isFalse();
    }

    /**
     * El correo del objeto de valor viene normalizado (RN-001); el de la variable lo
     * escribio una persona. Sin normalizar los dos lados, un `Moderadora@Sastra.CO` en la
     * configuracion no coincidiria y esa persona se quedaria sin acceso sin que nada
     * fallara.
     */
    @Test
    void deberia_reconocerlo_aunque_venga_con_mayusculas_y_espacios() {
        ModeratorBootstrapProperties propiedades =
                new ModeratorBootstrapProperties(List.of("  Moderadora@Sastra.CO  "));

        assertThat(new ConfiguredModeratorEmails(propiedades).incluye(new Email("moderadora@sastra.co")))
                .isTrue();
    }

    /** Con la lista vacia, que es lo normal, nadie es moderador. */
    @Test
    void deberia_no_reconocer_a_nadie_con_la_lista_vacia() {
        assertThat(new ConfiguredModeratorEmails(new ModeratorBootstrapProperties(null))
                        .incluye(new Email("quien@sastra.co")))
                .isFalse();
    }

    /**
     * Una lista de autorizacion que se puede modificar despues de construirla es una
     * lista en la que se puede colar un correo desde cualquier sitio que tenga la
     * referencia.
     */
    @Test
    void deberia_quedar_inmutable_frente_a_la_lista_original() {
        List<String> original = new ArrayList<>(List.of("moderadora@sastra.co"));
        ModeratorBootstrapProperties propiedades = new ModeratorBootstrapProperties(original);

        original.add("colada@sastra.co");

        assertThat(propiedades.moderators()).containsExactly("moderadora@sastra.co");
        assertThatThrownBy(() -> propiedades.moderators().add("otra@sastra.co"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
