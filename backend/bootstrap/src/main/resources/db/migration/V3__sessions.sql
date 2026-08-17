-- Sesiones. HU-001 rebanada B: inicio de sesion, JWT y refresco rotatorio.
--
-- Las columnas del bloqueo por intentos (failed_attempts, locked_until) ya
-- existen desde V2: son parte de la misma fila de user_credentials y anadirlas
-- ahora habria costado una migracion sin necesidad.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

CREATE TABLE refresh_tokens (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Todos los tokens que salen de rotar uno comparten familia. Es lo que
    -- permite revocar la cadena completa al detectar reutilizacion (RN-007).
    family_id   uuid        NOT NULL,
    -- Se guarda el hash, nunca el token: quien lea la base no puede continuar la
    -- sesion de nadie (ADR-0003).
    token_hash  text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    -- El token que salio de rotar este. Nulo mientras no se haya usado, y es
    -- justamente su presencia la que delata una reutilizacion.
    replaced_by uuid        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    -- Con que navegador se abrio. Sirve para que la persona reconozca sus propias
    -- sesiones; la IP va hasheada porque es un dato personal
    -- (docs/operacion/datos-personales.md).
    user_agent  text,
    ip_hash     text,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash)
);

-- El unico sobre token_hash sostiene la consulta de cada refresco; el de familia
-- sostiene la revocacion en bloque (docs/arquitectura/modelo-datos.md).
CREATE INDEX refresh_tokens_user_family ON refresh_tokens (user_id, family_id);

COMMENT ON COLUMN refresh_tokens.replaced_by IS
    'Token que reemplazo a este al rotar. Su presencia significa que este ya se uso (RN-007)';

CREATE TABLE login_attempts (
    id         uuid        PRIMARY KEY,
    -- El correo va hasheado: esta tabla se conserva 90 dias y no tiene por que
    -- acumular direcciones en claro para cumplir su funcion, que es reconocer
    -- patrones de ataque.
    email_hash text        NOT NULL,
    ip_hash    text,
    succeeded  boolean     NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- Los intentos se consultan siempre por correo y en orden reciente: es la forma
-- de mirar que paso en una cuenta concreta.
CREATE INDEX login_attempts_email_created ON login_attempts (email_hash, created_at DESC);

COMMENT ON TABLE login_attempts IS
    'Auditoria de accesos, 90 dias. El bloqueo de RN-006 no se calcula de aqui sino de user_credentials';
