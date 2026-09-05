package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ReadModerationHistoryQuery;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Que le paso a una publicacion propia y por que. HU-013.
 *
 * <p>Quien vende entiende que paso con lo suyo sin tener que buscar el correo que se lo
 * aviso, y sin que nadie le diga quien lo decidio (RN-074).
 *
 * <p><strong>Pregunta primero por la publicacion aunque el rastro viva en otro sitio.</strong>
 * Podria pedirle la bitacora al puerto directamente y devolver lo que saliera, que sobre una
 * publicacion ajena seria una lista con eventos de otra persona dentro. La comprobacion de
 * dueno la hace {@code buscarDelDueno}, que devuelve vacio tanto si no existe como si no es
 * suya: con las dos indistinguibles, el borde no tiene forma de responder 403 ni aunque
 * quisiera, y sale el 404 del criterio 7. Un 403 confirmaria que esa publicacion existe.
 *
 * <p><strong>No compone nada con {@code submitted_at}.</strong> La historia lo proponia
 * porque el envio no estaba en la bitacora; al decidir anotarlo como evento, el rastro sale
 * entero de una sola fuente y en un solo orden. Componer dos fuentes habria dejado el
 * criterio 4 a medias de todos modos: esa columna guarda un unico envio, el ultimo, y una
 * publicacion que fue y volvio tiene varios.
 */
public class ReadModerationHistoryUseCase {

    private final ListingRepository publicaciones;
    private final ModerationLog bitacora;

    public ReadModerationHistoryUseCase(ListingRepository publicaciones, ModerationLog bitacora) {
        this.publicaciones = publicaciones;
        this.bitacora = bitacora;
    }

    /*
     * Sin readOnly = true, aunque esto solo lea, por lo mismo que ReadListingUseCase: el
     * modulo presentation no declara spring-tx y al leer la anotacion avisa de que no puede
     * resolver el atributo, lo que con -Xlint:all -Werror rompe la compilacion.
     */
    @Transactional
    public List<ModerationEvent> execute(ReadModerationHistoryQuery consulta) {
        publicaciones
                .buscarDelDueno(consulta.publicacion(), consulta.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(consulta.publicacion()));

        return bitacora.historial(consulta.publicacion());
    }
}
