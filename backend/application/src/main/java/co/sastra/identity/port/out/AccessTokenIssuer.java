package co.sastra.identity.port.out;

import co.sastra.identity.model.TokenFamilyId;
import co.sastra.identity.model.User;
import java.time.Instant;

/**
 * Puerto de salida hacia la emision del token de acceso (ADR-0003).
 *
 * <p>El formato del token no aparece en esta firma a proposito. Que hoy sea un JWT
 * firmado con HS256 es una decision de infraestructura; el caso de uso solo
 * necesita un valor opaco y saber cuando deja de servir.
 *
 * <p>No hay metodo para validar. Validar el token de acceso ocurre en el borde
 * HTTP en cada peticion, y de eso se encarga Spring Security con su propio
 * decodificador: hacerlo pasar por aqui obligaria a que la cadena de filtros
 * dependiera de un caso de uso.
 */
public interface AccessTokenIssuer {

    /**
     * @param value el token que viaja en la cabecera {@code Authorization} y vive
     *     solo en memoria del cliente
     * @param expiresAt cuando deja de valer. RN-007 lo fija en 15 minutos
     */
    record IssuedAccessToken(String value, Instant expiresAt) {}

    /**
     * @param sesion a que sesion pertenece este token. Es el identificador de la
     *     <strong>familia</strong> y no el del token de refresco: la familia
     *     sobrevive a las rotaciones y el token cambia en cada refresco, asi que
     *     solo la familia identifica la sesion de forma estable
     *     <p>Sirve para el criterio 17: la lista de sesiones activas tiene que
     *     poder senalar cual es la que se esta usando ahora mismo, y la cookie de
     *     refresco no llega a {@code /users/me} porque su ruta esta limitada a
     *     {@code /api/v1/auth}. Sin este dato el servidor no puede saberlo.
     */
    IssuedAccessToken emitir(User usuario, TokenFamilyId sesion, Instant ahora);
}
