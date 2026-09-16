-- =============================================================================
-- V3 — Los catálogos de SP: membresías, monedas, tasas de cambio, países,
-- tipos de documento y brokers.
--
-- Van antes que las personas porque `users` los referencia. Todos comparten
-- una decisión: la unicidad por nombre se comprueba SIN acentos ni mayúsculas
-- con `f_unaccent(lower(...))`, que es lo que impide dos «Colombia» con
-- distinta tilde. Y tres de ellos —países, tipos de documento y brokers— NO se
-- administran por API: se pueblan por migración (RN-SP-009, RN-SP-036,
-- RN-SP-039). La intercalación `es-x-icu` en los nombres es la que ordena los
-- listados: el orden alfabético depende de ella, no de la consulta.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Membresías: niveles de acceso en CADENA LINEAL (RN-SP-007). `level` es la
-- distancia hasta la cima —1 es la superior y crece hacia abajo— y `parent`
-- apunta a la de MENOR level. Las dos unicidades van DEFERRABLE porque insertar
-- un eslabón en medio reordena la cadena entera dentro de una transacción, y
-- `NULLS NOT DISTINCT` en el padre es lo que garantiza UNA sola cima. El suelo
-- es BECA (RN-SP-018), que toda persona recibe al nacer.
-- ---------------------------------------------------------------------------

CREATE TABLE memberships (
    id                   uuid         PRIMARY KEY,
    code                 varchar(50)  NOT NULL,
    name                 varchar(100) NOT NULL,
    description          text         NULL,
    parent_membership_id uuid         NULL,
    level                smallint     NOT NULL,
    color                varchar(6)   NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_memberships_code UNIQUE (code),
    CONSTRAINT uq_memberships_level UNIQUE (level) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_memberships_parent
        UNIQUE NULLS NOT DISTINCT (parent_membership_id) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_memberships_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_memberships_color_format
        CHECK (color ~ '^[0-9A-F]{6}$'),
    CONSTRAINT ck_memberships_description_length
        CHECK (description IS NULL OR length(description) <= 500),
    CONSTRAINT ck_memberships_level_positive
        CHECK (level >= 1),
    CONSTRAINT ck_memberships_parent_not_self
        CHECK (parent_membership_id IS NULL OR parent_membership_id <> id),
    CONSTRAINT fk_memberships_parent
        FOREIGN KEY (parent_membership_id) REFERENCES memberships (id) ON DELETE RESTRICT
);

COMMENT ON TABLE memberships IS
    'Niveles de acceso, en cadena lineal. El suelo es BECA (RN-SP-018); se llamó FREE hasta V79.';
COMMENT ON COLUMN memberships.parent_membership_id IS
    'Membresía de MAYOR nivel jerárquico, es decir, de level MENOR. Nulo solo en la superior.';
COMMENT ON COLUMN memberships.level IS
    'Distancia hasta la cima: 1 es la membresía superior y el número crece hacia abajo.';
COMMENT ON COLUMN memberships.color IS
    'Color del nivel para el frontend: seis dígitos hexadecimales en mayúsculas, sin #.';

CREATE UNIQUE INDEX uq_memberships_name  ON memberships (f_unaccent(lower(name)));
CREATE UNIQUE INDEX uq_memberships_color ON memberships (color);

-- ---------------------------------------------------------------------------
-- Monedas. Inmutables por API salvo `is_active` (RN-SP-010). Exactamente UNA
-- es la de casa (`is_default`), no puede desactivarse, y sus `decimal_places`
-- condicionan el redondeo de todo cálculo financiero: cero es legítimo y
-- distinto de «no se sabe».
-- ---------------------------------------------------------------------------

CREATE TABLE currencies (
    id             uuid         PRIMARY KEY,
    code           char(3)      NOT NULL,
    name           varchar(100) NOT NULL,
    symbol         varchar(10)  NULL,
    decimal_places smallint     NOT NULL DEFAULT 2,
    is_default     boolean      NOT NULL DEFAULT false,
    is_active      boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_currencies_code UNIQUE (code),
    CONSTRAINT uq_currencies_name UNIQUE (name),
    CONSTRAINT ck_currencies_code_format
        CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_currencies_decimal_places
        CHECK (decimal_places >= 0 AND decimal_places <= 4),
    CONSTRAINT ck_currencies_default_active
        CHECK (NOT is_default OR is_active)
);

COMMENT ON TABLE currencies IS
    'Catálogo de monedas. Inmutable por API salvo is_active (RN-SP-010, RF-SP-023).';
COMMENT ON COLUMN currencies.decimal_places IS
    'Condiciona el redondeo de todo cálculo financiero. Cero es legítimo y distinto de «no se sabe».';
COMMENT ON COLUMN currencies.is_default IS
    'Moneda con la que opera el sistema. Exactamente una fila la lleva a true, y no puede desactivarse.';

CREATE UNIQUE INDEX uq_currencies_single_default ON currencies (is_default) WHERE is_default;

-- ---------------------------------------------------------------------------
-- Tasas de cambio (RF-SP-047 a RF-SP-050): a cuánto se cambia una moneda por
-- otra, con vigencia. Al revés que las monedas, SE ADMINISTRAN por API porque
-- cambian, y cambian seguido. `price` es 18,8 porque NO es un importe —no está
-- en ninguna moneda— y COP→USD ronda 0,00024. La EXCLUDE es la regla: dos tasas
-- vivas y activas del mismo par no pueden solaparse en el tiempo.
-- ---------------------------------------------------------------------------

CREATE TABLE exchange_rates (
    id                 uuid          PRIMARY KEY,
    source_currency_id uuid          NOT NULL,
    target_currency_id uuid          NOT NULL,
    price              numeric(18,8) NOT NULL,
    valid_from         date          NOT NULL,
    valid_to           date          NULL,
    is_active          boolean       NOT NULL DEFAULT true,
    created_at         timestamptz   NOT NULL DEFAULT now(),
    updated_at         timestamptz   NOT NULL DEFAULT now(),
    deleted_at         timestamptz   NULL,

    CONSTRAINT ck_exchange_rates_monedas_distintas
        CHECK (source_currency_id <> target_currency_id),
    CONSTRAINT ck_exchange_rates_price_positive
        CHECK (price > 0),
    CONSTRAINT ck_exchange_rates_vigencia
        CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT fk_exchange_rates_source
        FOREIGN KEY (source_currency_id) REFERENCES currencies (id),
    CONSTRAINT fk_exchange_rates_target
        FOREIGN KEY (target_currency_id) REFERENCES currencies (id),
    CONSTRAINT uq_exchange_rates_vigente
        EXCLUDE USING gist (
            source_currency_id WITH =,
            target_currency_id WITH =,
            daterange(valid_from, valid_to, '[]') WITH &&)
        WHERE (is_active AND deleted_at IS NULL)
);

COMMENT ON TABLE exchange_rates IS
    'A cuanto se cambia una moneda por otra, con su vigencia. Se administra por API, al reves que el catalogo de monedas (RN-SP-010).';
COMMENT ON COLUMN exchange_rates.price IS
    'Cuantas unidades de destino da UNA de origen. 18,8 porque NO es un importe: no esta expresada en ninguna moneda y COP->USD ronda 0,00024.';
COMMENT ON COLUMN exchange_rates.valid_to IS
    'Nula: la tasa es VITALICIA. Vencida no es lo mismo que retirada.';

CREATE INDEX ix_exchange_rates_listado ON exchange_rates (valid_from DESC, id DESC);

-- ---------------------------------------------------------------------------
-- Países. Código ISO 3166-1 alfa-3 (COL, USA); inmutables salvo `is_active`
-- (RN-SP-009, RF-SP-022).
-- ---------------------------------------------------------------------------

CREATE TABLE countries (
    id         uuid         PRIMARY KEY,
    code       char(3)      NOT NULL,
    name       varchar(100) NOT NULL COLLATE "es-x-icu",
    is_active  boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_countries_code UNIQUE (code),
    CONSTRAINT ck_countries_code_format
        CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_countries_name_not_blank
        CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE countries IS
    'Catálogo de países. Inmutable salvo is_active (RN-SP-009, RF-SP-022).';
COMMENT ON COLUMN countries.code IS
    'ISO 3166-1 alfa-3 (COL, USA). Tres letras mayúsculas; ck_countries_code_format lo exige.';
COMMENT ON COLUMN countries.name IS
    'Intercalación es-x-icu: el orden alfabético del listado depende de ella, no de la consulta.';

CREATE UNIQUE INDEX uq_countries_name ON countries (f_unaccent(lower(name)));
CREATE INDEX ix_countries_busqueda
    ON countries USING gin (f_unaccent(lower(code)) gin_trgm_ops, f_unaccent(lower(name)) gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- Tipos de documento. SOLO LOS DE MAYOR DE EDAD: su contenido ES la validación
-- de mayoría de edad (RN-SP-035, RN-SP-036), y por eso no hay API de escritura
-- —un `document-types:create` la desactivaría sin cambiar ninguna regla—.
-- ---------------------------------------------------------------------------

CREATE TABLE document_types (
    id           uuid         PRIMARY KEY,
    abbreviation varchar(10)  NOT NULL,
    name         varchar(100) NOT NULL COLLATE "es-x-icu",
    is_active    boolean      NOT NULL DEFAULT true,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_document_types_abbreviation UNIQUE (abbreviation),
    CONSTRAINT ck_document_types_abbreviation_format
        CHECK (abbreviation ~ '^[A-Z][A-Z0-9]{0,9}$'),
    CONSTRAINT ck_document_types_name_not_blank
        CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE document_types IS
    'Documentos de identidad admitidos. SOLO LOS DE MAYOR DE EDAD: su contenido ES la validación (RN-SP-035, RN-SP-036).';
COMMENT ON COLUMN document_types.abbreviation IS
    'Es el código; no hay columna code aparte. Mayúsculas, sin espacios.';
COMMENT ON COLUMN document_types.is_active IS
    'La lee RF-SP-051 y solo la escribe una migración: RN-SP-036 deja este catálogo fuera de la API.';

CREATE UNIQUE INDEX uq_document_types_name ON document_types (f_unaccent(lower(name)));

-- ---------------------------------------------------------------------------
-- Brokers. El nombre comercial ES la clave de negocio: no hay columna `code`.
-- Se puebla por migración y solo se consulta (RN-SP-039).
-- ---------------------------------------------------------------------------

CREATE TABLE brokers (
    id         uuid         PRIMARY KEY,
    name       varchar(120) NOT NULL COLLATE "es-x-icu",
    is_active  boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE brokers IS
    'Catálogo de brokers. Se puebla por migración y solo se consulta (RN-SP-039).';
COMMENT ON COLUMN brokers.name IS
    'Nombre comercial. ES la clave de negocio: no hay columna `code` (08-09-2026).';

CREATE UNIQUE INDEX uq_brokers_name ON brokers (f_unaccent(lower(name)));
