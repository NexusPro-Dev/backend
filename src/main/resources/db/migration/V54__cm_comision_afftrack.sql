-- ---------------------------------------------------------------------------
-- V54 — La comisión afftrack (RF-CM-015 a RF-CM-021; requirements/cm.md
-- v0.22.0 §5.8 y §7.9 a §7.12).
--
-- Escalones por FTD —una línea de venta BECA → BECA activada (RN-CM-036)—, de
-- rol y de persona, que cada cierre compara con los FTD que reunió cada persona
-- y su red: se paga el mayor límite alcanzado, una vez, y lo que sobra pasa al
-- cierre siguiente (RN-CM-041). Lo pagado es una fila más de commissions, que
-- desde hoy dice de qué clase es: POR_VENTA o POR_AFFTRACK (RN-CM-044).
--
-- Las cuatro tablas, el cambio de commissions y los nueve permisos del
-- submódulo nacen juntos, como en V51
-- (specs/cm/015-registrar-comision-afftrack-rol/plan.md §2).
-- ---------------------------------------------------------------------------

-- Los escalones de rol de cada producto FTD (§7.9). Calca commission_rates sin
-- la forma: un escalón siempre paga un importe por FTD (RN-CM-038).
CREATE TABLE afftrack_rates (
    id             uuid          PRIMARY KEY,
    product_id     uuid          NOT NULL,
    role_id        uuid          NOT NULL,
    threshold      integer       NOT NULL,
    amount_per_ftd numeric(14,4) NOT NULL,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    deleted_at     timestamptz   NULL,

    CONSTRAINT ck_afftrack_rates_threshold CHECK (threshold > 0),
    -- Por arriba no lo acota nada: no hay precio contra el que medir (RN-CM-038).
    CONSTRAINT ck_afftrack_rates_amount CHECK (amount_per_ftd >= 0),
    -- Que el producto sea FTD y el rol VENDEDOR vive en el dominio (RN-CM-037).
    CONSTRAINT fk_afftrack_rates_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_afftrack_rates_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- RN-CM-039: un valor por límite. Parcial, porque un escalón retirado no estorba
-- al que lo sustituye.
CREATE UNIQUE INDEX uq_afftrack_rates_product_role_threshold
    ON afftrack_rates (product_id, role_id, threshold) WHERE deleted_at IS NULL;
CREATE INDEX ix_afftrack_rates_product_role
    ON afftrack_rates (product_id, role_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE afftrack_rates IS
    'RF-CM-015: un escalón de la escala afftrack de un rol sobre un producto FTD. Al reunir threshold FTD en un cierre se pagan threshold × amount_per_ftd (RN-CM-041). Retiro lógico con motivo.';
COMMENT ON COLUMN afftrack_rates.amount_per_ftd IS
    'Valor por FTD, en la moneda del producto: no la declara (RN-CM-017). La forma de products.price porque la escala la decide la moneda.';

-- Los escalones de una persona (§7.10). Calca user_commission_rates.
CREATE TABLE user_afftrack_rates (
    id             uuid          PRIMARY KEY,
    user_id        uuid          NOT NULL,
    product_id     uuid          NOT NULL,
    threshold      integer       NOT NULL,
    amount_per_ftd numeric(14,4) NOT NULL,
    valid_from     date          NOT NULL,
    valid_to       date          NULL,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    deleted_at     timestamptz   NULL,

    CONSTRAINT ck_user_afftrack_rates_threshold CHECK (threshold > 0),
    CONSTRAINT ck_user_afftrack_rates_amount CHECK (amount_per_ftd >= 0),
    -- La rama IS NULL va delante: un CHECK que evalúa a NULL acepta la fila.
    CONSTRAINT ck_user_afftrack_rates_vigencia CHECK (valid_to IS NULL OR valid_to >= valid_from),
    -- RN-CM-039: la escala de una persona son varios escalones vigentes a la vez;
    -- lo que no se repite es el mismo límite el mismo día. No trae nombre al
    -- violarse: se traduce por estado SQL, 23P01 y 40P01.
    CONSTRAINT ex_user_afftrack_rates_vigente EXCLUDE USING gist (
        user_id WITH =,
        product_id WITH =,
        threshold WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&) WHERE (deleted_at IS NULL),
    CONSTRAINT fk_user_afftrack_rates_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_afftrack_rates_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX ix_user_afftrack_rates_user_product
    ON user_afftrack_rates (user_id, product_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE user_afftrack_rates IS
    'RF-CM-019: un escalón de la escala afftrack propia de una persona. Si tiene al menos uno vigente el día del cierre, su escala SUSTITUYE ENTERA la de su rol (RN-CM-039).';

-- Lo que cada cierre hizo con los FTD de cada persona y producto (§7.11). Es
-- también donde vive el remanente: la siguiente liquidación lee carried_out de
-- la más reciente. Hecho consumado (RN-CM-029): sin updated_at ni deleted_at.
CREATE TABLE afftrack_settlements (
    id                uuid        PRIMARY KEY,
    closing_id        uuid        NOT NULL,
    user_id           uuid        NOT NULL,
    product_id        uuid        NOT NULL,
    carried_in        integer     NOT NULL,
    new_ftds          integer     NOT NULL,
    paid_ftds         integer     NOT NULL,
    carried_out       integer     NOT NULL,
    source            varchar(20) NULL,
    threshold_rate_id uuid        NULL,
    created_at        timestamptz NOT NULL,

    CONSTRAINT uq_afftrack_settlements_closing_user_product UNIQUE (closing_id, user_id, product_id),
    -- RN-CM-041, declarada: una liquidación que pierde o inventa FTD no se escribe.
    CONSTRAINT ck_afftrack_settlements_counts CHECK (
        carried_in >= 0 AND new_ftds >= 0 AND paid_ftds >= 0 AND carried_out >= 0
        AND carried_out = carried_in + new_ftds - paid_ftds),
    -- Pagar sin escalón, o tener escalón y no pagar, no existe.
    CONSTRAINT ck_afftrack_settlements_threshold CHECK (
        (paid_ftds = 0) = (threshold_rate_id IS NULL)
        AND (threshold_rate_id IS NULL) = (source IS NULL)),
    CONSTRAINT ck_afftrack_settlements_source CHECK (
        source IS NULL OR source IN ('PERSONALIZADA', 'ROL')),
    CONSTRAINT fk_afftrack_settlements_closing FOREIGN KEY (closing_id) REFERENCES commission_closings (id),
    CONSTRAINT fk_afftrack_settlements_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_afftrack_settlements_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX ix_afftrack_settlements_user_product_created
    ON afftrack_settlements (user_id, product_id, created_at DESC);

COMMENT ON TABLE afftrack_settlements IS
    'RF-CM-020: la liquidación afftrack de una persona y un producto en un cierre, también la que no pagó nada. carried_out es el remanente para el siguiente cierre.';
COMMENT ON COLUMN afftrack_settlements.threshold_rate_id IS
    'El escalón exacto que se pagó. Sin clave foránea: apunta a afftrack_rates o a user_afftrack_rates según source.';

-- Qué FTD se le contaron a quién (§7.12). Un FTD se cuenta una vez por persona
-- en la historia (RN-CM-040).
CREATE TABLE afftrack_ftds (
    movement_detail_id uuid        NOT NULL,
    user_id            uuid        NOT NULL,
    chain_level        smallint    NOT NULL,
    settlement_id      uuid        NOT NULL,
    created_at         timestamptz NOT NULL,

    CONSTRAINT pk_afftrack_ftds PRIMARY KEY (movement_detail_id, user_id),
    CONSTRAINT ck_afftrack_ftds_chain_level CHECK (chain_level >= 0),
    -- Como fk_commissions_detail: toda suite que limpie movements limpia antes esta.
    CONSTRAINT fk_afftrack_ftds_detail
        FOREIGN KEY (movement_detail_id) REFERENCES movement_details (id) ON DELETE RESTRICT,
    CONSTRAINT fk_afftrack_ftds_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_afftrack_ftds_settlement
        FOREIGN KEY (settlement_id) REFERENCES afftrack_settlements (id) ON DELETE CASCADE
);

CREATE INDEX ix_afftrack_ftds_settlement ON afftrack_ftds (settlement_id);

COMMENT ON COLUMN afftrack_ftds.chain_level IS
    '0 si el FTD es de la persona; 1 si es de alguien que depende directamente de ella, y así hacia abajo (RN-CM-042). No decide nada: explica de dónde salen sus FTD.';

-- ---------------------------------------------------------------------------
-- commissions guarda dos clases (§7.6, RN-CM-044). El valor por omisión solo
-- rellena las filas que existen —todas de venta— y se retira acto seguido: que
-- ninguna inserción futura se ahorre declarar la clase.
-- ---------------------------------------------------------------------------

ALTER TABLE commissions
    ADD COLUMN commission_kind        varchar(20) NOT NULL DEFAULT 'POR_VENTA',
    ADD COLUMN afftrack_settlement_id uuid        NULL;
ALTER TABLE commissions ALTER COLUMN commission_kind DROP DEFAULT;

ALTER TABLE commissions
    ALTER COLUMN movement_detail_id DROP NOT NULL,
    ALTER COLUMN chain_level        DROP NOT NULL,
    ALTER COLUMN unit_price         DROP NOT NULL;

ALTER TABLE commissions
    ADD CONSTRAINT ck_commissions_kind_values CHECK (commission_kind IN ('POR_VENTA', 'POR_AFFTRACK')),
    -- Lo que cada clase exige y prohíbe. ck_commissions_amount y
    -- ck_commissions_chain_level evalúan a NULL con su operando nulo y aceptan,
    -- que es lo correcto para la fila afftrack: la presencia la exige esta.
    ADD CONSTRAINT ck_commissions_kind CHECK (
        (commission_kind = 'POR_VENTA'
            AND movement_detail_id IS NOT NULL AND chain_level IS NOT NULL
            AND unit_price IS NOT NULL AND afftrack_settlement_id IS NULL)
        OR (commission_kind = 'POR_AFFTRACK'
            AND movement_detail_id IS NULL AND chain_level IS NULL
            AND unit_price IS NULL AND afftrack_settlement_id IS NOT NULL
            AND rate_type = 'FIJO')),
    ADD CONSTRAINT fk_commissions_settlement
        FOREIGN KEY (afftrack_settlement_id) REFERENCES afftrack_settlements (id),
    -- Una liquidación paga un escalón, una vez: a lo sumo una fila.
    ADD CONSTRAINT uq_commissions_settlement UNIQUE (afftrack_settlement_id);

COMMENT ON COLUMN commissions.commission_kind IS
    'RN-CM-044: POR_VENTA —una línea, un nivel, una tasa— o POR_AFFTRACK —un escalón pagado en un cierre: sin línea ni nivel; quantity son los FTD pagados y fixed_amount el valor por FTD—.';
COMMENT ON COLUMN commissions.rate_id IS
    'La tasa o el escalón exacto (RN-CM-008). Sin clave foránea: según commission_kind y source apunta a commission_rates, user_commission_rates, afftrack_rates o user_afftrack_rates.';

-- ---------------------------------------------------------------------------
-- Los nueve permisos de la comisión afftrack (requirements/cm.md §6; security.md
-- v0.82.0 §4.4). Serie de CM, a continuación del 000019 de V51. A SUPERADMIN y
-- ADMIN: son configuración y lectura de administración, y lo propio no entra.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f7a0-9000-7009-9c4f-5e7ad6000020', 'afftrack-rates:read', 'afftrack-rates', 'read',
 'Consultar las comisiones afftrack de rol',
 'Listar los escalones afftrack de cada rol sobre cada producto FTD, por GET /afftrack-rates (RF-CM-016).'),
('01a0f7a0-9000-700a-9c4f-5e7ad6000021', 'afftrack-rates:create', 'afftrack-rates', 'create',
 'Registrar una comisión afftrack de rol',
 'Registrar un escalón —límite de FTD y valor por FTD— de un rol vendedor sobre un producto FTD, por POST /afftrack-rates (RF-CM-015).'),
('01a0f7a0-9000-700b-9c4f-5e7ad6000022', 'afftrack-rates:update', 'afftrack-rates', 'update',
 'Corregir una comisión afftrack de rol',
 'Corregir el límite o el valor de un escalón de rol, por PATCH /afftrack-rates/{id} (RF-CM-017).'),
('01a0f7a0-9000-700c-9c4f-5e7ad6000023', 'afftrack-rates:delete', 'afftrack-rates', 'delete',
 'Retirar una comisión afftrack de rol',
 'Retirar un escalón de rol con motivo, por POST /afftrack-rates/{id}/deletion (RF-CM-018).'),
('01a0f7a0-9000-700d-9c4f-5e7ad6000024', 'user-afftrack-rates:read', 'user-afftrack-rates', 'read',
 'Consultar las comisiones afftrack de persona',
 'Listar los escalones afftrack propios de cada persona, por GET /user-afftrack-rates (RF-CM-019).'),
('01a0f7a0-9000-700e-9c4f-5e7ad6000025', 'user-afftrack-rates:create', 'user-afftrack-rates', 'create',
 'Registrar una comisión afftrack de persona',
 'Registrar un escalón de una persona sobre un producto FTD, con su vigencia, por POST /user-afftrack-rates (RF-CM-019).'),
('01a0f7a0-9000-700f-9c4f-5e7ad6000026', 'user-afftrack-rates:update', 'user-afftrack-rates', 'update',
 'Corregir una comisión afftrack de persona',
 'Corregir el límite, el valor o el fin de vigencia de un escalón de persona, por PATCH /user-afftrack-rates/{id} (RF-CM-019).'),
('01a0f7a0-9000-7010-9c4f-5e7ad6000027', 'user-afftrack-rates:delete', 'user-afftrack-rates', 'delete',
 'Retirar una comisión afftrack de persona',
 'Retirar un escalón de persona con motivo, por POST /user-afftrack-rates/{id}/deletion (RF-CM-019).'),
('01a0f7a0-9000-7011-9c4f-5e7ad6000028', 'afftrack-settlements:read', 'afftrack-settlements', 'read',
 'Consultar las liquidaciones afftrack',
 'Listar lo que cada cierre hizo con los FTD de cada persona —pagados y remanente—, por GET /afftrack-settlements (RF-CM-021).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
  CROSS JOIN permissions p
 WHERE p.id IN ('01a0f7a0-9000-7009-9c4f-5e7ad6000020', '01a0f7a0-9000-700a-9c4f-5e7ad6000021',
                '01a0f7a0-9000-700b-9c4f-5e7ad6000022', '01a0f7a0-9000-700c-9c4f-5e7ad6000023',
                '01a0f7a0-9000-700d-9c4f-5e7ad6000024', '01a0f7a0-9000-700e-9c4f-5e7ad6000025',
                '01a0f7a0-9000-700f-9c4f-5e7ad6000026', '01a0f7a0-9000-7010-9c4f-5e7ad6000027',
                '01a0f7a0-9000-7011-9c4f-5e7ad6000028')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
