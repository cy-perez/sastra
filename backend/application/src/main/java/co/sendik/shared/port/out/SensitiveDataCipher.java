package co.sendik.shared.port.out;

import co.sendik.shared.crypto.EncryptedValue;

/**
 * Cifra y descifra los datos sensibles que van en columnas: el numero de documento
 * y el de la cuenta bancaria (RN-046, ADR-0020).
 *
 * <p>Es un puerto y no una clase de utilidad porque las claves son configuracion, y
 * la configuracion vive en {@code infrastructure}. El caso de uso pide «cifra esto»
 * sin saber con que algoritmo ni de donde sale la clave.
 *
 * <p>No cubre los archivos. La cedula y la selfie son imagenes y van al almacen
 * reservado, que las cifra en reposo por su cuenta (ADR-0018).
 */
public interface SensitiveDataCipher {

    /**
     * Cifra con la clave vigente.
     *
     * <p>Dos llamadas con el mismo valor devuelven textos cifrados distintos, y eso
     * es obligatorio, no un detalle: un cifrado que produjera siempre lo mismo
     * revelaria que dos filas comparten valor sin necesidad de descifrar ninguna.
     * Es tambien el motivo de que exista {@link #huella(String)}.
     */
    EncryptedValue cifrar(String claro);

    /**
     * Descifra con la clave que la version indica.
     *
     * <p>Falla si el texto se modifico: el cifrado es autenticado, asi que una fila
     * alterada a mano no descifra en lugar de descifrar en basura.
     */
    String descifrar(EncryptedValue cifrado);

    /**
     * La huella con la que se compara sin descifrar: HMAC-SHA256 con una clave
     * <strong>distinta</strong> de la de cifrado.
     *
     * <p>Es determinista a proposito, que es justo lo que el cifrado no puede ser, y
     * es lo que hace posible el criterio 5 de HU-002: preguntar si un documento ya
     * esta verificado sin poder leer ninguno.
     *
     * <p>La clave separada no es ceremonia. Una cedula colombiana es un numero de
     * ocho a diez digitos, asi que quien tuviera esta clave podria recorrer el
     * espacio entero y confirmar de quien es cada huella. Si fuera la misma clave del
     * cifrado, filtrar una daria las dos capacidades a la vez.
     */
    byte[] huella(String claro);
}
