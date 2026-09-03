package co.sendik.catalog.port.out;

import co.sendik.catalog.model.BuyerId;

/**
 * Si la cuenta de quien marca sigue existiendo. HU-011.
 *
 * <p><strong>Existe por una promesa escrita.</strong> {@code docs/operacion/datos-personales.md}
 * dice que el token de acceso ya emitido sigue siendo valido hasta quince minutos despues
 * de cerrar la cuenta, y que «las rutas que devuelven o tocan datos responden 401 en cuanto
 * la cuenta deja de existir». Todas las de {@code /users/me} de identidad recargan la
 * cuenta y lo cumplen; marcar un favorito escribe una fila nueva y tenia que cumplirlo
 * tambien.
 *
 * <p>Sin esto, durante esos quince minutos un token de una cuenta ya cerrada podia insertar
 * favoritos —la clave foranea se satisface, porque cerrar anonimiza y no borra la fila de
 * {@code users}— y nada volvia a limpiarlos: el cierre ya habia pasado. Quedaba dato
 * personal vivo justo despues de haber ejercido el derecho de supresion.
 *
 * <p><strong>Solo lo consulta el caso de uso que escribe.</strong> Leer la lista de una
 * cuenta cerrada devuelve vacio —sus favoritos se borraron al cerrarla—, consultar el
 * estado devuelve que no esta marcada, y quitar no borra nada. Ninguna de las tres crea
 * dato ni revela nada, asi que anadirles la comprobacion seria una consulta mas por
 * peticion a cambio de nada.
 *
 * <p>La alternativa de fondo —rechazar en la cadena de seguridad cualquier token cuya
 * cuenta este cerrada, y quitar la comprobacion de todos los casos de uso que hoy la
 * repiten— es mejor y es de otro alcance: cuesta una consulta a la base en cada peticion
 * autenticada del sistema y merece su propia decision.
 */
public interface BuyerAccounts {

    /** Falso tambien si nunca existio: para lo que hay que decidir, es lo mismo. */
    boolean estaActiva(BuyerId quien);
}
