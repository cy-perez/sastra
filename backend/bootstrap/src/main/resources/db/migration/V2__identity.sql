-- Esquema de cuentas. HU-001 rebanada A: registro y verificacion de correo.
--
-- Solo las tablas que esta rebanada usa. refresh_tokens y login_attempts entran
-- con el inicio de sesion, en la migracion siguiente: una tabla que todavia no
-- consulta nadie no se crea.
--
-- La extension citext la activo V1__baseline.sql. Es lo que permite que
-- "Ana@Correo.co" y "ana@correo.co" sean la misma cuenta sin normalizar a mano
-- en cada consulta (RN-001).

CREATE TABLE users (
    id                uuid PRIMARY KEY,
    email             citext      NOT NULL,
    email_verified_at timestamptz,
    display_name      text        NOT NULL,
    -- RN-008: se guarda la fecha, no el resultado de la comprobacion, porque en
    -- Fase 2 se contrasta contra el documento de identidad.
    birth_date        date        NOT NULL,
    city              text,
    phone             text,
    avatar_url        text,
    locale            text        NOT NULL DEFAULT 'es',
    status            text        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),

    -- RN-001: un correo, una sola cuenta.
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSING', 'CLOSED')),
    CONSTRAINT users_locale_valid CHECK (locale IN ('es', 'en'))
);

-- La cuenta sin verificar no es un estado distinto: es email_verified_at nulo.
-- RN-002 la deja entrar pero sin poder hacer nada mas que reenviar el correo.
COMMENT ON COLUMN users.email_verified_at IS 'Nulo mientras la persona no verifique su correo (RN-002)';

CREATE TABLE user_roles (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       text        NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, role),
    CONSTRAINT user_roles_role_valid CHECK (role IN ('BUYER', 'SELLER', 'MODERATOR', 'ADMIN'))
);

-- Ser vendedor no es otra cuenta: es un rol adicional sobre la misma
-- (docs/producto/glosario.md). Por eso la clave primaria es compuesta.

CREATE TABLE user_credentials (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- Argon2id. Nunca la contrasena, ni cifrada ni reversible
    -- (docs/operacion/datos-personales.md).
    password_hash       text        NOT NULL,
    password_updated_at timestamptz NOT NULL DEFAULT now(),
    -- RN-006. Los usa la rebanada B; se crean aqui porque son parte de la misma
    -- fila y anadir columnas despues obliga a una migracion sin necesidad.
    failed_attempts     integer     NOT NULL DEFAULT 0,
    locked_until        timestamptz,

    CONSTRAINT user_credentials_failed_attempts_no_negativo CHECK (failed_attempts >= 0)
);

CREATE TABLE verification_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose    text        NOT NULL,
    -- Se guarda el hash, nunca el token: quien lea la base no puede verificar
    -- una cuenta ajena ni restablecer una contrasena.
    token_hash text        NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT verification_tokens_hash_unique UNIQUE (token_hash),
    CONSTRAINT verification_tokens_purpose_valid
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'))
);

-- Sostiene dos consultas: buscar el token que llega por el enlace, y contar los
-- reenvios de la ultima hora para el limite de tres de HU-001.
CREATE INDEX verification_tokens_user_purpose_created
    ON verification_tokens (user_id, purpose, created_at DESC);

CREATE TABLE consents (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document    text        NOT NULL,
    -- La version exacta del documento aceptado. Sin ella el consentimiento no
    -- se puede demostrar: nadie sabria a que texto dijo que si.
    version     text        NOT NULL,
    accepted_at timestamptz NOT NULL DEFAULT now(),
    ip_hash     text,

    CONSTRAINT consents_document_valid CHECK (document IN ('TERMS', 'PRIVACY'))
);

-- Una fila por documento, no una por registro: la Ley 1581 exige aceptacion
-- separada de los terminos y de la politica de tratamiento
-- (docs/operacion/datos-personales.md).
CREATE INDEX consents_user ON consents (user_id);
