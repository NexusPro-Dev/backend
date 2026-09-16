-- =============================================================================
-- V6 — Comisiones (CM): lo que paga cada producto a cada rol vendedor, y las
-- excepciones por persona.
--
-- Lo que este bloque decide y conviene no perder:
--   · La tasa de rol NACE PARA UN PRODUCTO y rige solo sobre él (RN-CM-021,
--     15-09-2026): `product_id` es obligatorio e inmutable, y hay como mucho
--     una viva por (producto, rol).
--   · La forma se DECLARA (`rate_type`) y no se deduce de qué columna está
--     llena: PORCENTAJE lleva `percentage`, FIJO lleva `fixed_amount`, y nunca
--     las dos (RN-CM-016). El fijo NO lleva moneda —toma la del producto que se
--     venda (RN-CM-017)— ni tope por arriba (RN-CM-018).
--   · La tasa PERSONALIZADA es una excepción por persona con vigencia, y rige
--     SOLO sobre los productos a los que se asocie (RN-CM-012, RN-CM-014).
-- =============================================================================

CREATE TABLE commission_rates (
    id           uuid          PRIMARY KEY,
    product_id   uuid          NOT NULL,
    role_id      uuid          NOT NULL,
    rate_type    varchar(20)   NOT NULL,
    percentage   numeric(5,2)  NULL,
    fixed_amount numeric(14,4) NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    deleted_at   timestamptz   NULL,

    CONSTRAINT ck_commission_rates_type
        CHECK (rate_type IN ('PORCENTAJE', 'FIJO')),
    CONSTRAINT ck_commission_rates_forma
        CHECK ((rate_type = 'PORCENTAJE' AND percentage IS NOT NULL AND fixed_amount IS NULL)
            OR (rate_type = 'FIJO' AND fixed_amount IS NOT NULL AND percentage IS NULL)),
    CONSTRAINT ck_commission_rates_percentage
        CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),
    CONSTRAINT ck_commission_rates_fixed
        CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    CONSTRAINT fk_commission_rates_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_commission_rates_role
        FOREIGN KEY (role_id) REFERENCES roles (id)
);

COMMENT ON TABLE commission_rates IS
    'Que paga CADA PRODUCTO a cada rol vendedor (RN-CM-021, 15-09-2026): la tasa nace con su producto y rige solo sobre el, desde que existe. Retiro logico con motivo.';
COMMENT ON COLUMN commission_rates.product_id IS
    'El producto que paga esta tasa (RN-CM-021). Obligatorio e inmutable: cambiar de producto es retirar la tasa y registrar otra. Un solo rol por producto entre las vivas (uq_commission_rates_product_role).';
COMMENT ON COLUMN commission_rates.rate_type IS
    'PORCENTAJE o FIJO. Se DECLARA, no se deduce de que columna este llena: sin ella una fila con las dos vacias no permitiria saber cual de las dos formas se quiso declarar. `RN-CM-016`.';
COMMENT ON COLUMN commission_rates.percentage IS
    'De 0 a 100. El cero es «no comisiona», y no es lo mismo que no tener tasa (RN-CM-007).';
COMMENT ON COLUMN commission_rates.fixed_amount IS
    'Importe fijo por venta. MISMA FORMA QUE `products.price` porque la escala real la decide la moneda (`currencies.decimal_places`, de 0 a 4). NO LLEVA MONEDA: toma la del producto que se venda (`RN-CM-017`). NO ESTA ACOTADO POR ARRIBA (`RN-CM-018`).';

CREATE UNIQUE INDEX uq_commission_rates_product_role
    ON commission_rates (product_id, role_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_commission_rates_role ON commission_rates (role_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- La excepción por persona. Sin producto aquí: se asocia en la tabla de abajo.
-- ---------------------------------------------------------------------------

CREATE TABLE user_commission_rates (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL,
    rate_type    varchar(20)   NOT NULL,
    percentage   numeric(5,2)  NULL,
    fixed_amount numeric(14,4) NULL,
    valid_from   date          NOT NULL,
    valid_to     date          NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    deleted_at   timestamptz   NULL,

    CONSTRAINT ck_user_commission_rates_type
        CHECK (rate_type IN ('PORCENTAJE', 'FIJO')),
    CONSTRAINT ck_user_commission_rates_forma
        CHECK ((rate_type = 'PORCENTAJE' AND percentage IS NOT NULL AND fixed_amount IS NULL)
            OR (rate_type = 'FIJO' AND fixed_amount IS NOT NULL AND percentage IS NULL)),
    CONSTRAINT ck_user_commission_rates_percentage
        CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),
    CONSTRAINT ck_user_commission_rates_fixed
        CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    CONSTRAINT ck_user_commission_rates_vigencia
        CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT fk_user_commission_rates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

COMMENT ON TABLE user_commission_rates IS
    'Excepcion por persona. Rige SOLO donde este asociada (RN-CM-012, 11-09-2026). RN-CM-006 ya no vive aqui: cruza dos tablas.';
COMMENT ON COLUMN user_commission_rates.valid_to IS
    'Nulo: rige indefinidamente. Vencida no es lo mismo que retirada.';
COMMENT ON COLUMN user_commission_rates.rate_type IS
    'PORCENTAJE o FIJO, igual que en el catalogo por rol. `RN-CM-016`.';
COMMENT ON COLUMN user_commission_rates.fixed_amount IS
    'Importe fijo por venta, SIN MONEDA: se interpreta en la moneda del producto que se venda (`RN-CM-017`).';

-- ---------------------------------------------------------------------------
-- Sobre qué productos rige una tasa personalizada (RN-CM-014). Asociación
-- pura: la pareja es la clave y se borra físicamente (Art. V.13).
-- ---------------------------------------------------------------------------

CREATE TABLE user_commission_rate_products (
    user_commission_rate_id uuid        NOT NULL,
    product_id              uuid        NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_commission_rate_products PRIMARY KEY (user_commission_rate_id, product_id),
    CONSTRAINT fk_user_commission_rate_products_rate
        FOREIGN KEY (user_commission_rate_id) REFERENCES user_commission_rates (id),
    CONSTRAINT fk_user_commission_rate_products_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);

COMMENT ON TABLE user_commission_rate_products IS
    'Sobre que productos rige una tasa personalizada (RN-CM-014, 11-09-2026).';

CREATE INDEX ix_user_commission_rate_products_producto ON user_commission_rate_products (product_id);
