package co.sastra.identity.port.out;

import co.sastra.identity.model.BankCode;
import java.util.List;

/**
 * El catalogo de entidades donde un vendedor puede recibir su dinero (HU-002).
 *
 * <p>Es un puerto y no una enumeracion del dominio porque la lista es datos: las
 * entidades se fusionan, se renombran y aparecen nuevas, y agregar un banco no puede
 * exigir desplegar codigo.
 *
 * <p>Solo pregunta si existe. La validacion de que la entidad admita un tipo de
 * cuenta concreto —que una billetera no tiene cuenta de ahorros— no esta aqui a
 * proposito: nadie ha decidido esa regla y la clasificacion entre banco y billetera
 * todavia esta por confirmar. Sin la regla escrita, imponerla rechazaria
 * combinaciones validas.
 */
public interface FinancialInstitutions {

    /** Si el codigo corresponde a una entidad activa del catalogo. */
    boolean estaActiva(BankCode entidad);

    /**
     * Las entidades activas, para que el formulario las ofrezca.
     *
     * <p>Sin filtro y sin paginacion: son veintiocho y la pantalla las pinta todas en un
     * desplegable. El dia que sean cientos, el problema sera otro y la firma tambien.
     */
    List<FinancialInstitution> activas();

    /**
     * Una entidad del catalogo tal como la ofrece el formulario.
     *
     * @param code lo que se guarda en la fila del vendedor
     * @param name el nombre visible. No se traduce: es un nombre propio
     * @param wallet si es billetera o deposito electronico en lugar de banco. La pantalla
     *     lo necesita para saber que tipos de cuenta ofrecer
     */
    record FinancialInstitution(String code, String name, boolean wallet) {}
}
