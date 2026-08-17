-- Base del esquema.
--
-- Aqui no se crea ninguna tabla de negocio: las de usuarios llegan en la
-- siguiente migracion, con HU-001. Esta solo deja la base lista.
--
-- citext permite comparar correos sin distinguir mayusculas, que es como se
-- normaliza el correo de un usuario (docs/arquitectura/modelo-datos.md). Se
-- activa antes de que exista la tabla que lo usa porque crear una extension
-- exige permisos que la migracion de negocio no deberia necesitar.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

CREATE EXTENSION IF NOT EXISTS citext;
