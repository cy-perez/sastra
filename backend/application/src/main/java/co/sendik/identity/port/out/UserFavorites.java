package co.sendik.identity.port.out;

import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.UserId;
import java.util.List;

/**
 * Los favoritos de una persona, vistos desde {@code identity}. HU-011.
 *
 * <p><strong>Existe porque los favoritos son dato personal y esta capa responde por
 * ellos.</strong> Dicen que le interesa a una persona identificada
 * (docs/operacion/datos-personales.md), asi que entran en la descarga del criterio 22 y el
 * cierre de cuenta se los lleva. Lo que {@code identity} no puede hacer es leer ni escribir
 * la tabla, que es del catalogo: se lo pide por este puerto, y el adaptador pregunta por un
 * caso de uso publico del otro contexto.
 *
 * <p>Es el mismo patron que {@code SellerEligibility}, con la flecha al reves: alli el
 * catalogo le pregunta a identidad si alguien esta verificado. Que exista en las dos
 * direcciones no es un ciclo entre contextos sino dos conversaciones distintas, cada una
 * por la puerta publica del otro.
 *
 * <p><strong>Habla de {@link UserId} y no de identificadores del catalogo.</strong> Un
 * puerto se declara con el vocabulario de quien lo define; traducir al {@code BuyerId} del
 * otro contexto es trabajo del adaptador, que es el borde donde los dos se tocan.
 */
public interface UserFavorites {

    /**
     * Lo que esa persona tiene guardado, para su descarga de datos.
     *
     * <p>Sale sin filtrar por estado: la lista de la pantalla ensena lo que se puede volver
     * a ver, y esto entrega lo que se guarda.
     */
    List<UserDataExport.Favorito> de(UserId usuario);

    /**
     * Los borra. Para el cierre de cuenta.
     *
     * <p>No falla si no hay ninguno, que es el caso de casi todas las cuentas que se
     * cierran.
     */
    void borrarDe(UserId usuario);
}
