package co.sastra.identity.port.out;

/**
 * Puerto de salida hacia la generacion de tokens de un solo uso.
 *
 * <p>Genera el valor y su hash de una vez, para que el caso de uso nunca tenga
 * que decidir como se hashea ni pueda equivocarse guardando el valor en claro.
 */
public interface TokenGenerator {

    /**
     * @param valorEnClaro el que viaja dentro del enlace del correo. Existe una
     *     sola vez y nunca se guarda.
     * @param hash lo unico que llega a la base de datos.
     */
    record GeneratedToken(String valorEnClaro, String hash) {}

    GeneratedToken generar();

    /** Hashea un token recibido para poder buscarlo. Mismo algoritmo que {@link #generar()}. */
    String hashearRecibido(String valorEnClaro);
}
