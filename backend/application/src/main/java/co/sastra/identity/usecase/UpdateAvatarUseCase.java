package co.sastra.identity.usecase;

import co.sastra.identity.dto.UpdateAvatarCommand;
import co.sastra.identity.exception.AccountNoLongerExistsException;
import co.sastra.identity.model.User;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImagePolicy;
import co.sastra.shared.file.NormalizedImage;
import co.sastra.shared.port.out.ImageNormalizer;
import co.sastra.shared.port.out.PublicFileStore;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criterio 21 de HU-001: la foto de perfil.
 *
 * <p>El orden de los pasos es la parte que importa, y no es arbitrario:
 *
 * <ol>
 *   <li><strong>El tamano primero.</strong> Es lo unico que se comprueba sin tocar
 *       el contenido. Decodificar un archivo enorme para descubrir despues que era
 *       enorme es regalarle a quien lo suba el trabajo de decodificarlo.
 *   <li><strong>El tipo, por los bytes de cabecera.</strong> Ni la extension ni el
 *       {@code Content-Type}: los dos los pone quien sube.
 *   <li><strong>Normalizar.</strong> Decodificar y volver a codificar quita el EXIF
 *       —que lleva coordenadas GPS— y de paso demuestra que era una imagen.
 *   <li><strong>Las dimensiones al final</strong>, porque solo existen despues de
 *       decodificar.
 *   <li><strong>Guardar el archivo, guardar la cuenta, borrar el anterior.</strong>
 *       En ese orden.
 * </ol>
 *
 * <p>El ultimo punto tiene su motivo. Si se borrara el archivo viejo antes de
 * guardar la fila y el guardado fallara, la cuenta quedaria apuntando a un archivo
 * que ya no existe: la persona veria su perfil roto y no habria forma de
 * recuperarlo. Al reves, si el borrado falla, lo que queda es un archivo huerfano
 * que cuesta unos centimos. De los dos fallos posibles se elige el que se puede
 * limpiar despues.
 *
 * <p>Aqui no hay ningun {@code try} alrededor del borrado, y no es un olvido: el
 * contrato de {@link PublicFileStore#borrar} es que no falla. Quien registra el
 * archivo que quedo suelto es el adaptador, que es donde vive el registro; esta
 * capa no declara mas dependencias que {@code spring-tx} (backend/CLAUDE.md).
 */
public class UpdateAvatarUseCase {

    /** Agrupa las fotos de perfil dentro del almacen publico. */
    static final String CARPETA = "avatares";

    private final UserRepository usuarios;
    private final PublicFileStore almacen;
    private final ImageNormalizer normalizador;
    private final ImagePolicy politica;

    public UpdateAvatarUseCase(
            UserRepository usuarios, PublicFileStore almacen, ImageNormalizer normalizador, ImagePolicy politica) {
        this.usuarios = usuarios;
        this.almacen = almacen;
        this.normalizador = normalizador;
        this.politica = politica;
    }

    @Transactional
    public User execute(UpdateAvatarCommand comando) {
        User cuenta = usuarios.buscarPorId(comando.usuario()).orElseThrow(AccountNoLongerExistsException::new);

        politica.exigirTamanoAceptado(comando.contenido().length);
        ImageContentType tipo = politica.exigirTipoAceptado(comando.contenido());

        NormalizedImage imagen = normalizador.normalizar(comando.contenido(), tipo);
        politica.exigirDimensionesAceptadas(imagen.dimensions());

        FileKey nueva = almacen.guardar(CARPETA, imagen);

        User.CambioDeAvatar cambio = cuenta.conAvatar(nueva);
        usuarios.actualizar(cambio.cuenta());

        if (cambio.anterior() != null) {
            almacen.borrar(cambio.anterior());
        }

        return cambio.cuenta();
    }
}
