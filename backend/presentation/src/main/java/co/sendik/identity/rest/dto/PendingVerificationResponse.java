package co.sendik.identity.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * Una solicitud en la bandeja del moderador.
 *
 * <p><strong>Tampoco lleva numeros completos, y eso incluye al moderador.</strong> El
 * criterio 11 de HU-002 no hace excepciones por rol: en ninguna respuesta de la API
 * aparece el numero de documento ni el de la cuenta, solo los cuatro ultimos.
 *
 * <p>Lo que el moderador si puede ver son las imagenes, y no por aqui: se piden una por
 * una a un endpoint que comprueba su rol y anota la lectura. Este tipo no lleva ninguna
 * direccion ni ninguna clave, solo si el paso esta entregado.
 *
 * <p>Eso deja una limitacion real que conviene tener presente: **el moderador compara la
 * imagen del documento contra cuatro digitos**, no contra el numero entero. Es lo que el
 * criterio 11 permite, y si hace falta mas hay que decidirlo con su motivo.
 *
 * @param id el identificador de la solicitud, que es con lo que el moderador decide
 * @param waitingSince desde cuando espera, para poder atender lo mas viejo primero
 * @param own si la solicitud es de quien esta mirando la bandeja. RN-060 lo prohibe
 *     decidir, y sin este dato la interfaz no puede avisarlo antes de que lo intente: el
 *     resto del tipo no dice de quien es cada solicitud, a proposito (criterio 11). Es un
 *     booleano y no el identificador del dueno justamente por eso: responde lo unico que
 *     la pantalla necesita saber, sin decir quien es nadie
 */
public record PendingVerificationResponse(
        String id,
        int attempts,
        @Nullable String documentType,
        @Nullable String documentNumberLastFour,
        @Nullable String documentHolderName,
        boolean documentSubmitted,
        boolean selfieSubmitted,
        @Nullable String bank,
        @Nullable String bankAccountType,
        @Nullable String bankAccountLastFour,
        @Nullable String bankAccountHolderName,
        String waitingSince,
        boolean own) {}
