package co.sendik.identity.rest.dto;

/**
 * Lo justo para saber si hay sello que revocar, y cual. HU-010.
 *
 * <p><strong>Dos campos, y es deliberado.</strong> Quien pregunta es un moderador parado
 * en el perfil publico de alguien, y lo unico que necesita para decidir si ofrece la
 * accion es si esta verificado y sobre que identificador actuar. Todo lo demas de la
 * solicitud —el documento, la selfie, la cuenta— esta a una peticion de distancia por
 * {@code /verifications/{id}/images/...}, que si registra quien miro (RN-046, ADR-0018).
 *
 * <p>Por eso esta lectura <strong>no</strong> anota nada en {@code
 * verification_access_log}: no entrega ningun dato personal, y una bitacora que se llena
 * de accesos que no vieron nada deja de servir para encontrar los que si.
 */
public record SellerVerificationSummaryResponse(String id, String status) {}
