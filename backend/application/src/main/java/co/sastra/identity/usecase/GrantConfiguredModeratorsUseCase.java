package co.sastra.identity.usecase;

import co.sastra.identity.model.Email;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.User;
import co.sastra.identity.port.out.UserRepository;
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

    /**
     * El resumen de lo que paso, para que quien arranca pueda registrarlo.
     *
     * @param otorgados correos que ahora tienen el rol, tuvieran o no antes
     * @param sinCuenta correos configurados que no corresponden a ninguna cuenta
     * @param invalidos entradas que ni siquiera son un correo
     */
    public record Resultado(List<String> otorgados, List<String> sinCuenta, List<String> invalidos) {

        public Resultado {
            otorgados = List.copyOf(otorgados);
            sinCuenta = List.copyOf(sinCuenta);
            invalidos = List.copyOf(invalidos);
        }

        public boolean huboAlgoQueDecir() {
            return !otorgados.isEmpty() || !sinCuenta.isEmpty() || !invalidos.isEmpty();
        }
    }

    @Transactional
    public Resultado execute(List<String> correos) {
        List<String> otorgados = new ArrayList<>();
        List<String> sinCuenta = new ArrayList<>();
        List<String> invalidos = new ArrayList<>();
        Instant ahora = reloj.instant();

        for (String entrada : correos) {
            Optional<Email> correo = interpretar(entrada);

            if (correo.isEmpty()) {
                invalidos.add(entrada);
                continue;
            }

            Optional<User> cuenta = usuarios.buscarPorCorreo(correo.get());

            if (cuenta.isEmpty()) {
                sinCuenta.add(correo.get().value());
                continue;
            }

            usuarios.otorgarRol(cuenta.get().id(), Role.MODERATOR, ahora);
            otorgados.add(correo.get().value());
        }

        return new Resultado(otorgados, sinCuenta, invalidos);
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
