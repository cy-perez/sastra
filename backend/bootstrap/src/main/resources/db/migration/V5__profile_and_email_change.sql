-- Perfil editable y cambio de correo. HU-001 criterio 21.
--
-- Las columnas city, phone y avatar_url ya existen desde V2: se anticiparon con
-- el esquema inicial y aqui empiezan a usarse. avatar_url sigue sin usarse
-- porque la foto es una rebanada aparte, la que necesita almacenamiento.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

-- Donde vive el correo nuevo mientras no se confirma.
--
-- Va en el token y no en users a proposito: el token ES el cambio pendiente.
-- Ponerlo en la fila del usuario crearia una segunda fuente de verdad que habria
-- que limpiar cuando el enlace caduque, y nadie limpia lo que caduca solo.
--
-- El criterio 21 exige verificar el correo nuevo ANTES de reemplazar el anterior.
-- Al reves, quien escribiera mal una letra se quedaria fuera de su propia cuenta.
ALTER TABLE verification_tokens ADD COLUMN new_email citext;

COMMENT ON COLUMN verification_tokens.new_email IS
    'Correo pendiente de confirmar. Solo en tokens EMAIL_CHANGE (criterio 21)';

-- Un token de cambio sin correo nuevo no significa nada, y uno de cualquier otro
-- proposito con correo nuevo tampoco. La restriccion lo dice en vez de confiarlo
-- al codigo que escribe.
ALTER TABLE verification_tokens ADD CONSTRAINT verification_tokens_new_email_solo_en_cambio
    CHECK ((purpose = 'EMAIL_CHANGE') = (new_email IS NOT NULL));
