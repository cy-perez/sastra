package co.sendik.identity.usecase;

import co.sendik.identity.dto.VerificationImageContent;
import co.sendik.identity.dto.ViewVerificationImageCommand;
import co.sendik.identity.exception.VerificationNotFoundException;
import co.sendik.identity.model.IdentityDocument;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.VerificationImage;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.identity.port.out.VerificationAccessLog;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.port.out.RestrictedFileStore;
import java.time.Clock;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrega una de las tres imagenes de una verificacion al proceso de revision, dejando
 * constancia de quien la miro (RN-046, HU-002).
 *
 * <p><strong>Esto es lo que existe en lugar de una URL firmada.</strong> ADR-0018 decidio
 * que el almacen reservado no se sirve por ninguna direccion, y la historia pide que todo
 * acceso quede en bitacora con actor y motivo: un enlace que funciona por si solo no
 * puede registrar quien lo uso. El precio es que los bytes pasan por Cloud Run; lo que se
 * compra es que la bitacora sea cierta.
 *
 * <p><strong>Se anota antes de leer, no despues.</strong> Si se anotara al final y la
 * lectura fallara a mitad, habria un acceso sin registro; anotando primero, lo peor que
 * queda es un registro de un acceso que no llego a completarse, que es el error inofensivo
 * de los dos. Y las dos cosas van en la misma transaccion, asi que un fallo al anotar
 * impide la lectura.
 *
 * <p>Lo que se pide es «el frente de esta solicitud» y no una clave de archivo. Con la
 * clave en la peticion, quien la tuviera podria pedir cualquier cosa del almacen
 * reservado; aqui quien resuelve la clave es el servidor, a partir de la solicitud.
 */
public class ViewVerificationImageUseCase {

    private final SellerVerificationRepository verificaciones;
    private final RestrictedFileStore almacen;
    private final VerificationAccessLog bitacora;
    private final Clock reloj;

    public ViewVerificationImageUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            VerificationAccessLog bitacora,
            Clock reloj) {
        this.verificaciones = verificaciones;
        this.almacen = almacen;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    @Transactional
    public VerificationImageContent execute(ViewVerificationImageCommand comando) {
        SellerVerification verificacion = verificaciones
                .buscarPorId(comando.verificacion())
                .orElseThrow(() -> new VerificationNotFoundException(comando.verificacion()));

        FileKey clave = claveDe(verificacion, comando.imagen());
        if (clave == null) {
            // La solicitud existe pero ese paso no esta entregado. No es un error del
            // moderador y no dice nada de nadie: no esta.
            throw new VerificationNotFoundException(comando.verificacion());
        }

        Instant ahora = reloj.instant();
        bitacora.registrar(
                verificacion.id(), comando.moderador(), comando.imagen().acceso(), comando.motivo(), ahora);

        byte[] contenido = almacen.leer(clave);

        // El tipo se decide por los bytes, como en todo el proyecto. Lo guardado paso por
        // el normalizador, asi que es una imagen; si aun asi no se reconociera, se sirve
        // como binario antes que mentir sobre el tipo.
        String mediaType = ImageContentType.detectar(contenido)
                .map(ImageContentType::mediaType)
                .orElse("application/octet-stream");

        return new VerificationImageContent(contenido, mediaType);
    }

    private static @Nullable FileKey claveDe(SellerVerification verificacion, VerificationImage imagen) {
        IdentityDocument documento = verificacion.document();

        return switch (imagen) {
            case DOCUMENT_FRONT -> documento == null ? null : documento.frontImage();
            case DOCUMENT_BACK -> documento == null ? null : documento.backImage();
            case SELFIE -> verificacion.selfie();
        };
    }
}
