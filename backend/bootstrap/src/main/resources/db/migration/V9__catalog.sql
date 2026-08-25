-- Esquema del catalogo y siembra del arbol de categorias. HU-007, rebanada A.
--
-- El arbol vive en docs/producto/categorias.md, que es su fuente de verdad. Se
-- siembra aqui, como las entidades financieras de V7, y por el mismo motivo: son
-- datos, van a crecer, y agregar una categoria no puede exigir desplegar codigo.
--
-- Recordatorio: una migracion aplicada no se edita nunca. Se crea la siguiente.

-- ---------------------------------------------------------------------------
-- Categorias
-- ---------------------------------------------------------------------------

CREATE TABLE categories (
    id        uuid PRIMARY KEY,
    -- Nulo en las familias, que son el primer nivel. Dos niveles y no tres:
    -- docs/producto/categorias.md.
    parent_id uuid REFERENCES categories (id),
    slug      text NOT NULL UNIQUE,

    -- El nombre visible si vive en la tabla y lo traduce el servidor por
    -- Accept-Language. Es la excepcion que contrato-api.md ya tenia escrita: si
    -- saliera por Transloco, agregar una categoria obligaria a desplegar el
    -- frontend.
    name_es text NOT NULL,
    name_en text NOT NULL,

    -- Plural: una categoria admite mas de una escala. Sin eje de genero, unos jeans
    -- se venden en talla numerica y en pulgadas de cintura, y partir la categoria en
    -- dos por eso seria meter el genero por la puerta de atras. El vendedor elige
    -- una de estas y la elegida queda en products.size_system.
    size_systems text[] NOT NULL DEFAULT '{}',

    -- Que medidas son obligatorias. Nulo en las familias: no se publica en una
    -- familia, se publica en una categoria suya.
    measurement_group text,

    -- RN-064. En falso, la unica condicion admisible es NEW. Es lo que impide
    -- vender tecnologia de segunda, y vive en el dato y no en una constante del
    -- codigo porque es una propiedad del arbol.
    allows_used boolean NOT NULL DEFAULT true,

    -- Una categoria que se retira se desactiva, no se borra: hay publicaciones
    -- apuntando a ella y borrarla las dejaria sin categoria.
    active   boolean  NOT NULL DEFAULT true,
    position smallint NOT NULL DEFAULT 0,

    created_at timestamptz NOT NULL DEFAULT now(),

    -- La misma forma que el slug valida en el dominio, repetida aqui porque es la
    -- unica que sigue en pie si algo escribe sin pasar por ahi.
    CONSTRAINT categories_slug_forma CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),

    CONSTRAINT categories_measurement_group_valid CHECK (
        measurement_group IS NULL OR measurement_group IN (
            'TOP', 'BOTTOM', 'FULL_BODY', 'FOOTWEAR', 'ACCESSORY_VOLUME', 'ACCESSORY_FLAT', 'DEVICE')),

    -- Una hoja sin grupo de medida no puede pedir las medidas que RN-021 exige, y
    -- una familia con grupo de medida promete algo en lo que no se publica.
    CONSTRAINT categories_hoja_completa CHECK (
        (parent_id IS NULL AND measurement_group IS NULL AND cardinality(size_systems) = 0)
        OR (parent_id IS NOT NULL AND measurement_group IS NOT NULL AND cardinality(size_systems) > 0)),

    -- Solo impide que una fila sea su propio padre. **No impide un tercer nivel**:
    -- un CHECK no puede consultar otra fila, asi que la profundidad no se acota
    -- aqui. Lo que la sostiene hoy es que el arbol se siembra en esta migracion y
    -- nada mas escribe en esta tabla. El dia que exista un panel que cree
    -- categorias, esa comprobacion es suya o de un trigger.
    CONSTRAINT categories_no_es_su_propio_padre CHECK (parent_id IS NULL OR parent_id <> id)
);

COMMENT ON TABLE categories IS
    'Arbol de dos niveles. Fuente de verdad: docs/producto/categorias.md';

COMMENT ON COLUMN categories.allows_used IS
    'RN-064: en falso solo admite condicion NEW. Toda la familia de tecnologia';

CREATE INDEX categories_parent_position ON categories (parent_id, position);

-- ---------------------------------------------------------------------------
-- Productos
-- ---------------------------------------------------------------------------

CREATE TABLE products (
    id          uuid PRIMARY KEY,
    -- Sin ON DELETE CASCADE a proposito: cerrar una cuenta anonimiza, no borra, y
    -- un producto que desaparece se lleva por delante el historial de venta.
    seller_id   uuid NOT NULL REFERENCES users (id),
    category_id uuid NOT NULL REFERENCES categories (id),

    title       text NOT NULL,
    description text NOT NULL,
    -- Opcional y texto libre: mucha prenda de segunda no tiene marca legible.
    brand       text,

    condition text NOT NULL,

    -- Se copia de la categoria al crear el producto: cambiar la categoria despues
    -- no puede reinterpretar una talla ya declarada.
    size_system text NOT NULL,
    size_value  text NOT NULL,

    -- Las claves las decide el measurement_group de la categoria, y el dominio
    -- comprueba que esten todas. jsonb y no columnas porque el conjunto depende del
    -- grupo: siete grupos con columnas serian veinte columnas casi siempre nulas.
    measurements jsonb NOT NULL,

    color text NOT NULL,

    -- RN-029: nunca punto flotante. Entero de pesos, que es como el peso colombiano
    -- se expresa en precios.
    price bigint NOT NULL,

    -- Envio. Obligatorios desde el principio: sin ellos RN-039 no puede cotizar en
    -- Fase 3 y toda publicacion existente quedaria sin cotizacion.
    weight_grams integer NOT NULL,
    length_cm    numeric(6, 1) NOT NULL,
    width_cm     numeric(6, 1) NOT NULL,
    height_cm    numeric(6, 1) NOT NULL,

    -- Los dos siguientes solo tienen sentido en tecnologia y van en nulo en moda.
    -- is_sealed habilita las imagenes de referencia y rebaja las tomas exigidas a
    -- cuatro (RN-065); la garantia la declara y la responde el vendedor (RN-067).
    is_sealed                    boolean,
    manufacturer_warranty_months smallint,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT products_condition_valid CHECK (
        condition IN ('NEW', 'LIKE_NEW', 'GOOD', 'WITH_FLAWS')),

    CONSTRAINT products_size_system_valid CHECK (
        size_system IN ('ALPHA', 'NUMERIC_CO', 'WAIST_INCHES', 'FOOTWEAR_CO', 'ONE_SIZE')),

    CONSTRAINT products_color_valid CHECK (
        color IN ('BLACK', 'WHITE', 'GRAY', 'BEIGE', 'BROWN', 'RED', 'PINK', 'ORANGE',
                  'YELLOW', 'GREEN', 'BLUE', 'PURPLE', 'GOLD', 'SILVER', 'MULTICOLOR')),

    -- RN-020 es un rango blando: fuera de el se publica y se marca para revision mas
    -- atenta. Lo que si es duro es que el precio sea positivo.
    CONSTRAINT products_price_positivo CHECK (price > 0),

    CONSTRAINT products_envio_positivo CHECK (
        weight_grams > 0 AND length_cm > 0 AND width_cm > 0 AND height_cm > 0),

    CONSTRAINT products_garantia_no_negativa CHECK (
        manufacturer_warranty_months IS NULL OR manufacturer_warranty_months >= 0),

    -- La garantia del fabricante solo existe donde hay empaque de fabrica.
    CONSTRAINT products_garantia_exige_tecnologia CHECK (
        manufacturer_warranty_months IS NULL OR is_sealed IS NOT NULL)
);

COMMENT ON COLUMN products.price IS
    'Pesos colombianos enteros. RN-029: jamas punto flotante';

COMMENT ON COLUMN products.is_sealed IS
    'Nulo en moda. RN-065: habilita las imagenes de referencia y baja a cuatro tomas';

CREATE INDEX products_seller ON products (seller_id);
CREATE INDEX products_category ON products (category_id);

-- ---------------------------------------------------------------------------
-- Publicaciones
-- ---------------------------------------------------------------------------

CREATE TABLE listings (
    id         uuid PRIMARY KEY,
    -- Una publicacion por producto. La publicacion se separa del producto para que
    -- el ciclo de moderacion no contamine los datos de la prenda (modelo-datos.md).
    product_id uuid NOT NULL UNIQUE REFERENCES products (id) ON DELETE CASCADE,

    status text NOT NULL,

    published_at timestamptz,
    sold_at      timestamptz,

    moderated_by     uuid REFERENCES users (id),
    moderated_at     timestamptz,
    rejection_reason text,
    rejection_note   text,

    -- Marca de revision mas atenta. No cambia el estado ni bloquea nada: solo hace
    -- que el moderador la vea destacada. La pone un precio fuera del rango de RN-020
    -- o una toma cargada desde galeria (criterio 8 de HU-003).
    requires_attention boolean NOT NULL DEFAULT false,
    attention_reason   text,

    -- Bloqueo optimista, y no es decorativo: el vendedor y el moderador escriben
    -- sobre la misma fila a la vez con normalidad.
    version bigint NOT NULL DEFAULT 0,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    -- Los siete estados del glosario. Sus transiciones son RN-061 y las comprueba el
    -- dominio: aqui solo se impide que exista un estado que nadie definio.
    CONSTRAINT listings_status_valid CHECK (
        status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'PAUSED', 'SOLD', 'ARCHIVED')),

    CONSTRAINT listings_rejection_reason_valid CHECK (
        rejection_reason IS NULL OR rejection_reason IN (
            'PHOTOS_UNUSABLE', 'PHOTOS_MISMATCH', 'MEASUREMENTS_UNRELIABLE', 'CONDITION_MISDECLARED',
            'PROHIBITED_ITEM', 'SUSPECTED_COUNTERFEIT', 'PRICE_OUT_OF_RANGE')),

    CONSTRAINT listings_attention_reason_valid CHECK (
        attention_reason IS NULL OR attention_reason IN ('PRICE_OUT_OF_RANGE', 'GALLERY_UPLOAD')),

    -- RN-022: un rechazo indica siempre el motivo. Un estado REJECTED sin motivo es
    -- una publicacion que el vendedor no sabe como corregir.
    CONSTRAINT listings_rechazo_con_motivo CHECK (
        status <> 'REJECTED' OR rejection_reason IS NOT NULL),

    CONSTRAINT listings_publicada_con_fecha CHECK (
        status <> 'PUBLISHED' OR published_at IS NOT NULL),

    CONSTRAINT listings_marca_con_motivo CHECK (
        requires_attention = false OR attention_reason IS NOT NULL)
);

-- El indice del catalogo: lo visible, lo mas reciente primero.
CREATE INDEX listings_status_published ON listings (status, published_at DESC);

-- La bandeja de moderacion de publicaciones, que llega en otra historia. Parcial
-- como el de seller_verifications y por lo mismo: la bandeja solo mira una porcion.
CREATE INDEX listings_pendientes ON listings (updated_at) WHERE status = 'PENDING_REVIEW';

-- ---------------------------------------------------------------------------
-- Imagenes
-- ---------------------------------------------------------------------------

CREATE TABLE product_images (
    id         uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,

    -- Distingue la toma que hizo el vendedor de la imagen de referencia que no hizo
    -- (RN-066). No es presentacion: el conteo de tomas obligatorias y el visor 360
    -- solo miran las del vendedor. Con una sola clase de imagen, una publicacion de
    -- ocho fotos del fabricante pasaria todas las validaciones.
    kind text NOT NULL,

    object_key text NOT NULL,

    -- 0 a 7 en las tomas del vendedor. En las de referencia solo ordena.
    position      smallint NOT NULL,
    angle_degrees smallint,
    is_canonical  boolean NOT NULL DEFAULT false,

    width        integer NOT NULL,
    height       integer NOT NULL,
    bytes        bigint  NOT NULL,
    content_type text    NOT NULL,

    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT product_images_kind_valid CHECK (kind IN ('SELLER_SHOT', 'REFERENCE')),

    -- La unicidad es por clase: una toma y una imagen de referencia pueden compartir
    -- numero sin pisarse.
    CONSTRAINT product_images_posicion_unica UNIQUE (product_id, kind, position),

    CONSTRAINT product_images_posicion_valida CHECK (position >= 0),

    -- RN-017: las tomas del vendedor van de 0 a 7 y su angulo es multiplo de 45. Una
    -- imagen de referencia no tiene angulo ni es canonica: no se tomo girando nada.
    CONSTRAINT product_images_toma_de_vendedor CHECK (
        kind <> 'SELLER_SHOT'
        OR (position <= 7 AND angle_degrees IS NOT NULL AND angle_degrees % 45 = 0)),

    CONSTRAINT product_images_referencia_sin_angulo CHECK (
        kind <> 'REFERENCE' OR (angle_degrees IS NULL AND is_canonical = false)),

    CONSTRAINT product_images_dimensiones_positivas CHECK (
        width > 0 AND height > 0 AND bytes > 0)
);

COMMENT ON COLUMN product_images.kind IS
    'SELLER_SHOT la tomo el vendedor; REFERENCE es del fabricante (RN-066)';

CREATE INDEX product_images_producto ON product_images (product_id, kind, position);

-- ---------------------------------------------------------------------------
-- Eventos de moderacion
-- ---------------------------------------------------------------------------

CREATE TABLE moderation_events (
    id         uuid PRIMARY KEY,
    listing_id uuid NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    -- Sin ON DELETE CASCADE: si la cuenta del moderador desaparece, el evento se
    -- queda. Una bitacora que se borra sola no es una bitacora.
    actor_id   uuid NOT NULL REFERENCES users (id),

    action text NOT NULL,
    reason text,
    notes  text,

    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT moderation_events_action_valid CHECK (
        action IN ('APPROVED', 'REJECTED', 'ARCHIVED')),

    -- RN-022 otra vez, ahora sobre el rastro: un rechazo registrado sin motivo no
    -- deja saber por que se rechazo.
    CONSTRAINT moderation_events_rechazo_con_motivo CHECK (
        action <> 'REJECTED' OR reason IS NOT NULL)
);

CREATE INDEX moderation_events_listing ON moderation_events (listing_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Siembra del arbol
-- ---------------------------------------------------------------------------
--
-- Seis familias y treinta y una categorias. Los identificadores se generan aqui:
-- gen_random_uuid() es v4 y estas filas son de configuracion, no de negocio, asi
-- que no les hace falta el orden temporal de un v7 (ADR-0015).
--
-- ATENCION: los nombres en ingles son una primera propuesta y estan sin revisar.
-- docs/producto/categorias.md los anota como pendientes. Manda que se entiendan, no
-- que traduzcan literal.

INSERT INTO categories (id, parent_id, slug, name_es, name_en, position) VALUES
    (gen_random_uuid(), NULL, 'tops',        'Parte superior', 'Tops',        1),
    (gen_random_uuid(), NULL, 'bottoms',     'Parte inferior', 'Bottoms',     2),
    (gen_random_uuid(), NULL, 'full-body',   'Prenda entera',  'Full body',   3),
    (gen_random_uuid(), NULL, 'footwear',    'Calzado',        'Footwear',    4),
    (gen_random_uuid(), NULL, 'accessories', 'Accesorios',     'Accessories', 5),
    (gen_random_uuid(), NULL, 'tech',        'Tecnologia',     'Tech',        6);

INSERT INTO categories (id, parent_id, slug, name_es, name_en, size_systems, measurement_group, allows_used, position)
SELECT gen_random_uuid(), familia.id, hoja.slug, hoja.name_es, hoja.name_en,
       hoja.size_systems, hoja.measurement_group, hoja.allows_used, hoja.position
FROM (VALUES
    -- 1. Parte superior
    ('tops', 'camisetas',           'Camisetas',               'T-shirts',             ARRAY['ALPHA'],               'TOP', true, 1),
    ('tops', 'camisas-y-blusas',    'Camisas y blusas',        'Shirts and blouses',   ARRAY['ALPHA', 'NUMERIC_CO'], 'TOP', true, 2),
    ('tops', 'tops-y-bodies',       'Tops y bodies',           'Tops and bodysuits',   ARRAY['ALPHA'],               'TOP', true, 3),
    ('tops', 'sueteres-y-buzos',    'Sueteres, buzos y sacos', 'Sweaters and hoodies', ARRAY['ALPHA'],               'TOP', true, 4),
    ('tops', 'blazers',             'Blazers',                 'Blazers',              ARRAY['ALPHA', 'NUMERIC_CO'], 'TOP', true, 5),
    ('tops', 'chaquetas-y-abrigos', 'Chaquetas y abrigos',     'Jackets and coats',    ARRAY['ALPHA'],               'TOP', true, 6),

    -- 2. Parte inferior
    ('bottoms', 'jeans',                 'Jeans',                 'Jeans',                ARRAY['WAIST_INCHES', 'NUMERIC_CO'], 'BOTTOM', true, 1),
    ('bottoms', 'pantalones',            'Pantalones',            'Trousers',             ARRAY['WAIST_INCHES', 'NUMERIC_CO'], 'BOTTOM', true, 2),
    ('bottoms', 'shorts-y-bermudas',     'Shorts y bermudas',     'Shorts',               ARRAY['WAIST_INCHES', 'NUMERIC_CO'], 'BOTTOM', true, 3),
    ('bottoms', 'faldas',                'Faldas',                'Skirts',               ARRAY['ALPHA', 'NUMERIC_CO'],        'BOTTOM', true, 4),
    ('bottoms', 'leggings-y-deportivos', 'Leggings y deportivos', 'Leggings and joggers', ARRAY['ALPHA'],                      'BOTTOM', true, 5),

    -- 3. Prenda entera
    ('full-body', 'vestidos',             'Vestidos',             'Dresses',   ARRAY['ALPHA', 'NUMERIC_CO'], 'FULL_BODY', true, 1),
    ('full-body', 'enterizos-y-overoles', 'Enterizos y overoles', 'Jumpsuits', ARRAY['ALPHA', 'NUMERIC_CO'], 'FULL_BODY', true, 2),
    ('full-body', 'trajes-de-bano',       'Trajes de bano',       'Swimwear',  ARRAY['ALPHA'],               'FULL_BODY', true, 3),

    -- 4. Calzado
    ('footwear', 'tenis',            'Tenis y deportivos', 'Sneakers',    ARRAY['FOOTWEAR_CO'], 'FOOTWEAR', true, 1),
    ('footwear', 'zapatos-formales', 'Zapatos formales',   'Dress shoes', ARRAY['FOOTWEAR_CO'], 'FOOTWEAR', true, 2),
    ('footwear', 'botas-y-botines',  'Botas y botines',    'Boots',       ARRAY['FOOTWEAR_CO'], 'FOOTWEAR', true, 3),
    ('footwear', 'sandalias',        'Sandalias',          'Sandals',     ARRAY['FOOTWEAR_CO'], 'FOOTWEAR', true, 4),

    -- 5. Accesorios
    ('accessories', 'bolsos-y-mochilas',    'Bolsos y mochilas',    'Bags and backpacks',  ARRAY['ONE_SIZE'],          'ACCESSORY_VOLUME', true, 1),
    ('accessories', 'correas',              'Correas y cinturones', 'Belts',               ARRAY['ALPHA', 'ONE_SIZE'], 'ACCESSORY_FLAT',   true, 2),
    ('accessories', 'bufandas-y-panoletas', 'Bufandas y panoletas', 'Scarves',             ARRAY['ONE_SIZE'],          'ACCESSORY_FLAT',   true, 3),
    ('accessories', 'sombreros-y-gorras',   'Sombreros y gorras',   'Hats and caps',       ARRAY['ALPHA', 'ONE_SIZE'], 'ACCESSORY_FLAT',   true, 4),
    ('accessories', 'gafas',                'Gafas',                'Eyewear',             ARRAY['ONE_SIZE'],          'ACCESSORY_FLAT',   true, 5),
    ('accessories', 'joyeria-y-relojes',    'Joyeria y relojes',    'Jewelry and watches', ARRAY['ONE_SIZE'],          'ACCESSORY_FLAT',   true, 6),

    -- 6. Tecnologia. allows_used en falso en las siete: RN-064.
    ('tech', 'celulares-y-tabletas',     'Celulares y tabletas',      'Phones and tablets', ARRAY['ONE_SIZE'], 'DEVICE', false, 1),
    ('tech', 'computadores',             'Computadores y portatiles', 'Computers',          ARRAY['ONE_SIZE'], 'DEVICE', false, 2),
    ('tech', 'televisores-y-monitores',  'Televisores y monitores',   'TVs and monitors',   ARRAY['ONE_SIZE'], 'DEVICE', false, 3),
    ('tech', 'audio',                    'Audio',                     'Audio',              ARRAY['ONE_SIZE'], 'DEVICE', false, 4),
    ('tech', 'consolas-y-videojuegos',   'Consolas y videojuegos',    'Consoles and games', ARRAY['ONE_SIZE'], 'DEVICE', false, 5),
    ('tech', 'camaras',                  'Camaras',                   'Cameras',            ARRAY['ONE_SIZE'], 'DEVICE', false, 6),
    ('tech', 'accesorios-de-tecnologia', 'Accesorios de tecnologia',  'Tech accessories',   ARRAY['ONE_SIZE'], 'DEVICE', false, 7)
) AS hoja (familia_slug, slug, name_es, name_en, size_systems, measurement_group, allows_used, position)
JOIN categories AS familia ON familia.slug = hoja.familia_slug AND familia.parent_id IS NULL;
