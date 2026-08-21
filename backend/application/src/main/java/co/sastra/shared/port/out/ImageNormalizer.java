package co.sastra.shared.port.out;

import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.NormalizedImage;

/**
 * Decodifica lo que llego y lo vuelve a codificar sin metadatos.
 *
 * <p>Es un puerto y no logica de dominio porque decodificar una imagen exige un
 * decodificador, y el dominio de este proyecto no importa ni una libreria
 * (CLAUDE.md). Lo que si es de dominio son las reglas sobre el resultado: el tipo
 * aceptado, el tamano y el minimo de RN-019, que viven en
 * {@link co.sastra.shared.file.ImagePolicy}.
 */
public interface ImageNormalizer {

    /**
     * @param tipo el que ya detecto la politica mirando los bytes de cabecera. Se
     *     pasa en lugar de volver a deducirlo aqui para que exista un unico sitio
     *     donde se decide de que tipo es un archivo
     * @throws NoSePudoLeerLaImagenException si no se puede decodificar, lo que
     *     incluye un archivo que empieza con la firma correcta y sigue con basura
     */
    NormalizedImage normalizar(byte[] contenido, ImageContentType tipo);

    /**
     * El contenido no es una imagen que se pueda leer.
     *
     * <p>No lleva dentro ni el nombre ni los bytes del archivo: es entrada de quien
     * subio, y lo que entra en un registro deja de estar bajo control
     * (`docs/operacion/datos-personales.md`).
     */
    class NoSePudoLeerLaImagenException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoSePudoLeerLaImagenException(Throwable causa) {
            super("No se pudo decodificar el contenido como imagen", causa);
        }
    }
}
