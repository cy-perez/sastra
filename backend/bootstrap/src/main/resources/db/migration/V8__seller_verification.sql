-- Verificacion de vendedor. HU-002, rebanada B.
--
-- Las dos columnas sensibles siguen el formato de ADR-0020: el cifrado con su
-- nonce y su etiqueta, la version de clave con la que se cifro, el HMAC indexado
-- que hace posible comparar sin descifrar, y los cuatro ultimos digitos en claro
-- porque es lo unico que la pantalla muestra.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

CREATE TABLE seller_verifications (
    id      uuid PRIMARY KEY,
    -- Una verificacion por cuenta. Los reintentos de RN-014 no crean filas: mueven
    -- el estado y suben el contador, porque lo que la persona ve es una sola
    -- solicitud que va y viene.
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status  text NOT NULL,

    -- Documento. Todo nulo mientras no lo haya entregado: el caso borde de HU-002
    -- exige guardar el avance y retomarlo donde iba.
    document_type              text,
    document_number_cipher     text,
    document_number_key_version smallint,
    document_number_lookup     bytea,
    document_number_last_four  text,
    document_holder_name       text,
    document_front_key         text,
    document_back_key          text,

    selfie_key text,

    -- Cuenta bancaria.
    bank_code                    text REFERENCES financial_institutions (code),
    bank_account_type            text,
    bank_account_cipher          text,
    bank_account_key_version     smallint,
    bank_account_last_four       text,
    bank_account_holder_name     text,

    -- RN-014: envios a revision, no correcciones de un formulario.
    attempts smallint NOT NULL DEFAULT 0,

    rejection_reason text,
    rejection_note   text,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT seller_verifications_user_unique UNIQUE (user_id),

    -- Los seis estados del glosario. REVOKED es distinto de REJECTED: el primero
    -- paso la revision y se le quito el sello (RN-013), el segundo no la paso nunca.
    CONSTRAINT seller_verifications_status_valid CHECK (
        status IN ('NOT_STARTED', 'IN_PROGRESS', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED', 'REVOKED')),

    CONSTRAINT seller_verifications_document_type_valid CHECK (
        document_type IS NULL OR document_type IN ('CC', 'CE', 'PPT')),

    CONSTRAINT seller_verifications_account_type_valid CHECK (
        bank_account_type IS NULL OR bank_account_type IN ('SAVINGS', 'CHECKING', 'ELECTRONIC_DEPOSIT')),

    CONSTRAINT seller_verifications_rejection_reason_valid CHECK (
        rejection_reason IS NULL OR rejection_reason IN (
            'ILLEGIBLE_PHOTOS', 'EXPIRED_DOCUMENT', 'HOLDER_MISMATCH',
            'DOCUMENT_ALREADY_VERIFIED', 'REQUIREMENTS_NOT_MET')),

    CONSTRAINT seller_verifications_attempts_valid CHECK (attempts BETWEEN 0 AND 3),

    -- Las cuatro columnas del documento viajan juntas o no viajan: un cifrado sin su
    -- version de clave no se puede descifrar nunca mas, y es el tipo de fila que se
    -- descubre el dia que hay que leerla.
    CONSTRAINT seller_verifications_document_completo CHECK (
        (document_number_cipher IS NULL
            AND document_number_key_version IS NULL
            AND document_number_lookup IS NULL
            AND document_number_last_four IS NULL)
        OR (document_number_cipher IS NOT NULL
            AND document_number_key_version IS NOT NULL
            AND document_number_lookup IS NOT NULL
            AND document_number_last_four IS NOT NULL)),

    CONSTRAINT seller_verifications_cuenta_completa CHECK (
        (bank_account_cipher IS NULL
            AND bank_account_key_version IS NULL
            AND bank_account_last_four IS NULL)
        OR (bank_account_cipher IS NOT NULL
            AND bank_account_key_version IS NOT NULL
            AND bank_account_last_four IS NOT NULL)),

    CONSTRAINT seller_verifications_last_four_forma CHECK (
        (document_number_last_four IS NULL OR document_number_last_four ~ '^[0-9]{4}$')
        AND (bank_account_last_four IS NULL OR bank_account_last_four ~ '^[0-9]{4}$')),

    -- La misma forma que FileKey valida en el dominio, para las tres claves.
    CONSTRAINT seller_verifications_claves_forma CHECK (
        (document_front_key IS NULL OR document_front_key ~ '^[a-z0-9]+(-[a-z0-9]+)*/[a-zA-Z0-9_-]+\.[a-z]{3,4}$')
        AND (document_back_key IS NULL OR document_back_key ~ '^[a-z0-9]+(-[a-z0-9]+)*/[a-zA-Z0-9_-]+\.[a-z]{3,4}$')
        AND (selfie_key IS NULL OR selfie_key ~ '^[a-z0-9]+(-[a-z0-9]+)*/[a-zA-Z0-9_-]+\.[a-z]{3,4}$')),

    -- El frente y el reverso no pueden ser la misma imagen: seria la misma foto
    -- subida dos veces y el moderador mirando dos veces el mismo lado.
    CONSTRAINT seller_verifications_caras_distintas CHECK (
        document_front_key IS NULL OR document_back_key IS NULL OR document_front_key <> document_back_key)
);

COMMENT ON COLUMN seller_verifications.document_number_cipher IS
    'AES-256-GCM con nonce y etiqueta (ADR-0020). Nunca sale en una respuesta de la API (RN-046)';

COMMENT ON COLUMN seller_verifications.document_number_lookup IS
    'HMAC-SHA256 con clave propia, distinta de la de cifrado. Es lo unico comparable: el cifrado no se puede indexar';

COMMENT ON COLUMN seller_verifications.document_number_last_four IS
    'En claro a proposito: es lo que la pantalla muestra y cuatro digitos no identifican a nadie';

COMMENT ON COLUMN seller_verifications.attempts IS
    'RN-014: maximo tres. El cuarto exige que una persona intervenga';

-- Criterio 5 de HU-002 y RN-010: un mismo documento no puede quedar VERIFICADO en
-- dos cuentas.
--
-- Indice parcial y no unico sobre toda la tabla, y eso es la lectura literal del
-- criterio: dos personas pueden llegar a tener el mismo documento en revision
-- —pasa cuando alguien intenta usar la cedula de otro— y lo que no puede pasar es
-- que las dos queden verificadas. La segunda aprobacion falla aqui aunque el caso
-- de uso no lo hubiera comprobado antes, que es de lo que sirve una restriccion en
-- la base.
--
-- REVOKED no bloquea: a quien se le revoco el sello ya no es vendedor, y dejar su
-- documento tomado para siempre impediria que la misma persona lo intentara de
-- nuevo con otra cuenta.
CREATE UNIQUE INDEX seller_verifications_documento_verificado_unico
    ON seller_verifications (document_number_lookup)
    WHERE status = 'VERIFIED' AND document_number_lookup IS NOT NULL;

-- Para la bandeja del moderador: las que esperan revision, las mas viejas primero.
CREATE INDEX seller_verifications_pendientes
    ON seller_verifications (updated_at)
    WHERE status = 'PENDING_REVIEW';

-- Bitacora de accesos. HU-002: "todo acceso a estos datos queda registrado con
-- actor y motivo".
--
-- Es la razon de que la cedula y la selfie no se sirvan por URL firmada: un enlace
-- que funciona por si solo no puede registrar quien lo uso (ADR-0018).
CREATE TABLE verification_access_log (
    id              uuid PRIMARY KEY,
    verification_id uuid NOT NULL REFERENCES seller_verifications (id) ON DELETE CASCADE,
    -- Quien miro. No admite nulo: un acceso sin actor no es una bitacora.
    actor_id        uuid NOT NULL REFERENCES users (id),
    action          text NOT NULL,
    reason          text,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT verification_access_log_action_valid CHECK (action IN (
        'VIEW_DOCUMENT_FRONT', 'VIEW_DOCUMENT_BACK', 'VIEW_SELFIE',
        'VIEW_BANK_ACCOUNT', 'APPROVE', 'REJECT', 'REVOKE'))
);

COMMENT ON TABLE verification_access_log IS
    'Quien vio o decidio sobre datos sensibles de una verificacion (RN-046, HU-002)';

COMMENT ON COLUMN verification_access_log.reason IS
    'Motivo declarado por quien accede. Nunca contiene el dato al que se accedio';

CREATE INDEX verification_access_log_por_verificacion
    ON verification_access_log (verification_id, created_at DESC);
