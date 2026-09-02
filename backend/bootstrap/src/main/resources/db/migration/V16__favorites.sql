-- Los favoritos. HU-011, RN-070 a RN-073.
--
-- Una fila por par persona-publicacion. La clave primaria ES el par y no un `id`
-- propio, que es la excepcion que ya sienta `user_roles` en V1: aqui la identidad
-- de la fila es la pareja, no algo que la fila tenga.
--
-- Y esa misma restriccion es lo que hace idempotente al criterio 4 sin leer antes
-- de escribir. Un "si no existe, guarda" no basta: entre esa lectura y la
-- escritura cabe la peticion de la otra pestana, y ahi no hay consulta que salve.
-- El adaptador se apoya en la clave con ON CONFLICT DO NOTHING.
--
-- No hay `updated_at`: un favorito no se modifica, se pone y se quita.
--
-- Las dos claves foraneas van sin ON DELETE CASCADE a proposito. Ninguna de las
-- dos filas de las que cuelga se borra nunca: una publicacion se archiva y una
-- cuenta se anonimiza conservando su fila. Lo que si borra estos favoritos es el
-- cierre de cuenta, explicitamente, porque son dato personal
-- (docs/operacion/datos-personales.md).
CREATE TABLE favorites (
    user_id    uuid        NOT NULL REFERENCES users (id),
    listing_id uuid        NOT NULL REFERENCES listings (id),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, listing_id)
);

COMMENT ON TABLE favorites IS
    'Publicaciones que alguien guardo para volver a ellas. Privadas: RN-070';

COMMENT ON COLUMN favorites.created_at IS
    'Cuando se marco. Es la fecha del gesto y no la de la publicacion: por ella ordena la lista (criterio 11)';

-- La lista propia, escrita como indice. Criterios 11 y 12.
--
-- Las tres columnas en el mismo orden que la consulta: filtra por persona, ordena
-- por el gesto y desempata por publicacion. El desempate no es de adorno:
-- `created_at` se repite -- dos toques seguidos, y siempre con un reloj fijo en
-- pruebas -- y un cursor sobre un orden indefinido se salta filas o las repite.
--
-- La clave primaria no sirve para esto. Es (user_id, listing_id), asi que ordena
-- por identificador de publicacion y no por fecha: PostgreSQL tendria que ordenar
-- en memoria la lista entera de alguien antes de entregar las primeras
-- veinticuatro.
--
-- No es parcial, al reves que el indice del catalogo. Aquel filtra por un estado
-- que vive en su misma tabla; el estado que RN-071 exige aqui esta en `listings`,
-- al otro lado del join, y un indice parcial no puede mirar otra tabla.
CREATE INDEX idx_favorites_recent
    ON favorites (user_id, created_at DESC, listing_id DESC);

COMMENT ON INDEX idx_favorites_recent IS
    'La lista de favoritos de HU-011: filtra por persona y ordena por la fecha del gesto';
