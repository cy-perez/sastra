package co.sendik.identity.usecase;

import co.sendik.identity.dto.GrantedModeratorsResult;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Otorga el rol de moderador a las cuentas que la configuracion nombra.
 *
 * <p><strong>Por que existe.</strong> Hasta ahora el rol solo se concedia con un
 * {@code INSERT} escrito a mano contra la base, y HU-002 lo documentaba asi. Eso deja
 * dos problemas: no hay forma de que exista el primer moderador en un entorno nuevo sin
 * que alguien entre a la base de produccion, y las pruebas de extremo a extremo, que
 * crean sus cuentas por la interfaz, no pueden fabricar uno.
 *
 * <p><strong>Lo que no hace, y es la mitad importante.</strong> No crea cuentas: si el
 * correo no tiene una, no pasa nada y se dice. No abre sesiones ni salta ninguna
 * comprobacion de autenticacion. No concede {@code ADMIN}. Y sobre todo <strong>no
 * revoca</strong>: quitar un correo de la lista no le quita el rol a nadie, porque un
 * despliegue con la variable mal puesta degradaria a todos los moderadores en silencio,
 * y quedarse sin quien apruebe es peor que un rol de mas. Retirar el rol sigue siendo
 * una operacion deliberada.
 *
 * <p><strong>Solo a cuentas con el correo verificado.</strong> Registrarse demuestra que
 * alguien sabe escribir una direccion; verificarla demuestra que controla el buzon, y el
 * rol da acceso a las cedulas de todos los vendedores pendientes. A quien este configurado
 * y todavia no haya verificado, el rol le llega cuando lo haga.
 *
 * <p>Es idempotente: {@code otorgarRol} inserta con {@code ON CONFLICT DO NOTHING}, asi
 * que arrancar cien veces deja lo mismo que arrancar una.
 *
 * <p>Con la lista vacia —que es lo que hay por omision— no hace absolutamente nada.
 */
public class GrantConfiguredModeratorsUseCase {

    private final UserRepository usuarios;
    private final Clock reloj;

    public GrantConfiguredModeratorsUseCase(UserRepository usuarios, Clock reloj) {
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Transactional
    public GrantedModeratorsResult execute(List<String> correos) {
        List<String> otorgados = new ArrayList<>();
        List<String> sinCuenta = new ArrayList<>();
        List<String> sinVerificar = new ArrayList<>();
        int invalidos = 0;
        Instant ahora = reloj.instant();

        for (String entrada : correos) {
            Optional<Email> correo = interpretar(entrada);

            if (correo.isEmpty()) {
                invalidos++;
                continue;
            }

            Optional<User> cuenta = usuarios.buscarPorCorreo(correo.get());

            if (cuenta.isEmpty()) {
                sinCuenta.add(correo.get().value());
                continue;
            }

            // Sin correo verificado no hay rol. Quien no ha abierto su enlace no ha
            // demostrado que controla ese buzon, y el rol da acceso a las cedulas de
            // todos los vendedores pendientes. Cuando lo verifique, lo recibe: de eso se
            // encarga VerifyEmailUseCase.
            if (!cuenta.get().tieneElCorreoVerificado()) {
                sinVerificar.add(correo.get().value());
                continue;
            }

            usuarios.otorgarRol(cuenta.get().id(), Role.MODERATOR, ahora);
            otorgados.add(correo.get().value());
        }

        return new GrantedModeratorsResult(otorgados, sinCuenta, sinVerificar, invalidos);
    }

    /**
     * Un correo mal escrito no tumba el arranque.
     *
     * <p>Es la diferencia con las claves de cifrado, que si lo tumban: sin ellas nada
     * funciona, mientras que aqui lo que pasa es que una persona se queda sin un rol.
     * Dejar el servicio caido por una coma de mas seria desproporcionado. Se registra y
     * se sigue, y por eso el resumen distingue este caso del correo sin cuenta: son dos
     * errores distintos y se corrigen en sitios distintos.
     */
    private static Optional<Email> interpretar(String entrada) {
        try {
            return Optional.of(new Email(entrada));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
