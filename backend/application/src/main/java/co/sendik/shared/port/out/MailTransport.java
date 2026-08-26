package co.sendik.shared.port.out;

/**
 * Manda un correo. Solo eso: a quien, con que asunto y con que cuerpo.
 *
 * <p><strong>Es de {@code shared} porque el correo no es de ningun contexto.</strong>
 * Escriben {@code identity} cuando alguien se registra y {@code catalog} cuando un
 * moderador decide; manana lo haran {@code order} y {@code payment}. Sin este puerto, el
 * segundo contexto que necesitara mandar un correo tenia que pasar por el puerto del
 * primero, y eso es exactamente lo que
 * {@code docs/arquitectura/vision-tecnica.md} prohibe entre contextos (ADR-0023).
 *
 * <p><strong>Qué dice cada correo no se decide aqui.</strong> Este puerto no sabe de
 * verificaciones ni de publicaciones: recibe texto ya armado. El vocabulario de cada
 * contexto se queda en su contexto, que es la diferencia con haberle agregado metodos al
 * {@code MailSender} de identidad.
 *
 * <p>El destinatario viaja como cadena y no como objeto de valor: {@code Email} es del
 * modelo de {@code identity}, y un puerto de {@code shared} que lo nombrara volveria a
 * atar los contextos por otro sitio. Quien llama ya tiene la direccion validada.
 *
 * <p>No lanza si el proveedor falla: el envio no puede tumbar la operacion que lo
 * provoco. El adaptador registra el fallo y sigue, igual que hacia antes.
 */
public interface MailTransport {

    void enviar(String destinatario, String asunto, String cuerpoHtml);
}
