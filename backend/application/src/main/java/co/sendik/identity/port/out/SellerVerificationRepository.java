package co.sendik.identity.port.out;

import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import java.util.List;
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
     * Por su identificador, que es como llega desde la bandeja del moderador.
     *
     * <p>El moderador trabaja sobre una lista de solicitudes y no sobre cuentas: pedirle
     * el identificador de la cuenta seria pedirle un dato que su pantalla no tiene por
     * que mostrar.
     */
    Optional<SellerVerification> buscarPorId(SellerVerificationId verificacion);

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

    /**
     * Las que esperan revision, las mas viejas primero. La bandeja del moderador.
     *
     * <p>Con tope y sin paginacion por cursor. El contrato de la API la pide para los
     * listados de catalogo (contrato-api.md) y aqui no aplica: esta lista la trabaja una
     * persona hasta vaciarla, y si llega a necesitar paginacion el problema no es la
     * consulta, es que nadie esta revisando.
     *
     * <p>Devuelve el agregado entero, asi que descifra el numero de cada fila. Es
     * trabajo de mas para una lista, y se acepta mientras el volumen sea el que es:
     * partir el tipo en dos —uno para listar y otro para decidir— es lo que se hara
     * cuando la bandeja tenga cientos de filas y no antes.
     */
    List<SellerVerification> pendientesDeRevision(int limite);
}
