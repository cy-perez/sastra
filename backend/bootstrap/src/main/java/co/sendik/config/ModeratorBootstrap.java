package co.sendik.config;

import co.sendik.identity.config.ModeratorBootstrapProperties;
import co.sendik.identity.dto.GrantedModeratorsResult;
import co.sendik.identity.usecase.GrantConfiguredModeratorsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Concede el rol de moderador al arrancar, a quien diga la configuracion (HU-006).
 *
 * <p>Va en un {@code ApplicationRunner} y no en el arranque del contexto porque necesita
 * la base ya migrada: Flyway corre antes, y con el contexto a medio levantar la consulta
 * fallaria por un motivo que no tiene nada que ver.
 *
 * <p><strong>Siempre deja rastro.</strong> Otorgar autorizacion en silencio es lo que
 * convierte un mecanismo legitimo en algo indistinguible de una puerta trasera: si un dia
 * alguien pregunta por que esa cuenta modera, la respuesta tiene que estar en el registro
 * del arranque. Cuando la lista esta vacia no se dice nada, que es el caso normal y no
 * merece una linea en cada despliegue.
 *
 * <p>Los correos configurados que no tienen cuenta y las entradas que no son un correo se
 * registran como aviso y no detienen nada: lo que esta en juego es que una persona se
 * quede sin un rol, no que el servicio funcione. Se distinguen porque se corrigen en
 * sitios distintos —uno es un registro que falta, el otro una errata en la variable—.
 */
@Component
public class ModeratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeratorBootstrap.class);

    private final GrantConfiguredModeratorsUseCase caso;
    private final ModeratorBootstrapProperties propiedades;

    public ModeratorBootstrap(GrantConfiguredModeratorsUseCase caso, ModeratorBootstrapProperties propiedades) {
        this.caso = caso;
        this.propiedades = propiedades;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        if (propiedades.moderators().isEmpty()) {
            return;
        }

        GrantedModeratorsResult resultado = caso.execute(propiedades.moderators());

        if (!resultado.otorgados().isEmpty()) {
            log.info("Rol de moderador otorgado por configuracion a: {}", resultado.otorgados());
        }
        if (!resultado.sinCuenta().isEmpty()) {
            log.warn(
                    "Configurados como moderadores pero sin cuenta en Sendik, no se les otorgo nada: {}",
                    resultado.sinCuenta());
        }
        if (!resultado.sinVerificar().isEmpty()) {
            log.warn(
                    "Configurados como moderadores pero sin verificar su correo. Recibiran el rol"
                            + " cuando abran su enlace: {}",
                    resultado.sinVerificar());
        }
        if (resultado.invalidos() > 0) {
            // El numero y no los valores: si alguien pega en esta variable el contenido de
            // otra —una clave, la direccion de un proveedor— esa otra acabaria impresa
            // entera en el registro de arranque.
            log.warn(
                    "{} entrada(s) de SECURITY_BOOTSTRAP_MODERATORS no son un correo y se ignoraron",
                    resultado.invalidos());
        }
    }
}
