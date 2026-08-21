package co.sastra.shared.file;

/**
 * Ancho y alto en pixeles de una imagen ya decodificada.
 *
 * <p>Quien las lee es un adaptador: decodificar es cosa de infraestructura. Aqui
 * llegan como dos numeros para que las reglas que las usan —RN-019 y la proporcion
 * de RN-018— se puedan probar sin ningun archivo.
 */
public record ImageDimensions(int width, int height) {

    public ImageDimensions {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Las dimensiones tienen que ser positivas: " + width + "x" + height);
        }
    }

    public boolean alcanza(ImageDimensions minimo) {
        return width >= minimo.width() && height >= minimo.height();
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
