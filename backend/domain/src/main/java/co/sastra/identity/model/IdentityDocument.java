package co.sastra.identity.model;

import co.sastra.shared.file.FileKey;
import java.util.Objects;

/**
 * El documento de identidad que entrega quien quiere vender: tipo, numero, nombre
 * del titular y las dos caras.
 *
 * <p>Las dos caras son obligatorias las dos (criterio 2 de HU-002). Una sola cara
 * no sirve: el numero esta en una y la fecha de vencimiento suele estar en la otra,
 * y sin ella el motivo de rechazo {@code EXPIRED_DOCUMENT} no se puede comprobar.
 *
 * <p><strong>Aqui no hay fecha de vencimiento.</strong> Nadie decidio pedirla, y
 * pedirla seria pedir un dato mas sin uso concreto, que
 * {@code docs/operacion/datos-personales.md} prohibe. Quien mira si el documento
 * vencio es el moderador, sobre la imagen.
 *
 * <p>Las dos claves apuntan al almacen reservado, nunca al publico (RN-046,
 * ADR-0018). El tipo no lo puede garantizar —una {@link FileKey} es una clave y no
 * sabe en que almacen esta—, asi que lo garantiza el caso de uso, que solo tiene
 * inyectado el almacen reservado.
 */
public record IdentityDocument(
        IdentityDocumentType type,
        IdentityDocumentNumber number,
        LegalName holderName,
        FileKey frontImage,
        FileKey backImage) {

    public IdentityDocument {
        Objects.requireNonNull(type, "El tipo de documento es obligatorio");
        Objects.requireNonNull(number, "El numero del documento es obligatorio");
        Objects.requireNonNull(holderName, "El nombre del titular es obligatorio");
        Objects.requireNonNull(frontImage, "La imagen del frente es obligatoria");
        Objects.requireNonNull(backImage, "La imagen del reverso es obligatoria");

        if (frontImage.equals(backImage)) {
            // Es la misma foto subida dos veces. Pasaria todas las validaciones de
            // imagen y llegaria a la revision como un documento completo, con el
            // moderador mirando dos veces el mismo lado.
            throw new IllegalArgumentException("El frente y el reverso no pueden ser la misma imagen");
        }
    }
}
