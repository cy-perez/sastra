package co.sendik.identity.port.out;

import co.sendik.identity.model.Email;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.User;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Puerto de salida hacia el correo transaccional (ADR-0012).
 *
 * <p>Recibe el token en claro y no el enlace ya montado: la direccion publica del
 * sitio es configuracion, y la configuracion vive en infraestructura. Asi este
 * puerto no sabe nada de URL.
 *
 * <p>Ningun metodo lanza si el envio falla. Un correo que no sale no debe
 * impedir crear la cuenta: la persona siempre puede pedir el reenvio, y perder el
 * registro entero por una caida del proveedor es peor.
 */
public interface MailSender {

    /** Criterio 1: el enlace de verificacion que activa la cuenta. */
    void enviarVerificacionDeCorreo(User destinatario, String tokenEnClaro);

    /**
     * Criterio 2: alguien intento registrarse con un correo que ya tiene cuenta.
     *
     * <p>Este aviso es lo que hace honesta la respuesta identica del registro: al
     * atacante no se le dice nada, y al titular legitimo se le avisa de que su
     * correo se esta usando.
     */
    void enviarAvisoDeRegistroConCorreoExistente(User titular);

    /**
     * Criterio 12: la cuenta se acaba de bloquear por intentos fallidos (RN-006).
     *
     * <p>Se envia una sola vez por bloqueo, no en cada intento posterior: quien
     * este probando contrasenas convertiria el aviso en una forma de inundar el
     * buzon del titular.
     *
     * @param desbloqueoEn cuando vuelve a poder entrar
     */
    void enviarAvisoDeCuentaBloqueada(User titular, Instant desbloqueoEn);

    /**
     * Criterio 15: llego un token de refresco que ya se habia usado, asi que se
     * revoco la sesion completa.
     *
     * <p>Es el unico aviso de esta lista que describe algo que el titular no puede
     * haber provocado sin darse cuenta: o le copiaron la cookie, o alguien esta
     * reproduciendo peticiones viejas. Por eso se avisa aunque ya se haya cortado
     * el acceso.
     */
    void enviarAvisoDeSesionRevocadaPorSeguridad(User titular);

    /**
     * Criterio 18: el enlace para poner una contrasena nueva.
     *
     * <p>Recibe el token en claro, como la verificacion: el enlace lo monta el
     * adaptador, que es quien conoce la direccion publica del sitio.
     */
    void enviarRestablecimientoDeContrasena(User destinatario, String tokenEnClaro);

    /**
     * Criterio 20: la contrasena acaba de cambiar y se cerraron las sesiones.
     *
     * <p>Este aviso es lo que convierte un cambio no autorizado en algo que el
     * titular puede detectar: si no fue quien recibe el correo, alguien tiene su
     * buzon y hay que actuar. Se manda aunque el cambio sea legitimo, que es la
     * unica forma de que sirva cuando no lo sea.
     */
    void enviarAvisoDeContrasenaCambiada(User titular);

    /**
     * Criterio 23: la cuenta acaba de cerrarse.
     *
     * <p>Se manda <strong>antes</strong> de anonimizar, porque despues ya no hay
     * direccion a la que escribir. Es el ultimo mensaje que recibe esa cuenta y
     * sirve para lo mismo que el aviso de contrasena cambiada: si no fue la
     * persona, alguien tiene su buzon y todavia esta a tiempo de reaccionar.
     */
    void enviarAvisoDeCuentaCerrada(User titular);

    /**
     * Criterio 21: el enlace que confirma el correo nuevo.
     *
     * <p>Va a la direccion nueva y no a la actual, y es el punto entero del
     * criterio: solo quien pueda abrir ese buzon completa el cambio.
     */
    void enviarConfirmacionDeCorreoNuevo(User titular, Email destino, String tokenEnClaro);

    /**
     * Criterio 21: alguien intento mudar su cuenta a este correo, que ya tiene una.
     *
     * <p>Es lo que hace honesta la respuesta identica del cambio: al que lo pidio
     * no se le dice nada, y al titular legitimo se le avisa de que su direccion se
     * esta usando. El mismo trato que el aviso de registro con correo existente.
     */
    void enviarAvisoDeIntentoDeCambioAEsteCorreo(User titular);

    // --- Verificacion de vendedor. HU-002 criterio 10 ------------------------
    //
    // Los cuatro cambios de estado que otra persona provoca o que dejan a alguien
    // esperando. **No hay aviso de empezar el proceso**, aunque NOT_STARTED a
    // IN_PROGRESS tambien sea un cambio de estado: lo provoca la propia persona
    // pulsando un boton y lo ve en pantalla en el momento. Un correo ahi no informa de
    // nada y ensena a ignorar los nuestros.

    /**
     * Criterio 6 y 10: la solicitud quedo enviada y espera revision.
     *
     * <p>No lleva el plazo como parametro: lo lee el adaptador de su configuracion, que
     * es donde vive. Pasarlo por aqui obligaria al caso de uso a conocer una cifra que
     * solo aparece en un texto.
     */
    void enviarAvisoDeVerificacionRecibida(User titular);

    /** Criterio 8 y 10: aprobada. Ya es vendedor verificado. */
    void enviarAvisoDeVerificacionAprobada(User titular);

    /**
     * Criterio 7 y 10: rechazada, con el motivo de la lista cerrada.
     *
     * @param nota lo que escribio quien revisa. Viaja a la persona rechazada y nunca
     *     lleva informacion judicial ni datos de un tercero
     * @param intentosRestantes lo que queda de RN-014. En cero, el correo dice que hay
     *     que escribir en lugar de invitar a reintentar
     */
    void enviarAvisoDeVerificacionRechazada(
            User titular, RejectionReason motivo, @Nullable String nota, int intentosRestantes);

    /**
     * RN-013 y criterio 10: se revoco el sello de quien ya lo tenia.
     *
     * <p>Aviso propio y no el de rechazo, por lo mismo que son dos estados distintos:
     * a quien nunca paso la revision se le dice que corrija; a quien la paso y perdio el
     * sello hay que decirle que sus publicaciones siguen visibles y que no puede crear
     * nuevas, que es otra conversacion.
     */
    void enviarAvisoDeVerificacionRevocada(User titular, RejectionReason motivo, @Nullable String nota);

    /**
     * Criterio 21: el correo de la cuenta acaba de cambiar.
     *
     * <p>Va a la direccion <strong>anterior</strong>. Es lo que evita el peor caso:
     * quien robe una sesion cambia el correo y saca al titular de su cuenta en
     * silencio. Al buzon nuevo no hace falta avisarle, que acaba de abrir el
     * enlace.
     */
    void enviarAvisoDeCorreoCambiado(User titular, Email anterior);

    // --- Decisiones sobre una publicacion. HU-007 criterio 26 ----------------
    //
    // Los tres avisos que otra persona provoca sobre algo que el vendedor
    // publico. No hay aviso de enviar a revision: lo hace el propio vendedor y lo
    // ve en pantalla, igual que en la verificacion.
    //
    // **Los parametros son cadenas y no tipos de catalog, y eso es a proposito.**
    // Este puerto es de identity: si su firma nombrara ListingRejectionReason o
    // Listing, el contexto de identidad quedaria atado al modelo del catalogo y
    // cualquier cambio alla romperia esto. El motivo llega ya traducido al idioma
    // del destinatario desde MailListingNotifier, que es quien conoce la
    // enumeracion y quien tiene delante a la persona.

    /** Criterio 21 y 26: aprobada y ya visible. */
    void enviarAvisoDePublicacionAprobada(User titular, String tituloDeLaPublicacion);

    /** Criterio 22 y 26: rechazada, con el motivo y la nota que escribio el moderador. */
    void enviarAvisoDePublicacionRechazada(
            User titular, String tituloDeLaPublicacion, String motivo, @Nullable String nota);

    /** Criterio 31: el moderador bajo algo que ya era visible (RN-024). */
    void enviarAvisoDePublicacionRetirada(
            User titular, String tituloDeLaPublicacion, String motivo, @Nullable String nota);
}
