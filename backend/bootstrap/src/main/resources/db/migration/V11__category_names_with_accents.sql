-- Los nombres visibles de las categorias, con sus tildes y su ene.
--
-- V9 los sembro sin acentos: «Sueteres», «Camaras», «Trajes de bano»,
-- «Panoletas», «portatiles», «Joyeria», «tecnologia». Ese texto es visible al
-- comprador —es el nombre de la categoria en la ficha y en el catalogo—, asi que
-- escribirlo mal no es un detalle de estilo: es texto de cara al publico con
-- faltas de ortografia.
--
-- Va en una migracion nueva y no corrigiendo V9 porque V9 ya se aplico
-- (backend/CLAUDE.md). Solo toca `name_es`: el ingles de V9 esta bien.
--
-- Se filtra por `slug` y no por el nombre viejo: el slug es estable por
-- definicion y el nombre es justo lo que esta cambiando.

UPDATE categories SET name_es = 'Suéteres, buzos y sacos'  WHERE slug = 'sueteres-y-buzos';
UPDATE categories SET name_es = 'Trajes de baño'           WHERE slug = 'trajes-de-bano';
UPDATE categories SET name_es = 'Bufandas y pañoletas'     WHERE slug = 'bufandas-y-panoletas';
UPDATE categories SET name_es = 'Joyería y relojes'        WHERE slug = 'joyeria-y-relojes';
UPDATE categories SET name_es = 'Computadores y portátiles' WHERE slug = 'computadores';
UPDATE categories SET name_es = 'Cámaras'                  WHERE slug = 'camaras';
UPDATE categories SET name_es = 'Accesorios de tecnología' WHERE slug = 'accesorios-de-tecnologia';

-- Y las familias, que se sembraron en la misma migracion.
UPDATE categories SET name_es = 'Tecnología' WHERE slug = 'tech' AND parent_id IS NULL;
