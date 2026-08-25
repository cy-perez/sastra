-- Correcciones al esquema de catalogo, salidas de la revision de HU-007.
--
-- Tres cosas que V9 dejo mal. No se edita V9 porque una migracion no se edita
-- nunca: se crea la siguiente.

-- ---------------------------------------------------------------------------
-- 1. Las marcas de atencion son varias, no una
-- ---------------------------------------------------------------------------
--
-- Una publicacion puede estar barata Y tener tomas cargadas desde galeria. Con una
-- sola columna, la segunda marca pisaba a la primera segun el orden de los clics y
-- el moderador veia una de las dos. Los criterios 12 y 18 piden las dos.

ALTER TABLE listings ADD COLUMN attention_reasons text[] NOT NULL DEFAULT '{}';

UPDATE listings
SET attention_reasons = ARRAY[attention_reason]
WHERE attention_reason IS NOT NULL;

ALTER TABLE listings DROP CONSTRAINT listings_marca_con_motivo;
ALTER TABLE listings DROP CONSTRAINT listings_attention_reason_valid;
ALTER TABLE listings DROP COLUMN attention_reason;
ALTER TABLE listings DROP COLUMN requires_attention;

-- Requiere atencion es ahora "el arreglo no esta vacio". Un booleano aparte podia
-- contradecir a la lista, y dos verdades sobre lo mismo acaban discrepando.
ALTER TABLE listings ADD CONSTRAINT listings_attention_reasons_validas CHECK (
    attention_reasons <@ ARRAY['PRICE_OUT_OF_RANGE', 'GALLERY_UPLOAD']::text[]);

COMMENT ON COLUMN listings.attention_reasons IS
    'Vacio si no hay nada que mirar. Las marcas conviven: RN-020 y criterio 18 de HU-003';

-- ---------------------------------------------------------------------------
-- 2. La bitacora de moderacion no se borra en cascada
-- ---------------------------------------------------------------------------
--
-- V9 argumentaba, en la propia tabla, que actor_id no lleva cascada porque «una
-- bitacora que se borra sola no es una bitacora», y tres lineas mas arriba ponia
-- ON DELETE CASCADE en listing_id. Encadenado con la cascada de listings sobre
-- products, un DELETE de un producto se llevaba la publicacion y con ella todo su
-- rastro de moderacion.
--
-- Hoy nada borra productos, asi que esto es un riesgo latente y no un incidente. El
-- dia que llegue la supresion por Ley 1581 o una limpieza de borradores, se activaba
-- en silencio, que es la peor forma de activarse. RN-045: ninguna transicion se
-- pierde.

ALTER TABLE moderation_events DROP CONSTRAINT moderation_events_listing_id_fkey;
ALTER TABLE moderation_events ADD CONSTRAINT moderation_events_listing_id_fkey
    FOREIGN KEY (listing_id) REFERENCES listings (id) ON DELETE RESTRICT;

ALTER TABLE listings DROP CONSTRAINT listings_product_id_fkey;
ALTER TABLE listings ADD CONSTRAINT listings_product_id_fkey
    FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT;

-- product_images si conserva la cascada: una fila de imagen no significa nada sin su
-- producto, y no es un rastro de nada. Ojo: la cascada borra la fila, no el archivo
-- del almacen; eso lo hace el caso de uso.

-- ---------------------------------------------------------------------------
-- 3. Indice para el listado del vendedor
-- ---------------------------------------------------------------------------
--
-- `buscarDelVendedor` filtra por seller_id y ordena por created_at, y el unico indice
-- que habia servia al catalogo publico —status con published_at—, que no es esta
-- consulta.

CREATE INDEX products_seller_reciente ON products (seller_id, created_at DESC);
