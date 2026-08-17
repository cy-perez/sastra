package co.sastra.shared.error;

/**
 * Catalogo de codigos de error de la API.
 *
 * <p>El backend nunca envia texto para mostrar al usuario final: envia uno de
 * estos codigos y el frontend lo traduce con Transloco. Agregar un codigo aqui
 * obliga a agregarlo a {@code frontend/src/i18n/*.json} en el mismo commit
 * (docs/arquitectura/contrato-api.md).
 *
 * <p>Los prefijos separan contextos: {@code AUTH_}, {@code USER_},
 * {@code SELLER_}, {@code CATALOG_}, {@code ORDER_}, {@code PAYMENT_},
 * {@code SHIPPING_}, {@code COMMON_}.
 *
 * <p>Falta a proposito un codigo para "el correo ya existe". El criterio 2 de
 * HU-001 exige que registrar un correo existente responda exactamente igual que
 * un registro nuevo: devolver un codigo distinto convertiria el formulario en un
 * detector de cuentas.
 */
public enum ErrorCode {

    /** RN-005: la contrasena no llega al minimo de caracteres. */
    AUTH_PASSWORD_TOO_SHORT,

    /** RN-005: la contrasena aparece en una filtracion conocida (ADR-0013). */
    AUTH_PASSWORD_BREACHED,

    /** RN-008: no ha cumplido 18 anos. */
    AUTH_UNDERAGE,

    /** Falta aceptar los terminos o la politica de tratamiento de datos. */
    AUTH_CONSENT_REQUIRED,

    /** El enlace no corresponde a ningun token, o ya se uso. */
    AUTH_VERIFICATION_TOKEN_INVALID,

    /** RN-003: el enlace caduco. */
    AUTH_VERIFICATION_TOKEN_EXPIRED,

    /** Se agotaron los reenvios permitidos dentro de la hora. */
    AUTH_RESEND_LIMIT_REACHED,

    /** La peticion no cumple el contrato. El detalle por campo va en {@code errors}. */
    COMMON_VALIDATION_FAILED,

    /** Cualquier fallo no previsto. Nunca lleva detalle hacia afuera. */
    COMMON_UNEXPECTED
}
