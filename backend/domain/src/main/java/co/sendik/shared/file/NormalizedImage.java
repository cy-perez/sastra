package co.sendik.shared.file;

import java.util.Objects;

/**
 * Una imagen ya decodificada y vuelta a codificar, sin metadatos y con sus
 * dimensiones conocidas.
 *
 * <p><strong>Es lo unico que llega a guardarse.</strong> Nunca se guardan los bytes
 * tal como llegaron, y eso es deliberado (ADR-0018): volver a codificar borra el
 * EXIF de una vez y sin tener que entender su formato, y ademas prueba que lo
 * subido era una imagen de verdad —si no se puede decodificar, no hay nada que
 * recodificar—.
 *
 * <p>El EXIF importa mas de lo que parece. Lleva las coordenadas GPS de donde se
 * tomo la foto, asi que una toma de producto publicada con su EXIF dice donde vive
 * el vendedor. Aplica igual a la selfie de HU-002.
 *
 * <p>Los bytes no se copian al entrar ni al salir. Es una imagen entera en memoria
 * y copiarla dos veces por cada peticion se paga en un servicio que escala a cero;
 * quien la recibe no la modifica.
 */
public record NormalizedImage(byte[] content, ImageContentType type, ImageDimensions dimensions) {

    public NormalizedImage {
        Objects.requireNonNull(content, "El contenido es obligatorio");
        Objects.requireNonNull(type, "El tipo es obligatorio");
        Objects.requireNonNull(dimensions, "Las dimensiones son obligatorias");

        if (content.length == 0) {
            throw new IllegalArgumentException("Una imagen normalizada no puede venir vacia");
        }
    }

    public long bytes() {
        return content.length;
    }
}
