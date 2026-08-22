package co.sastra.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** La lista de quien arranca siendo moderador (HU-006). */
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
