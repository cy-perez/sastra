package co.sastra.identity.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * La solicitud de verificacion tal como la ve su dueno. Criterio 11 de HU-002.
 *
 * <p><strong>Lo que este tipo no tiene es la mitad de su valor.</strong> No hay claves
 * de archivo, ni direcciones de imagen, ni el numero de documento, ni el de la cuenta:
 * solo los cuatro ultimos digitos de cada uno. Y no es una omision que haya que
 * recordar en cada endpoint, es que los campos no existen, asi que no se pueden
 * devolver por descuido (RN-046, criterio 11).
 *
 * <p>De las imagenes no sale ni una direccion, tampoco firmada. Lo que hay en el
 * almacen reservado se lee por un endpoint que comprueba el rol y registra la lectura
 * (ADR-0018); un enlace que funciona por si solo no puede registrar quien lo uso.
 *
 * <p>Si lleva los nombres de titular, y eso es deliberado: son de quien mira, los
 * escribio esa misma persona, y verlos es lo que le permite corregir un rechazo por
 * RN-012. Un perfil publico de vendedor sera otro tipo y no llevara ninguno de estos
 * campos.
 *
 * @param status uno de los seis estados del glosario
 * @param attempts envios ya realizados, para que la pantalla pueda decir cuantos
 *     quedan (RN-014)
 * @param documentSubmitted si ya entrego el documento. Un booleano y no la clave:
 *     lo unico que la pantalla necesita saber es si ese paso esta hecho
 * @param selfieSubmitted lo mismo con la selfie
 * @param rejectionReason el motivo de la lista cerrada, para traducir en el cliente
 * @param rejectionNote la nota del moderador, que viaja a la persona rechazada
 */
public record SellerVerificationResponse(
        String status,
        int attempts,
        int remainingAttempts,
        boolean complete,
        boolean documentSubmitted,
        @Nullable String documentType,
        @Nullable String documentNumberLastFour,
        @Nullable String documentHolderName,
        boolean selfieSubmitted,
        @Nullable String bank,
        @Nullable String bankAccountType,
        @Nullable String bankAccountLastFour,
        @Nullable String bankAccountHolderName,
        @Nullable String rejectionReason,
        @Nullable String rejectionNote,
        String updatedAt) {}
