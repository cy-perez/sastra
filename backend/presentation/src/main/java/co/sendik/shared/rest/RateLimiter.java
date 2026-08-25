package co.sendik.shared.rest;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de peticiones por clave, con ventana fija.
 *
 * <p>Es lo unico de este limite que tiene logica, y por eso vive separado del
 * borde HTTP: se prueba con un reloj fijo y sin levantar nada.
 *
 * <p><strong>La cuenta es de esta instancia y de nadie mas.</strong> Con dos
 * replicas detras de un balanceador, cada una permite el maximo por separado y el
 * limite real se duplica. Es aceptable mientras el despliegue sea de una sola
 * instancia (ADR-0009) y sigue siendo mejor que no tener nada, pero el dia que se
 * escale horizontalmente esto se queda corto y el conteo tiene que mudarse a un
 * almacen compartido o al balanceador.
 *
 * <p>Ventana fija y no deslizante: permite hasta el doble del maximo a caballo de
 * dos ventanas. Para lo que aqui se defiende, que es que nadie pruebe
 * contrasenas a millares ni use el registro como emisor de correo gratuito, esa
 * imprecision no cambia nada, y una ventana deslizante costaria guardar la marca
 * de tiempo de cada peticion en vez de un entero.
 */
public final class RateLimiter {

    private final int maximo;
    private final Duration ventana;
    private final int maximoDeClaves;

    private final Map<String, Conteo> conteos = new ConcurrentHashMap<>();

    private record Conteo(Instant inicio, int peticiones) {}

    /**
     * @param maximo peticiones permitidas dentro de una ventana
     * @param ventana cuanto dura la ventana
     * @param maximoDeClaves techo de claves vivas. Sin el, quien varie su IP a
     *     voluntad hace crecer el mapa hasta agotar la memoria: la defensa se
     *     convertiria en la via de ataque
     */
    public RateLimiter(int maximo, Duration ventana, int maximoDeClaves) {
        if (maximo < 1) {
            throw new IllegalArgumentException("El maximo de peticiones debe ser al menos 1");
        }
        if (ventana.isZero() || ventana.isNegative()) {
            throw new IllegalArgumentException("La ventana debe ser positiva");
        }
        if (maximoDeClaves < 1) {
            throw new IllegalArgumentException("El maximo de claves debe ser al menos 1");
        }

        this.maximo = maximo;
        this.ventana = ventana;
        this.maximoDeClaves = maximoDeClaves;
    }

    /**
     * Anota una peticion de esta clave.
     *
     * @return vacio si puede pasar; si no, cuanto falta para que se abra la
     *     ventana siguiente
     */
    public Optional<Duration> registrar(String clave, Instant ahora) {
        // compute es atomico sobre la clave: contar y decidir en dos pasos dejaria
        // pasar de mas justo cuando llegan muchas a la vez, que es cuando importa.
        Conteo actual = conteos.compute(clave, (sinUsar, previo) -> {
            if (previo == null || haVencido(previo, ahora)) {
                return new Conteo(ahora, 1);
            }
            return new Conteo(previo.inicio(), previo.peticiones() + 1);
        });

        if (actual.peticiones() <= maximo) {
            descartarLoVencidoSiHaceFalta(ahora);
            return Optional.empty();
        }

        // Siempre positiva: si la ventana ya hubiera vencido, el compute de arriba
        // la habria reiniciado y no se llegaria hasta aqui.
        //
        // Puede ser de milisegundos, y eso esta bien: aqui se devuelve el tiempo
        // que de verdad queda. Redondearlo al segundo es cosa de quien lo escribe
        // en la cabecera Retry-After, que solo admite segundos enteros.
        return Optional.of(Duration.between(ahora, actual.inicio().plus(ventana)));
    }

    private boolean haVencido(Conteo conteo, Instant ahora) {
        return !ahora.isBefore(conteo.inicio().plus(ventana));
    }

    /**
     * Barrido perezoso: solo cuando el mapa pasa del techo, y solo de lo ya
     * vencido. Un barrido programado obligaria a un hilo propio para algo que se
     * resuelve mirando el tamano en la peticion que lo hace crecer.
     */
    private void descartarLoVencidoSiHaceFalta(Instant ahora) {
        if (conteos.size() <= maximoDeClaves) {
            return;
        }

        Iterator<Map.Entry<String, Conteo>> entradas = conteos.entrySet().iterator();
        while (entradas.hasNext()) {
            if (haVencido(entradas.next().getValue(), ahora)) {
                entradas.remove();
            }
        }
    }
}
