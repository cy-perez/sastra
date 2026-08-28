package co.sendik.catalog.dto;

import co.sendik.catalog.model.SellerId;
import co.sendik.shared.file.FileKey;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * El vendedor tal como lo pinta el catalogo. HU-009, criterios 15, 18 y 19.
 *
 * <p>Tres campos, y ninguno mas cabe. El nombre y la foto los sirve {@code identity} en su
 * propia forma publica; la insignia la responde el catalogo preguntando si puede publicar,
 * que es la misma condicion que RN-011 exige para tener el sello.
 *
 * <p><strong>La insignia no dice «buen vendedor».</strong> Dice que Sendik confirmo su
 * identidad y su cuenta bancaria, que es lo que verifica HU-002 y lo unico que el texto
 * del sitio promete.
 *
 * @param id de quien es este perfil
 * @param nombre el nombre visible
 * @param avatar la clave de su foto en el almacen publico, o nulo
 * @param verificado si tiene el sello vigente
 */
public record SellerProfileView(
        SellerId id, String nombre, @Nullable FileKey avatar, boolean verificado) {

    public SellerProfileView {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(nombre, "El nombre es obligatorio");
    }
}
