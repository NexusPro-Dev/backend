-- =============================================================================
-- V7 — Movimientos (MV): el libro de hechos económicos, sus líneas y los
-- métodos de pago.
--
-- Lo que este bloque decide y conviene no perder:
--   · Un movimiento NO se edita ni se borra (RN-MV-001): sin updated_at ni
--     deleted_at. Lo que cambia es su `status`, y anular es un estado.
--   · Las líneas COPIAN del catálogo lo que se vendió —precio y vigencia— y no
--     lo releen nunca (RN-MV-002): corregir un producto no reescribe facturas.
--   · El vendedor sale de quien compra y SE CONGELA (RN-MV-003); nulo es una
--     venta sin atribución, que no comisiona a nadie.
--   · Los catálogos de tipos y de métodos de pago se siembran por migración;
--     la visibilidad separa lo que se elige (PUBLICO) de lo que pone el
--     sistema (INTERNO), y no es `is_active` con otro nombre (RN-MV-023).
-- =============================================================================

CREATE TABLE movement_types (
    id         uuid         PRIMARY KEY,
    code       varchar(50)  NOT NULL,
    name       varchar(100) NOT NULL,
    prefix     varchar(6)   NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_movement_types_code UNIQUE (code),
    CONSTRAINT ck_movement_types_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_movement_types_prefix
        CHECK (prefix ~ '^[A-Z][A-Z0-9]*$')
);

COMMENT ON TABLE movement_types IS
    'Catalogo de tipos de movimiento. Inmutable por API (RN-MV-017). Hoy una sola fila: VENTA.';
COMMENT ON COLUMN movement_types.prefix IS
    'Prefijo del codigo de comprobante (RN-MV-016): VTA-20260904-K7M2QX.';

CREATE TABLE payment_methods (
    id         uuid         PRIMARY KEY,
    code       varchar(50)  NOT NULL,
    name       varchar(100) NOT NULL,
    is_active  boolean      NOT NULL DEFAULT true,
    visibility varchar(20)  NOT NULL DEFAULT 'PUBLICO',
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_methods_code UNIQUE (code),
    CONSTRAINT ck_payment_methods_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_payment_methods_visibility
        CHECK (visibility IN ('PUBLICO', 'INTERNO'))
);

COMMENT ON TABLE payment_methods IS
    'Catalogo de metodos de pago. Sembrado por migracion; sin API de administracion todavia.';
COMMENT ON COLUMN payment_methods.is_active IS
    'RN-MV-018: un metodo desactivado no invalida lo ya pagado con el, pero no sirve para vender hoy.';
COMMENT ON COLUMN payment_methods.visibility IS
    'PUBLICO se ofrece en el selector; INTERNO sirve para pagar y NADIE lo elige — lo pone el sistema (RN-MV-023). No es is_active con otro nombre.';

-- Dónde NO vale cada método (RN-MV-019). Se publica y no se comprueba:
-- registrar una venta no mira el país. Un método sin filas vale en todos.
CREATE TABLE payment_method_exclusions (
    payment_method_id uuid        NOT NULL,
    country_id        uuid        NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_payment_method_exclusions PRIMARY KEY (payment_method_id, country_id),
    CONSTRAINT fk_payment_method_exclusions_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id) ON DELETE CASCADE,
    CONSTRAINT fk_payment_method_exclusions_country
        FOREIGN KEY (country_id) REFERENCES countries (id) ON DELETE RESTRICT
);

COMMENT ON TABLE payment_method_exclusions IS
    'RN-MV-019: donde NO vale cada metodo de pago. SE PUBLICA Y NO SE COMPRUEBA: registrar una venta no mira el pais.';
COMMENT ON COLUMN payment_method_exclusions.country_id IS
    'Primera clave foranea entrante de `countries` (04-09-2026). Un metodo sin filas vale en todos los paises.';

-- ---------------------------------------------------------------------------
-- El libro. `occurred_at` es cuándo ocurrió la venta y de ahí sale el día del
-- código (RN-MV-016), cortado en America/Bogota; `created_at` es cuándo se
-- registró. `confirmed_at` va exactamente cuando el estado es CONFIRMADA.
-- ---------------------------------------------------------------------------

CREATE TABLE movements (
    id                uuid          PRIMARY KEY,
    movement_type_id  uuid          NOT NULL,
    client_id         uuid          NOT NULL,
    seller_id         uuid          NULL,
    payment_method_id uuid          NOT NULL,
    currency_id       uuid          NOT NULL,
    code              varchar(30)   NOT NULL,
    status            varchar(20)   NOT NULL,
    total_amount      numeric(14,2) NOT NULL,
    discount_amount   numeric(14,2) NOT NULL DEFAULT 0,
    payable_amount    numeric(14,2) NOT NULL,
    occurred_at       timestamptz   NOT NULL,
    confirmed_at      timestamptz   NULL,
    reference_id      uuid          NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_movements_code UNIQUE (code),
    CONSTRAINT ck_movements_status
        CHECK (status IN ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'ANULADA')),
    CONSTRAINT ck_movements_amounts
        CHECK (total_amount >= 0 AND discount_amount >= 0 AND payable_amount >= 0),
    CONSTRAINT ck_movements_payable
        CHECK (payable_amount = total_amount - discount_amount),
    CONSTRAINT ck_movements_confirmed
        CHECK ((status = 'CONFIRMADA') = (confirmed_at IS NOT NULL)),
    CONSTRAINT fk_movements_type
        FOREIGN KEY (movement_type_id) REFERENCES movement_types (id) ON DELETE RESTRICT,
    CONSTRAINT fk_movements_client
        FOREIGN KEY (client_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_movements_seller
        FOREIGN KEY (seller_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_movements_payment_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_movements_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id) ON DELETE RESTRICT
);

COMMENT ON TABLE movements IS
    'El libro de hechos economicos. NO lleva updated_at ni deleted_at (RN-MV-001): una venta no se edita y no se borra.';
COMMENT ON COLUMN movements.seller_id IS
    'RN-MV-003: el vendedor sale de quien compra y SE CONGELA. NULL = venta sin atribucion, que NO COMISIONA A NADIE.';
COMMENT ON COLUMN movements.occurred_at IS
    'Cuando ocurrio la venta. De aqui sale el dia del codigo (RN-MV-016), no de created_at.';
COMMENT ON COLUMN movements.reference_id IS
    'Columna RESERVADA, sin FK y sin regla que la gobierne. Pendiente de definir (mv.md §7.1).';

-- Las dos mitades de «mis movimientos» (RF-MV-008), ya ordenadas; la del
-- vendedor es parcial porque una venta sin vendedor nunca está en esa respuesta.
CREATE INDEX ix_movements_client ON movements (client_id, occurred_at DESC);
CREATE INDEX ix_movements_seller ON movements (seller_id, occurred_at DESC) WHERE seller_id IS NOT NULL;
COMMENT ON INDEX ix_movements_client IS
    'RF-MV-008: la mitad «lo que compre» de los movimientos propios, ya ordenada.';
COMMENT ON INDEX ix_movements_seller IS
    'RF-MV-008: la mitad «lo que vendi». Parcial: una venta sin vendedor nunca esta en esa respuesta.';

-- ---------------------------------------------------------------------------
-- Las líneas, con lo que se COPIÓ del catálogo (RN-MV-002). Un producto una
-- vez por movimiento; la cantidad existe pero hoy es siempre uno.
-- ---------------------------------------------------------------------------

CREATE TABLE movement_details (
    id            uuid          PRIMARY KEY,
    movement_id   uuid          NOT NULL,
    product_id    uuid          NOT NULL,
    quantity      integer       NOT NULL,
    unit_price    numeric(14,2) NOT NULL,
    line_amount   numeric(14,2) NOT NULL,
    validity_days integer       NULL,

    CONSTRAINT uq_movement_details_producto UNIQUE (movement_id, product_id),
    CONSTRAINT ck_movement_details_quantity
        CHECK (quantity > 0),
    CONSTRAINT ck_movement_details_validity
        CHECK (validity_days IS NULL OR validity_days > 0),
    CONSTRAINT fk_movement_details_movement
        FOREIGN KEY (movement_id) REFERENCES movements (id) ON DELETE CASCADE,
    CONSTRAINT fk_movement_details_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT
);

COMMENT ON TABLE movement_details IS
    'Las lineas de la venta, con LO QUE SE COPIO del catalogo (RN-MV-002).';
COMMENT ON COLUMN movement_details.unit_price IS
    'COPIA del precio del catalogo en el momento de la venta. No se relee nunca (RN-MV-002).';
COMMENT ON COLUMN movement_details.validity_days IS
    'COPIA de la vigencia en dias. NULL significa que lo adquirido no caduca (RN-PM-015).';

CREATE INDEX idx_movement_details_movement ON movement_details (movement_id);
