-- El catalogo publico. HU-009, RN-068.
--
-- Es la consulta del catalogo escrita como indice, igual que hizo V12 con la cola
-- del moderador: aquella filtra por `PENDING_REVIEW` y ordena por espera, esta
-- filtra por `PUBLISHED` y ordena por fecha de publicacion.
--
-- Parcial, y por lo mismo: de los siete estados el catalogo solo mira uno, y un
-- indice que solo cubre lo que se consulta no crece con los borradores ni con lo
-- archivado, que es justo lo que mas se acumula.
--
-- Las dos columnas en el mismo orden que el ORDER BY, y `id` no esta de adorno:
-- `published_at` se repite -- dos aprobaciones en el mismo instante son normales
-- con un reloj fijo y posibles en produccion -- y sin desempate el orden entre
-- iguales es indefinido. Un cursor sobre un orden indefinido se salta filas o las
-- repite, que es exactamente lo que la paginacion por cursor existe para evitar.
CREATE INDEX idx_listings_catalog
    ON listings (published_at DESC, id DESC)
    WHERE status = 'PUBLISHED';

COMMENT ON INDEX idx_listings_catalog IS
    'El catalogo publico de HU-009: filtra por PUBLISHED y ordena por fecha e id';

-- El escaparate de un vendedor filtra ademas por `products.seller_id`, que ya
-- tiene su indice desde V9 (`products_seller`). No se crea uno compuesto entre las
-- dos tablas porque no puede existir: son dos tablas y el join las une por
-- `product_id`, que es la clave primaria de una y unica en la otra.
