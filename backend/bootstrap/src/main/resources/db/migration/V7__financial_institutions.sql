-- Catalogo de entidades donde un vendedor puede recibir su dinero. HU-002.
--
-- Es una tabla y no una enumeracion del codigo por dos motivos. El primero: las
-- entidades se fusionan, se renombran y aparecen nuevas, y agregar un banco no
-- puede exigir desplegar codigo. El segundo: la Fase 3 necesita el codigo de cada
-- una para el desembolso, y ese codigo tiene que vivir donde se pueda completar sin
-- tocar el dominio.
--
-- Lo que se guarda en la fila del vendedor es el `code`, nunca el nombre: el nombre
-- es lo que cambia.

CREATE TABLE financial_institutions (
    code   text        PRIMARY KEY,
    name   text        NOT NULL,
    -- BANK admite cuenta de ahorros y corriente; WALLET solo deposito
    -- electronico. La distincion no es cosmetica: el desembolso de la Fase 3
    -- necesita el tipo correcto o el dinero no llega.
    kind   text        NOT NULL,
    -- Una entidad que deja de operar se desactiva, no se borra: hay filas de
    -- vendedores apuntando a ella y borrarla dejaria una cuenta sin entidad.
    active boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),

    -- La misma forma que BankCode valida en el dominio. Repetida aqui porque es la
    -- unica que sigue en pie si algo escribe sin pasar por ahi.
    CONSTRAINT financial_institutions_code_forma CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT financial_institutions_kind_valid CHECK (kind IN ('BANK', 'WALLET'))
);

COMMENT ON COLUMN financial_institutions.name IS
    'Nombre propio de la entidad. No se traduce: es el mismo en los dos idiomas';

COMMENT ON COLUMN financial_institutions.kind IS
    'BANK admite ahorros y corriente; WALLET solo deposito electronico (HU-002)';

-- Bancos. Scotiabank y Colpatria van como una sola entrada porque son la misma
-- entidad: dos opciones que son una obligan a la persona a adivinar.
INSERT INTO financial_institutions (code, name, kind) VALUES
    ('bancolombia',          'Bancolombia',          'BANK'),
    ('davivienda',           'Banco Davivienda',     'BANK'),
    ('banco-de-bogota',      'Banco de Bogotá',      'BANK'),
    ('bbva-colombia',        'BBVA Colombia',        'BANK'),
    ('av-villas',            'Banco AV Villas',      'BANK'),
    ('banco-caja-social',    'Banco Caja Social',    'BANK'),
    ('scotiabank-colpatria', 'Scotiabank Colpatria', 'BANK'),
    ('banco-popular',        'Banco Popular',        'BANK'),
    ('banco-de-occidente',   'Banco de Occidente',   'BANK'),
    ('gnb-sudameris',        'Banco GNB Sudameris',  'BANK'),
    ('banco-agrario',        'Banco Agrario',        'BANK'),
    ('itau',                 'Banco Itaú',           'BANK'),
    ('citibank',             'Citibank',             'BANK'),
    ('banco-pichincha',      'Banco Pichincha',      'BANK'),
    ('banco-santander',      'Banco Santander',      'BANK'),
    ('bancoomeva',           'Bancoomeva',           'BANK'),
    ('lulo-bank',            'Lulo Bank',            'BANK'),
    ('banco-falabella',      'Banco Falabella',      'BANK'),
    ('banco-serfinanza',     'Banco Serfinanza',     'BANK'),
    ('bancamia',             'Bancamía',             'BANK'),
    ('banco-mundo-mujer',    'Banco Mundo Mujer',    'BANK');

-- Billeteras y depositos electronicos. Van como WALLET porque lo que tienen no es
-- una cuenta de ahorros aunque se use igual.
INSERT INTO financial_institutions (code, name, kind) VALUES
    ('daviplata', 'DaviPlata', 'WALLET'),
    ('dale',      'Dale',      'WALLET'),
    ('rappipay',  'RappiPay',  'WALLET'),
    ('nequi',     'Nequi',     'WALLET'),
    ('movii',     'Movii',     'WALLET'),
    ('tpaga',     'Tpaga',     'WALLET'),
    ('uala',      'Ualá',      'WALLET');
