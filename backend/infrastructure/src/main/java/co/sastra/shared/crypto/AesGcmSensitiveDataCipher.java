package co.sastra.shared.crypto;

import co.sastra.shared.port.out.SensitiveDataCipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifrado autenticado AES-256-GCM y HMAC-SHA256 de busqueda, con biblioteca
 * estandar (ADR-0020).
 *
 * <p>Sin dependencia nueva: el JDK trae las dos cosas. Es la mitad del motivo de
 * haber elegido cifrar en la aplicacion en lugar de en la base.
 *
 * <p><strong>Formato del texto cifrado:</strong> nonce de 12 bytes y a continuacion
 * el texto cifrado con su etiqueta de 16, todo en base64. El nonce va delante y en
 * claro, que es lo normal: no es un secreto, es un valor que no se repite. Lo que
 * no puede pasar nunca es reutilizarlo con la misma clave —eso rompe GCM del todo,
 * no solo un poco— y por eso sale de {@link SecureRandom} en cada llamada y no de un
 * contador.
 *
 * <p>La version de clave no viaja dentro del texto: va en su propia columna
 * (ADR-0020). Meterla dentro obligaria a descifrar para saber con que descifrar.
 */
@Component
public class AesGcmSensitiveDataCipher implements SensitiveDataCipher {

    private static final String TRANSFORMACION = "AES/GCM/NoPadding";

    private static final String ALGORITMO_HMAC = "HmacSHA256";

    /** 96 bits, el tamano que la especificacion de GCM recomienda. */
    private static final int BYTES_DE_NONCE = 12;

    /** 128 bits de etiqueta de autenticacion. */
    private static final int BITS_DE_ETIQUETA = 128;

    private static final int BYTES_DE_CLAVE = 32;

    private static final SecureRandom AZAR = new SecureRandom();

    private final Map<Integer, SecretKeySpec> clavesDeDatos;

    private final int versionVigente;

    private final SecretKeySpec claveDeBusqueda;

    public AesGcmSensitiveDataCipher(CryptoProperties propiedades) {
        this.clavesDeDatos = leerClaves(propiedades.dataKeys());
        this.versionVigente = propiedades.currentVersion();
        this.claveDeBusqueda =
                new SecretKeySpec(decodificar(propiedades.lookupKey(), "sastra.crypto.lookup-key"), ALGORITMO_HMAC);

        if (!clavesDeDatos.containsKey(versionVigente)) {
            throw new IllegalStateException("sastra.crypto.current-version apunta a la version " + versionVigente
                    + ", que no esta en sastra.crypto.data-keys");
        }
        exigirClaveDeBusquedaDistinta(propiedades);
    }

    @Override
    public EncryptedValue cifrar(String claro) {
        byte[] nonce = new byte[BYTES_DE_NONCE];
        AZAR.nextBytes(nonce);

        try {
            Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
            cifrador.init(
                    Cipher.ENCRYPT_MODE,
                    clavesDeDatos.get(versionVigente),
                    new GCMParameterSpec(BITS_DE_ETIQUETA, nonce));

            byte[] cifrado = cifrador.doFinal(claro.getBytes(StandardCharsets.UTF_8));

            byte[] todo = new byte[nonce.length + cifrado.length];
            System.arraycopy(nonce, 0, todo, 0, nonce.length);
            System.arraycopy(cifrado, 0, todo, nonce.length, cifrado.length);

            return new EncryptedValue(Base64.getEncoder().encodeToString(todo), versionVigente);
        } catch (GeneralSecurityException fallo) {
            // Sin el valor en el mensaje: acabaria en un registro
            // (docs/operacion/datos-personales.md).
            throw new IllegalStateException("No se pudo cifrar el dato sensible", fallo);
        }
    }

    @Override
    public String descifrar(EncryptedValue cifrado) {
        SecretKeySpec clave = clavesDeDatos.get(cifrado.keyVersion());
        if (clave == null) {
            // Pasa si se retiro una clave que todavia tenia filas. Decirlo asi es lo
            // unico que permite arreglarlo: el dato no se ha perdido, falta la clave.
            throw new IllegalStateException(
                    "No hay clave para la version " + cifrado.keyVersion() + " en sastra.crypto.data-keys");
        }

        byte[] todo = Base64.getDecoder().decode(cifrado.cipher());
        if (todo.length <= BYTES_DE_NONCE) {
            throw new IllegalStateException("El texto cifrado no lleva nonce ni contenido");
        }

        byte[] nonce = new byte[BYTES_DE_NONCE];
        System.arraycopy(todo, 0, nonce, 0, BYTES_DE_NONCE);

        byte[] contenido = new byte[todo.length - BYTES_DE_NONCE];
        System.arraycopy(todo, BYTES_DE_NONCE, contenido, 0, contenido.length);

        try {
            Cipher descifrador = Cipher.getInstance(TRANSFORMACION);
            descifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_DE_ETIQUETA, nonce));

            return new String(descifrador.doFinal(contenido), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException fallo) {
            // Aqui cae la fila alterada a mano: la etiqueta no cuadra y GCM se niega.
            // Es la diferencia entre un cifrado autenticado y uno que devuelve basura.
            throw new IllegalStateException("El dato cifrado no se pudo descifrar o fue alterado", fallo);
        }
    }

    @Override
    public byte[] huella(String claro) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            mac.init(claveDeBusqueda);
            return mac.doFinal(claro.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException fallo) {
            throw new IllegalStateException("No se pudo calcular la huella del dato sensible", fallo);
        }
    }

    private static Map<Integer, SecretKeySpec> leerClaves(Map<Integer, String> configuradas) {
        Map<Integer, SecretKeySpec> claves = new HashMap<>();
        configuradas.forEach((version, base64) -> claves.put(
                version, new SecretKeySpec(decodificar(base64, "sastra.crypto.data-keys[" + version + "]"), "AES")));
        return Map.copyOf(claves);
    }

    /**
     * Exige 32 bytes: AES-256. Una clave mas corta funcionaria —AES admite 128 y 192
     * bits— y nadie se enteraria de que la proteccion es menor de la que dice la ADR.
     */
    private static byte[] decodificar(String base64, String nombre) {
        byte[] clave;
        try {
            clave = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException fallo) {
            throw new IllegalStateException(nombre + " no es base64 valido", fallo);
        }

        if (clave.length != BYTES_DE_CLAVE) {
            throw new IllegalStateException(
                    nombre + " tiene " + clave.length + " bytes y necesita " + BYTES_DE_CLAVE + " (AES-256)");
        }
        return clave;
    }

    /**
     * La clave de busqueda no puede ser ninguna de las de cifrado.
     *
     * <p>Se comprueba al arrancar porque es el error que no da sintomas: todo
     * funcionaria igual, y la separacion que ADR-0020 puso para que filtrar una clave
     * no diera dos capacidades a la vez no existiria. Copiar y pegar la misma cadena
     * en dos variables de entorno es exactamente lo que pasa un martes.
     */
    private void exigirClaveDeBusquedaDistinta(CryptoProperties propiedades) {
        String busqueda = propiedades.lookupKey().trim();

        propiedades.dataKeys().forEach((version, base64) -> {
            if (base64.trim().equals(busqueda)) {
                throw new IllegalStateException("sastra.crypto.lookup-key es igual a la clave de datos de la version "
                        + version + ", y ADR-0020 exige que sean distintas");
            }
        });
    }
}
