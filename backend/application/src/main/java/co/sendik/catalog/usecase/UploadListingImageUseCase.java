package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.UploadListingImageCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImagePolicy;
import co.sendik.shared.file.NormalizedImage;
import co.sendik.shared.port.out.ImageNormalizer;
import co.sendik.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
 *
 * <p>Eso vale para un fallo de la base de datos, que nadie puede prever. Lo que si se
 * puede prever es que el agregado rechace la imagen —posicion fuera de rango, referencia
 * sobre algo que no es tecnologia sellada, estado que no admite cambios—, y ahi el archivo
 * se borra en el acto: es un caso que el cliente puede repetir a voluntad.
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
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        politica.exigirTamanoAceptado(comando.contenido().length);
        ImageContentType tipo = politica.exigirTipoAceptado(comando.contenido());

        NormalizedImage imagen = normalizador.normalizar(comando.contenido(), tipo);
        politica.exigirDimensionesAceptadas(imagen.dimensions());

        FileKey clave = almacen.guardar(CARPETA, imagen);
        Instant ahora = Instant.now(reloj);

        Optional<FileKey> reemplazada = claveEnLaMismaPosicion(actual, comando);

        Listing guardada;
        try {
            ProductImage nueva = construir(comando, clave, imagen);
            Listing conImagen = actual.conImagen(nueva, ahora);

            if (comando.desdeGaleria()) {
                conImagen = conImagen.marcarCargaDesdeGaleria(ahora);
            }
            guardada = publicaciones.guardar(conImagen);
        } catch (RuntimeException rechazada) {
            // El archivo ya esta en el almacen publico y la publicacion no lo quiso: una
            // posicion fuera de rango, una imagen de referencia sobre algo que no es
            // tecnologia sellada (RN-066) o un estado que no admite cambios. La
            // transaccion revierte la fila; el almacen no es transaccional y nadie mas va
            // a volver por el.
            //
            // Sin esto, repetir una peticion invalida deja un archivo publico por intento
            // y nada lo limita: esta ruta no pasa por el limitador de tasa.
            almacen.borrar(clave);
            throw rechazada;
        }

        // Criterio 16: «la anterior se borra del almacen». Despues de guardar la fila,
        // por el mismo motivo que en el avatar: de los dos fallos posibles se elige el
        // que se puede limpiar. Y no es solo espacio: una toma que el vendedor sustituyo
        // al ver que salia su cara o su direccion seguiria servida para siempre, con
        // cache de un ano, y eso convierte «suprimir» en «dejar de enlazar» (Ley 1581).
        reemplazada.ifPresent(almacen::borrar);
        return guardada;
    }

    /** La que ocupaba esa posicion y esa clase, si habia alguna. */
    private static Optional<FileKey> claveEnLaMismaPosicion(Listing actual, UploadListingImageCommand comando) {
        return actual.images().stream()
                .filter(imagen -> imagen.kind() == comando.clase() && imagen.position() == comando.posicion())
                .map(ProductImage::objectKey)
                .findFirst();
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
