package co.sendik.identity.usecase;

import co.sendik.identity.dto.SubmitSelfieCommand;
import co.sendik.identity.exception.InvalidVerificationTransitionException;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.VerificationStatus;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImagePolicy;
import co.sendik.shared.file.NormalizedImage;
import co.sendik.shared.port.out.ImageNormalizer;
import co.sendik.shared.port.out.RestrictedFileStore;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda la selfie. Criterio 3 de HU-002.
 *
 * <p>Al almacen reservado, como el documento: una selfie es la cara de alguien y
 * {@code datos-personales.md} la clasifica sensible. Carpeta propia dentro del mismo
 * almacen, para poder aplicarle una retencion distinta el dia que haga falta.
 *
 * <p><strong>Que se haya tomado en el momento no se comprueba aqui.</strong> Aqui
 * llegan bytes: no hay forma de distinguir una foto de la camara de una de la
 * galeria, y el criterio 3 pide que la interfaz no ofrezca el selector de archivos,
 * no que el servidor lo detecte. Prometer lo segundo seria mentir.
 *
 * <p>La anterior se borra despues de guardar la fila, por el mismo motivo que en el
 * documento: de los dos fallos posibles se elige el que se puede limpiar despues.
 */
public class SubmitSelfieUseCase {

    /** Carpeta propia dentro del almacen reservado. */
    static final String CARPETA = "selfies";

    private final SellerVerificationRepository verificaciones;
    private final RestrictedFileStore almacen;
    private final ImageNormalizer normalizador;
    private final ImagePolicy politica;
    private final Clock reloj;

    public SubmitSelfieUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            ImageNormalizer normalizador,
            ImagePolicy politica,
            Clock reloj) {
        this.verificaciones = verificaciones;
        this.almacen = almacen;
        this.normalizador = normalizador;
        this.politica = politica;
        this.reloj = reloj;
    }

    @Transactional
    public SellerVerification execute(SubmitSelfieCommand comando) {
        SellerVerification actual = verificaciones
                .buscarPorUsuario(comando.usuario())
                .orElseThrow(() -> new InvalidVerificationTransitionException(
                        VerificationStatus.NOT_STARTED, VerificationStatus.IN_PROGRESS));

        politica.exigirTamanoAceptado(comando.contenido().length);
        ImageContentType tipo = politica.exigirTipoAceptado(comando.contenido());

        NormalizedImage imagen = normalizador.normalizar(comando.contenido(), tipo);
        politica.exigirDimensionesAceptadas(imagen.dimensions());

        FileKey nueva = almacen.guardar(CARPETA, imagen);

        FileKey anterior = actual.selfie();
        SellerVerification actualizada = actual.conSelfie(nueva, reloj.instant());
        verificaciones.guardar(actualizada);

        if (anterior != null) {
            almacen.borrar(anterior);
        }

        return actualizada;
    }
}
