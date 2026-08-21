package co.sastra.shared.id;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Genera identificadores UUID version 7: ordenables por tiempo (RFC 9562).
 *
 * <p>Existe porque {@code docs/arquitectura/modelo-datos.md} fija v7 para la clave
 * primaria de toda tabla y Java 25 no trae generador de v7 en su biblioteca
 * estandar: {@link UUID} sabe construirse desde dos {@code long} y sabe generar v4,
 * nada mas. Se compone aqui con biblioteca estandar en lugar de traer una
 * dependencia porque estos identificadores viven en {@code domain}, cuya lista de
 * dependencias es corta a proposito (ADR-0002, ADR-0015).
 *
 * <p>Los 48 bits altos llevan los milisegundos del instante de creacion, asi que
 * las filas nuevas caen juntas al final del indice en vez de partir el arbol en un
 * punto aleatorio. Es la unica cosa que un v7 aporta sobre un v4: el orden temporal
 * consultable ya lo da {@code created_at} en todas las tablas.
 *
 * <p><strong>Sin contador de monotonicidad.</strong> Dos identificadores generados
 * dentro del mismo milisegundo quedan en orden arbitrario entre si. La RFC 9562
 * describe el contador como opcional y solo afecta a la localidad de escritura
 * dentro de ese milisegundo; si algun dia hace falta, entra sin cambiar la firma.
 *
 * <p><strong>La marca de tiempo va dentro del identificador.</strong> Quien vea un
 * {@code id} sabe cuando se creo la fila, y por eso esto no sirve para todo: un
 * identificador que salga hacia afuera y cuya hora revele algo sigue siendo v4, con
 * su excepcion anotada en ADR-0015. Los tokens tampoco son de aqui: son hash
 * aleatorio, no {@code uuid}.
 */
public final class Uuid7 {

    /** Version 7 en los cuatro bits que la RFC 9562 reserva para ella. */
    private static final long VERSION = 0x7000L;

    /** Variante 10 en los dos bits altos del septimo octeto. */
    private static final long VARIANTE = 0x8000000000000000L;

    private static final int BITS_DE_MILISEGUNDOS = 48;

    private static final SecureRandom AZAR = new SecureRandom();

    private Uuid7() {
        // Solo fabricas.
    }

    /** Un identificador nuevo con el instante actual. */
    public static UUID nuevo() {
        return nuevo(Instant.now());
    }

    /**
     * Un identificador nuevo con el instante que se le da.
     *
     * <p>La sobrecarga existe para que la prueba pueda afirmar que la marca de
     * tiempo embebida es la que se esperaba. En produccion se llama {@link #nuevo()}.
     */
    public static UUID nuevo(Instant instante) {
        Objects.requireNonNull(instante, "El instante es obligatorio");

        long milisegundos = instante.toEpochMilli();
        if (milisegundos < 0) {
            throw new IllegalArgumentException("El instante es anterior a la epoca: " + instante);
        }
        if (milisegundos >= 1L << BITS_DE_MILISEGUNDOS) {
            // Ano 10889. No es alcanzable, pero un desbordamiento silencioso aqui
            // produciria identificadores que ya no ordenan y nadie lo notaria.
            throw new IllegalArgumentException("El instante no cabe en 48 bits: " + instante);
        }

        byte[] azar = new byte[10];
        AZAR.nextBytes(azar);

        // 48 bits de tiempo, 4 de version y 12 de azar.
        long altos = (milisegundos << 16) | VERSION | (leerDosBytes(azar, 0) & 0x0FFFL);

        // 2 bits de variante y 62 de azar.
        long bajos = (leerOchoBytes(azar, 2) >>> 2) | VARIANTE;

        return new UUID(altos, bajos);
    }

    private static long leerDosBytes(byte[] origen, int desde) {
        return ((long) (origen[desde] & 0xFF) << 8) | (origen[desde + 1] & 0xFF);
    }

    private static long leerOchoBytes(byte[] origen, int desde) {
        long valor = 0;
        for (int i = 0; i < 8; i++) {
            valor = (valor << 8) | (origen[desde + i] & 0xFF);
        }
        return valor;
    }
}
