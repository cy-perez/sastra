package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;
import co.sendik.shared.file.FileKey;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lo que de una persona puede ver cualquiera. HU-009, criterio 19.
 *
 * <p><strong>Es un tipo aparte y no el {@code User} recortado, y esa es toda su
 * razon de existir.</strong> Devolver la entidad y confiar en que el borde filtre
 * significa que el dia que alguien agregue un campo —un telefono, una ciudad, la fecha de
 * nacimiento— se publica solo, sin que ninguna prueba lo note. Aqui lo que no esta
 * declarado no tiene por donde salir: es la misma decision que separa
 * {@code PublicListingResponse} de {@code ListingResponse}, y por el mismo motivo.
 *
 * <p>Dentro van dos cosas y no tres. El nombre es publico porque el sitio dice quien
 * vende (textos-web.md) y la foto porque acompana al nombre; el correo no, y por eso no
 * hay campo donde meterlo. La insignia de verificado tampoco esta aqui: no es un dato de
 * identidad sino la respuesta a una pregunta del catalogo, y se responde por su propio
 * puerto.
 *
 * @param id de quien es este perfil
 * @param nombre el nombre visible, tal como lo escribio la persona
 * @param avatar la clave de su foto en el almacen publico, o nulo si no tiene
 */
public record PublicProfileView(
        UserId id, String nombre, @Nullable FileKey avatar) {

    public PublicProfileView {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(nombre, "El nombre es obligatorio");
    }
}
