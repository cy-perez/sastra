package co.sastra.identity.usecase;

import co.sastra.identity.dto.SubmitIdentityDocumentCommand;
import co.sastra.identity.exception.DocumentAlreadyVerifiedException;
import co.sastra.identity.exception.InvalidVerificationTransitionException;
import co.sastra.identity.model.IdentityDocument;
import co.sastra.identity.model.IdentityDocumentNumber;
import co.sastra.identity.model.LegalName;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.VerificationStatus;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImagePolicy;
import co.sastra.shared.file.NormalizedImage;
import co.sastra.shared.port.out.ImageNormalizer;
import co.sastra.shared.port.out.RestrictedFileStore;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda el documento de identidad por las dos caras. Criterio 2 de HU-002.
 *
 * <p><strong>Las dos imagenes van al almacen reservado</strong>, nunca al publico
 * (RN-046, ADR-0018). Este caso de uso solo tiene inyectado el reservado, y eso no es
 * una precaucion sino la garantia: no existe forma de escribir aqui una linea que
 * publique una cedula.
 *
 * <p>El orden de los pasos es el mismo que en la foto de perfil y por los mismos
 * motivos: tamano antes de decodificar, tipo por los bytes de cabecera, normalizar
 * para quitar el EXIF —que en la foto de una cedula lleva las coordenadas de donde se
 * tomo— y dimensiones al final, que solo existen despues de decodificar.
 *
 * <p><strong>El criterio 5 se comprueba antes de guardar los archivos.</strong> Si el
 * documento ya esta verificado en otra cuenta, la solicitud no va a llegar a ninguna
 * parte, y subir dos imagenes de la cedula de alguien para despues rechazarlas es
 * guardar dos imagenes que no habia por que guardar.
 *
 * <p>Los archivos anteriores se borran despues de guardar la fila, nunca antes. Si se
 * borraran primero y el guardado fallara, la solicitud apuntaria a imagenes que ya no
 * existen y el moderador no tendria que mirar. Al reves, lo que queda es un archivo
 * huerfano que cuesta unos centimos.
 */
public class SubmitIdentityDocumentUseCase {

    /** Agrupa los documentos dentro del almacen reservado. */
    static final String CARPETA = "documentos";

    private final SellerVerificationRepository verificaciones;
    private final RestrictedFileStore almacen;
    private final ImageNormalizer normalizador;
    private final ImagePolicy politica;
    private final Clock reloj;

    public SubmitIdentityDocumentUseCase(
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
    public SellerVerification execute(SubmitIdentityDocumentCommand comando) {
        SellerVerification actual = verificaciones
                .buscarPorUsuario(comando.usuario())
                .orElseThrow(() -> new InvalidVerificationTransitionException(
                        VerificationStatus.NOT_STARTED, VerificationStatus.IN_PROGRESS));

        IdentityDocumentNumber numero = new IdentityDocumentNumber(comando.numero());

        if (verificaciones.existeOtraVerificadaConDocumento(numero.value(), comando.usuario())) {
            throw new DocumentAlreadyVerifiedException();
        }

        FileKey frente = guardarImagen(comando.frente());
        FileKey reverso = guardarImagen(comando.reverso());

        IdentityDocument documento =
                new IdentityDocument(comando.tipo(), numero, new LegalName(comando.titular()), frente, reverso);

        IdentityDocument anterior = actual.document();
        SellerVerification actualizada = actual.conDocumento(documento, reloj.instant());
        verificaciones.guardar(actualizada);

        if (anterior != null) {
            almacen.borrar(anterior.frontImage());
            almacen.borrar(anterior.backImage());
        }

        return actualizada;
    }

    private FileKey guardarImagen(byte[] contenido) {
        politica.exigirTamanoAceptado(contenido.length);
        ImageContentType tipo = politica.exigirTipoAceptado(contenido);

        NormalizedImage imagen = normalizador.normalizar(contenido, tipo);
        politica.exigirDimensionesAceptadas(imagen.dimensions());

        return almacen.guardar(CARPETA, imagen);
    }
}
