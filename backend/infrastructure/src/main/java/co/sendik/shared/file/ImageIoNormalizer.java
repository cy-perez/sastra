package co.sendik.shared.file;

import co.sendik.shared.port.out.ImageNormalizer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Decodifica y vuelve a codificar con {@code javax.imageio}, que viene en el JDK.
 *
 * <p>Sin ninguna libreria de imagenes de terceros, y no por ahorrar una
 * dependencia: lo que hace falta aqui es decodificar formatos que el JDK ya
 * decodifica. Una libreria de procesamiento entraria para redimensionar o
 * recortar, y eso ocurre en el cliente (RN-018).
 *
 * <p><strong>Volver a codificar es lo que borra el EXIF.</strong> No se recorre el
 * archivo buscando los bloques de metadatos para quitarlos: se decodifica a
 * pixeles y se escribe de nuevo, y lo que sale lleva unicamente pixeles. Es mas
 * barato de razonar y no depende de entender cada variante de cada formato. El
 * EXIF importa porque lleva coordenadas GPS: una foto publicada con su EXIF dice
 * donde se tomo.
 *
 * <p>Efecto colateral buscado: si el contenido no era una imagen de verdad
 * —empieza con la firma correcta y sigue con basura—, no se puede decodificar y
 * aqui se para. La comprobacion de los bytes de cabecera sola no da esa garantia.
 */
@Component
public class ImageIoNormalizer implements ImageNormalizer {

    @Override
    public NormalizedImage normalizar(byte[] contenido, ImageContentType tipo) {
        BufferedImage pixeles = decodificar(contenido);
        byte[] recodificado = codificar(pixeles, tipo);

        return new NormalizedImage(recodificado, tipo, new ImageDimensions(pixeles.getWidth(), pixeles.getHeight()));
    }

    private static BufferedImage decodificar(byte[] contenido) {
        try {
            BufferedImage leida = ImageIO.read(new ByteArrayInputStream(contenido));
            if (leida == null) {
                // ImageIO devuelve nulo, sin excepcion, cuando ningun lector
                // reconoce el contenido. Es el caso normal de "esto no era una
                // imagen", asi que se traduce al error del puerto.
                throw new NoSePudoLeerLaImagenException(new IOException("Ningun lector reconocio el contenido"));
            }
            return leida;
        } catch (IOException fallo) {
            throw new NoSePudoLeerLaImagenException(fallo);
        }
    }

    private static byte[] codificar(BufferedImage pixeles, ImageContentType salida) {
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        try {
            // JPEG no tiene canal alfa: escribir una imagen con transparencia como
            // JPEG produce colores rotos, no un error. Se pasa a RGB antes.
            BufferedImage escribible = salida == ImageContentType.JPEG ? sinTransparencia(pixeles) : pixeles;

            if (!ImageIO.write(escribible, salida.extension(), destino)) {
                throw new NoSePudoLeerLaImagenException(
                        new IOException("Ningun escritor disponible para " + salida.mediaType()));
            }
            return destino.toByteArray();
        } catch (IOException fallo) {
            throw new NoSePudoLeerLaImagenException(fallo);
        }
    }

    private static BufferedImage sinTransparencia(BufferedImage original) {
        if (original.getTransparency() == BufferedImage.OPAQUE) {
            return original;
        }
        BufferedImage opaca = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        var lienzo = opaca.createGraphics();
        try {
            // Fondo blanco y no negro: lo transparente de una foto de producto
            // recortada es el borde, y en blanco se ve como un margen en vez de
            // como un marco.
            lienzo.setColor(java.awt.Color.WHITE);
            lienzo.fillRect(0, 0, original.getWidth(), original.getHeight());
            lienzo.drawImage(original, 0, 0, null);
        } finally {
            lienzo.dispose();
        }
        return opaca;
    }
}
