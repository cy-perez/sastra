package co.sendik.catalog.port.out;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.SellerId;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * El rastro de cada paso por moderacion. RN-045: ninguna transicion se pierde.
 *
 * <p>Separado del repositorio a proposito. Guardar la publicacion y anotar lo que le paso
 * son dos cosas distintas: la primera describe como esta el catalogo ahora, la segunda por
 * que llego a estarlo, y esa segunda no se sobrescribe nunca.
 *
 * <p><strong>Desde HU-013 tambien se lee.</strong> Hasta entonces solo escribia, y lo que
 * RN-045 obliga a guardar no lo podia consultar nadie salvo por consulta directa a la base.
 */
public interface ModerationLog {

    /**
     * Lo que decidio un moderador.
     *
     * <p>Recibe el motivo y la nota como texto y no como la enumeracion del dominio porque
     * las tres decisiones no comparten lista de motivos: el rechazo y el retiro usan
     * {@code ListingRejectionReason} y la aprobacion no lleva ninguno.
     *
     * <p><strong>La fecha la pone quien llama, y eso no era asi.</strong> Hasta HU-013 la
     * ponia el {@code now()} de la tabla, que era inofensivo mientras la bitacora solo se
     * auditaba por consulta directa. Al empezar a leerla en orden dejo de serlo: el envio
     * sella la hora con el reloj de la aplicacion y las decisiones la tomaban del motor de
     * la base, y dos relojes escribiendo en un mismo registro ordenado pueden cruzarse. Lo
     * destapo {@code ListingJourneyTest}, que vio una aprobacion fechada antes del envio que
     * la habia provocado.
     *
     * @param cuando el mismo instante con el que el caso de uso sella la publicacion. El
     *     rastro y {@code listings.moderated_at} cuentan el mismo momento o no cuentan lo
     *     mismo
     */
    void registrar(
            ListingId publicacion,
            ModeratorId actor,
            ModerationAction accion,
            @Nullable String motivo,
            @Nullable String nota,
            Instant cuando);

    /**
     * Que la publicacion entro a revision. HU-013.
     *
     * <p><strong>Metodo propio y con {@link SellerId}, en vez de pasar al vendedor por el
     * parametro {@code actor} del de arriba.</strong> Quien envia no es un moderador, y
     * fabricar un {@code ModeratorId} a partir de un vendedor seria darle a un identificador
     * tipado el significado contrario al suyo aunque el UUID de dentro fuera el mismo. En la
     * tabla los dos acaban en la misma columna, y esa traduccion es de {@code infrastructure}.
     *
     * <p>No lleva motivo ni nota: enviar algo a revision no se justifica.
     *
     * @param cuando el instante que sella el dominio al entrar a {@code PENDING_REVIEW}, y
     *     no el de escribir la fila: el rastro cuenta cuando paso, no cuando se anoto
     */
    void registrarEnvio(ListingId publicacion, SellerId vendedor, Instant cuando);

    /**
     * Lo que le paso a esa publicacion, lo mas reciente primero. HU-013.
     *
     * <p>Devuelve tipos de dominio, no filas ni JSON: es un puerto de salida y quien
     * traduce al contrato publico es {@code presentation}.
     *
     * <p><strong>Ni {@code actor_id} ni {@code notes} viajan en {@link ModerationEvent}</strong>,
     * y por eso quien implemente esto no debe traerlos de la base: filtrar despues es
     * confiar en que nadie escriba el campo de mas (RN-074).
     *
     * <p>Sin paginar. Se decidio a proposito al escribir la historia: no hay tope de vueltas
     * escrito para una publicacion, pero tampoco volumen que justifique paginar una linea de
     * tiempo corta. Si algun dia lo hay, esta firma es lo que cambia.
     *
     * <p>Lista vacia cuando no ha pasado nada, que es lo normal en un borrador. Quien llame
     * no puede distinguir eso de «la publicacion no existe», y no le hace falta: eso ya lo
     * resolvio antes con el repositorio.
     */
    List<ModerationEvent> historial(ListingId publicacion);
}
