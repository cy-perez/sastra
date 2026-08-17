package co.sastra.shared.rest;

import java.time.Duration;

/**
 * Los numeros del limite de peticiones, ya resueltos.
 *
 * <p>Existe por lo mismo que {@link RefreshCookies} recibe sus valores sueltos: la
 * configuracion tipada vive en {@code infrastructure}, una capa que este modulo no
 * ve, y {@code bootstrap} es quien puede traducir de una a otra.
 *
 * @param maxDeCredenciales peticiones por minuto en las rutas donde se escriben o
 *     se piden credenciales
 * @param maxDeSesion peticiones en las rutas que el navegador dispara solo
 * @param maxDeOrigenes techo de origenes vivos en memoria
 */
public record RateLimitSettings(
        int maxDeCredenciales,
        Duration ventanaDeCredenciales,
        int maxDeSesion,
        Duration ventanaDeSesion,
        int maxDeOrigenes) {}
