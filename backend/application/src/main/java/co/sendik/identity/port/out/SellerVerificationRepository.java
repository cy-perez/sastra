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
     * <p>Por desplazamiento y no por cursor: el contrato reserva el cursor para el
     * catalogo publico y admite pagina y tamano en los listados administrativos acotados
     * (contrato-api.md). La pagina la calcula quien llama; aqui se recibe ya resuelta.
     *
     * <p><strong>Salto y no numero de pagina, y no es cosmetico.</strong> Con
     * {@code (pagina, tamano)} el desplazamiento se deriva del mismo argumento que el
     * limite, asi que quien pida una fila de mas para saber si hay pagina siguiente mueve
     * tambien el arranque: {@code (1, 21)} salta 21 filas en vez de 20 y la fila 21 no
     * sale en ninguna pagina. Se pierde una por pagina, en silencio. Separarlos es lo que
     * permite pedir mas de las que caben sin mover de sitio la ventana.
     *
     * <p><strong>Quien llama pide una de mas</strong> para saber si hay pagina siguiente,
     * igual que en {@code ListingRepository.publicadas}. Ver
     * {@code ListPendingVerificationsUseCase}.
     *
     * <p>Aqui hubo un tope sin desplazamiento, con el argumento de que esta lista la
     * trabaja una persona hasta vaciarla. Eso describe bien la carga y mal el alcance: la
     * bandeja drena sola -decidir saca la fila- pero sin desplazamiento no habia forma de
     * llegar a una solicitud concreta que no estuviera entre las primeras.
     *
     * <p>Devuelve el agregado entero, asi que descifra el numero de cada fila. Es
     * trabajo de mas para una lista, y se acepta mientras el volumen sea el que es:
     * partir el tipo en dos —uno para listar y otro para decidir— es lo que se hara
     * cuando la bandeja tenga cientos de filas y no antes.
     *
     * @param salto cuantas filas se saltan antes de empezar. Como {@code long}: el
     *     producto de pagina por tamano desborda en {@code int} mucho antes de que la
     *     tabla llegue ahi, y desbordar da un salto negativo, que PostgreSQL responde con
     *     un error y no con una pagina vacia
     * @param cuantas cuantas traer. Quien llama pide una de mas de las que va a mostrar
     */
    List<SellerVerification> pendientesDeRevision(long salto, int cuantas);

    /**
     * Si queda al menos una pendiente a partir de esa posicion. La respuesta a «¿hay
     * pagina siguiente?».
     *
     * <p><strong>Existe para no tener que traer a nadie para contestarlo.</strong> Antes
     * se pedia una fila de mas de las que caben y se usaba su presencia como señal. Eso
     * funciona, pero {@code pendientesDeRevision} devuelve el agregado entero, asi que
     * descifraba la cedula y la cuenta bancaria de una persona con el unico proposito de
     * comprobar que su fila existia, y las tiraba. Procesar un dato sensible sin
     * finalidad es justo lo que la minimizacion de la Ley 1581 desaconseja
     * (docs/operacion/datos-personales.md).
     *
     * <p>Tampoco es contar: no dice cuantas quedan -que exigiria recorrerlas todas- sino
     * si queda alguna, y para eso basta con llegar hasta la primera.
     *
     * @param salto la posicion a partir de la cual se pregunta. Para saber si hay pagina
     *     siguiente es el final de la actual
     */
    boolean hayPendientesDesde(long salto);
}
