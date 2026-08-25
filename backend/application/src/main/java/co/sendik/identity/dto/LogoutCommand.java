package co.sendik.identity.dto;

import org.jspecify.annotations.Nullable;

/**
 * Cierre de sesion. Criterio 16.
 *
 * @param refreshToken admite nulo porque cerrar sesion sin cookie es un caso
 *     normal, no un error: el navegador puede haberla borrado, o la sesion puede
 *     haber caducado mientras la pestana estaba abierta. En los dos casos la
 *     respuesta es la misma, y el cliente termina igual de desconectado
 */
public record LogoutCommand(@Nullable String refreshToken) {}
