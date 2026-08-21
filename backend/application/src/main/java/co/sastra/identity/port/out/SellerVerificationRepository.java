package co.sastra.identity.port.out;

import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.UserId;
import java.util.Optional;

/**
 * Puerto de salida hacia el almacen de verificaciones de vendedor (HU-002).
 *
 * <p>Un repositorio por agregado: la verificacion es uno solo, aunque su fila tenga
 * columnas de tres cosas distintas.
 *
 * <p>Aqui no aparece el cifrado. Quien implemente esto lo resuelve con
 * {@code SensitiveDataCipher} y el caso de uso ni se entera: pide guardar un
 * agregado y recibe un agregado (ADR-0020).
 */
public interface SellerVerificationRepository {

    /**
     * Crea o actualiza. Una verificacion por cuenta, asi que la clave natural es la
     * cuenta y no hace falta distinguir las dos operaciones desde fuera.
     *
     * <p>Los reintentos de RN-014 no crean filas: mueven el estado y suben el
     * contador, porque lo que la persona ve es una sola solicitud que va y viene.
     */
    void guardar(SellerVerification verificacion);

    Optional<SellerVerification> buscarPorUsuario(UserId usuario);

    /**
     * Criterio 5 de HU-002 y RN-010: si ese documento ya esta verificado en otra
     * cuenta.
     *
     * <p>Recibe el numero en claro y no la huella. La huella es un detalle de como se
     * compara —del adaptador, con la clave que solo el tiene— y sacarla a la firma
     * obligaria al caso de uso a conocer ADR-0020 para hacer una pregunta de negocio.
     *
     * <p>Excluye una cuenta a proposito: quien corrige su propia solicitud tiene su
     * documento en su propia fila, y sin excluirla chocaria consigo mismo.
     */
    boolean existeOtraVerificadaConDocumento(String numeroDeDocumento, UserId exceptoEstaCuenta);
}
