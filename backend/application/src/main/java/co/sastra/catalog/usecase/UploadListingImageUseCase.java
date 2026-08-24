package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.UploadListingImageCommand;
import co.sastra.catalog.model.ImageKind;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ProductImage;
import co.sastra.catalog.model.ProductImageId;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImagePolicy;
import co.sastra.shared.file.NormalizedImage;
import co.sastra.shared.port.out.ImageNormalizer;
import co.sastra.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sube una toma o una imagen de referencia. Criterios 14, 15, 16 y 18.
 *
 * <p>Las tres comprobaciones ocurren <strong>antes</strong> de que el archivo exista en
 * el almacen, que es la razon de que la subida vaya por el backend y no por URL firmada
 * (ADR-0018): el tipo real por los bytes de cabecera, el tamano, y las dimensiones
 * minimas de RN-019 sobre la imagen ya decodificada. El EXIF lo quita el normalizador,
 * porque una toma publicada con su EXIF dice donde vive el vendedor.
 *
 * <p>Van al <strong>almacen publico</strong>: son fotos que el catalogo sirve a
 * cualquiera. Una consecuencia que se acepta a conciencia es que la toma de un borrador
 * ya esta ahi antes de que la publicacion sea visible; no esta enlazada y su clave no
 * es adivinable, y es la foto de un producto que el vendedor va a publicar.
 *
 * <p>La imagen se guarda antes que la fila. De los dos fallos posibles se elige el que
 * se puede limpiar despues: un archivo huerfano en el almacen se barre, una fila que
 * apunta a un archivo que no existe rompe la ficha.
 */
public class UploadListingImageUseCase {

    /** Carpeta propia dentro del almacen publico. */
    static final String CARPETA = "productos";

    private final ListingRepository publicaciones;
    private final PublicFileStore almacen;
    private final ImageNormalizer normalizador;
    private final ImagePolicy politica;
    private final Clock reloj;

    public UploadListingImageUseCase(
            ListingRepository publicaciones,
            PublicFileStore almacen,
            ImageNormalizer normalizador,
            ImagePolicy politica,
            Clock reloj) {
        this.publicaciones = publicaciones;
        this.almacen = almacen;
        this.normalizador = normalizador;
        this.politica = politica;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(UploadListingImageCommand comando) {
        Listing actual = ListingAccess.deVendedor(publicaciones, comando.publicacion(), comando.vendedor());

        politica.exigirTamanoAceptado(comando.contenido().length);
        ImageContentType tipo = politica.exigirTipoAceptado(comando.contenido());

        NormalizedImage imagen = normalizador.normalizar(comando.contenido(), tipo);
        politica.exigirDimensionesAceptadas(imagen.dimensions());

        FileKey clave = almacen.guardar(CARPETA, imagen);
        Instant ahora = Instant.now(reloj);

        ProductImage nueva = construir(comando, clave, imagen);
        Listing conImagen = actual.conImagen(nueva, ahora);

        if (comando.desdeGaleria()) {
            conImagen = conImagen.marcarCargaDesdeGaleria(ahora);
        }
        return publicaciones.guardar(conImagen);
    }

    private static ProductImage construir(UploadListingImageCommand comando, FileKey clave, NormalizedImage imagen) {
        ProductImageId id = ProductImageId.nuevo();

        if (comando.clase() == ImageKind.REFERENCE) {
            return ProductImage.referencia(
                    id, clave, comando.posicion(), imagen.dimensions(), imagen.bytes(), imagen.type());
        }
        return ProductImage.toma(id, clave, comando.posicion(), imagen.dimensions(), imagen.bytes(), imagen.type());
    }
}
