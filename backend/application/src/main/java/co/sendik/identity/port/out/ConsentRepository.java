package co.sendik.identity.port.out;

import co.sendik.identity.model.Consent;
import co.sendik.identity.model.UserId;
import java.util.List;

/** Puerto de salida hacia la evidencia de consentimiento (Ley 1581 de 2012). */
public interface ConsentRepository {

    /**
     * Guarda los consentimientos de un registro. Van juntos porque o se aceptan
     * los dos documentos o no hay cuenta: una mitad guardada no prueba nada.
     */
    void guardarTodos(List<Consent> consentimientos);

    /**
     * Los consentimientos de una persona, del mas reciente al mas antiguo.
     *
     * <p>Existe para el criterio 22: la evidencia de a que documentos dijo que si,
     * con su version, es de las pocas cosas que una persona puede querer comprobar
     * anos despues.
     */
    List<Consent> listarDe(UserId usuario);
}
