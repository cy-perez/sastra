package co.sastra.identity.service;

import co.sastra.identity.exception.PasswordTooShortException;
import co.sastra.identity.model.RawPassword;
import java.util.Objects;

/**
 * RN-005. Diez caracteres como minimo y ningun simbolo obligatorio: la longitud
 * protege mas que la complejidad artificial, que solo consigue que la gente
 * escriba {@code Password1!} y lo reutilice en todas partes.
 *
 * <p>La otra mitad de la regla, que la contrasena no aparezca en una filtracion
 * conocida, no esta aqui: exige salir a la red y eso es un puerto de
 * {@code application} (ADR-0013). La separacion importa porque este minimo se
 * comprueba <strong>siempre</strong>, tambien cuando el servicio externo no
 * responde, y ese es justo el caso que el fallo abierto deja pasar.
 */
public final class PasswordPolicy {

    public static final int LARGO_MINIMO = 10;

    private PasswordPolicy() {}

    /**
     * @throws PasswordTooShortException si no alcanza el largo minimo
     */
    public static void verificar(RawPassword contrasena) {
        Objects.requireNonNull(contrasena, "La contrasena es obligatoria");
        if (!cumpleElLargoMinimo(contrasena)) {
            throw new PasswordTooShortException();
        }
    }

    /** Cuenta caracteres, no bytes: una tilde vale lo mismo que una letra. */
    public static boolean cumpleElLargoMinimo(RawPassword contrasena) {
        return contrasena != null && contrasena.largo() >= LARGO_MINIMO;
    }
}
