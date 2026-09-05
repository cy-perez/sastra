-- El envio a revision entra en la bitacora. HU-013, criterio 4.
--
-- Hasta aqui `moderation_events` anotaba solo lo que decidia un moderador, y con eso el
-- rastro de una publicacion empieza a media frase: se ve que la rechazaron sin que se vea
-- nunca que la habian mandado. Peor con las vueltas, que es lo que el criterio 4 pide ver:
-- rechazada, corregida y reenviada tiene dos envios, y `listings.submitted_at` guarda uno
-- -- el ultimo -- porque se sobrescribe en cada entrada a PENDING_REVIEW.
--
-- Es una decision de producto y no una de forma: el rastro pasa a contar lo que le paso a
-- la publicacion, y no solo lo que hizo Sendik.

ALTER TABLE moderation_events DROP CONSTRAINT moderation_events_action_valid;

ALTER TABLE moderation_events ADD CONSTRAINT moderation_events_action_valid CHECK (
    action IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'ARCHIVED'));

-- `actor_id` sigue siendo NOT NULL y sigue apuntando a `users`: quien envia es el vendedor,
-- y un vendedor es un usuario. La columna guarda quien hizo esto, no quien lo modero. Lo
-- que no cambia es que no sale nunca en una respuesta (RN-074).

-- ---------------------------------------------------------------------------
-- Los envios que ya ocurrieron
-- ---------------------------------------------------------------------------
--
-- Sin esto, toda publicacion que hoy existe estrena el rastro sin su primera linea, y el
-- criterio 4 solo se cumpliria para lo que se publique de aqui en adelante.
--
-- Se rellena con `submitted_at`, que es lo unico que hay. Guarda un solo envio -- el
-- ultimo -- asi que a una publicacion que fue y volvio le queda anotada una vuelta y no
-- las dos. Es incompleto y es lo maximo que se puede reconstruir: inventar los envios
-- anteriores exigiria datos que nadie guardo. De aqui en adelante se anotan todos.
--
-- El orden sale bien con eso: para una rechazada y reenviada, `submitted_at` es posterior
-- al rechazo, que es exactamente donde va la fila.
--
-- El vendedor sale de `products`, porque `listings` no lo tiene: la publicacion se separo
-- del producto para que el ciclo de moderacion no contaminara los datos de la prenda.
--
-- `gen_random_uuid()` y no un v7, como la siembra del arbol en V9: estas filas se escriben
-- todas a la vez y su identificador solo desempata entre eventos del mismo instante, cosa
-- que no ocurre aqui -- cada publicacion recibe una sola y con la fecha que ya tenia.
INSERT INTO moderation_events (id, listing_id, actor_id, action, created_at)
SELECT gen_random_uuid(), l.id, p.seller_id, 'SUBMITTED', l.submitted_at
FROM listings l
         JOIN products p ON p.id = l.product_id
WHERE l.submitted_at IS NOT NULL;
