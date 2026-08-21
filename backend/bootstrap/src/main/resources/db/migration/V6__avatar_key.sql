-- Foto de perfil. HU-001 criterio 21, la parte que faltaba (ADR-0018).
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

-- Se guarda la CLAVE del archivo, no su direccion.
--
-- `avatar_url` existe desde V2 y nunca se uso. No se reutiliza, y no es por
-- gusto: lo que se guarda es la clave dentro del almacen, y la direccion publica
-- se construye en el borde a partir de la configuracion del almacen (ADR-0018).
-- Guardar una clave en una columna llamada `url` seria una mentira que confunde a
-- todo el que lea la tabla despues, y guardar la direccion completa ataria cada
-- fila al dominio desde el que se sirve hoy: cambiar de CDN obligaria a reescribir
-- la tabla.
ALTER TABLE users ADD COLUMN avatar_key text;

COMMENT ON COLUMN users.avatar_key IS
    'Clave del archivo en el almacen publico. La direccion se construye en el borde (ADR-0018)';

-- La clave la genera el servidor a partir de un identificador (ADR-0015) y nunca
-- del nombre que traia el archivo. La restriccion repite en la base la forma que
-- FileKey ya valida en el dominio: es la unica que sigue en pie si algun dia algo
-- escribe en esta columna sin pasar por ahi.
ALTER TABLE users ADD CONSTRAINT users_avatar_key_forma
    CHECK (avatar_key IS NULL OR avatar_key ~ '^[a-z0-9]+(-[a-z0-9]+)*/[a-zA-Z0-9_-]+\.[a-z]{3,4}$');

-- `avatar_url` queda por ahora. Se elimina en una migracion posterior y no en
-- esta: toda migracion destructiva va en dos pasos separados por al menos un
-- despliegue (docs/operacion/entornos.md). Que la columna nunca se haya usado no
-- cambia la regla; la regla existe para no tener que comprobar caso por caso si
-- alguien la usaba.
COMMENT ON COLUMN users.avatar_url IS
    'Sin uso. Sustituida por avatar_key en V6; se elimina en una migracion posterior';
