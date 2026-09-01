-- RN-069: el motivo de la revocacion tiene su propia lista cerrada y su propia columna.
--
-- Hasta HU-010, revocar reutilizaba la lista del rechazo y escribia en rejection_reason.
-- Son dos decisiones distintas sobre cosas distintas: el rechazo juzga una solicitud que
-- todavia no se aprobo, y la revocacion se lo quita a alguien que ya vende. Con una sola
-- columna, quien lee lo guardado tiene que mirar status para saber que enumeracion esta
-- parseando, y el correo que recibe la persona sale con el texto de la otra lista.
--
-- No se migra ningun dato. Las filas en REVOKED que pudieran existir conservan su motivo
-- viejo en rejection_reason, que es de la lista equivocada: no hay traduccion posible
-- entre las dos listas que no sea inventarse la decision que alguien tomo. En dev no hay
-- ninguna fila asi y produccion todavia no existe.
ALTER TABLE seller_verifications
    ADD COLUMN revocation_reason text;

COMMENT ON COLUMN seller_verifications.revocation_reason IS
    'Motivo de RN-069. Excluyente con rejection_reason: reintentar limpia los dos.';
