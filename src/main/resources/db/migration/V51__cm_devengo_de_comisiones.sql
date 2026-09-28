-- ---------------------------------------------------------------------------
-- V51 — La liquidación de CM, con el devengo automático (RF-CM-009 a
-- RF-CM-014; requirements/cm.md v0.20.0 §5.6, §5.7 y §7.5 a §7.8).
--
-- La comisión de una línea de venta nace en el momento en que la venta está
-- CONFIRMADA y la línea tiene vendedor (RN-CM-022, RN-CM-031): una fila por
-- nivel de la cadena en commissions, sumada al lote ABIERTO de su persona y
-- moneda (RN-CM-033). El cierre programado pasa los lotes abiertos a
-- PENDIENTE (RF-CM-009) y Finanzas los paga (RF-CM-011).
--
-- Las cuatro tablas y los ocho permisos del submódulo nacen juntos: repartirlos
-- en cinco migraciones obligaría a cinco recuentos del catálogo
-- (specs/cm/013-devengar-comision-linea/plan.md §2).
-- ---------------------------------------------------------------------------

-- Cada cierre, también el que no cerró nada y el que falló a medias (§7.8).
CREATE TABLE commission_closings (
    id              uuid        PRIMARY KEY,
    origin          varchar(20) NOT NULL,
    scheduled_for   timestamptz NULL,
    triggered_by    uuid        NULL,
    started_at      timestamptz NOT NULL,
    closed_at       timestamptz NULL,
    batches_closed  integer     NOT NULL DEFAULT 0,
    lines_swept     integer     NOT NULL DEFAULT 0,
    lines_retried   integer     NOT NULL DEFAULT 0,
    lines_recovered integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL,

    CONSTRAINT ck_commission_closings_origin CHECK (
        origin IN ('PROGRAMADO', 'MANUAL')
        AND ((origin = 'MANUAL') = (triggered_by IS NOT NULL))
        AND ((origin = 'PROGRAMADO') = (scheduled_for IS NOT NULL))),
    CONSTRAINT ck_commission_closings_counts CHECK (
        batches_closed >= 0 AND lines_swept >= 0 AND lines_retried >= 0
        AND lines_recovered >= 0 AND lines_recovered <= lines_retried),
    CONSTRAINT fk_commission_closings_triggered_by
        FOREIGN KEY (triggered_by) REFERENCES users (id)
);

-- RN-CM-035: un turno programado se cierra una vez, aunque lo disparen todas
-- las réplicas. La fila se escribe lo primero; la réplica que choca no hace nada.
CREATE UNIQUE INDEX uq_commission_closings_scheduled ON commission_closings (scheduled_for);

COMMENT ON TABLE commission_closings IS
    'RF-CM-009: cada cierre del periodo, programado o a mano. closed_at nulo: en curso, o falló entre el barrido y el cierre.';

-- El lote de una persona, un periodo y una moneda (§7.5). Sin deleted_at: es un
-- hecho consumado (RN-CM-029).
CREATE TABLE commission_batches (
    id           uuid          PRIMARY KEY,
    code         varchar(40)   NOT NULL,
    user_id      uuid          NOT NULL,
    currency_id  uuid          NOT NULL,
    period_start timestamptz   NOT NULL,
    period_end   timestamptz   NULL,
    status       varchar(20)   NOT NULL,
    closing_id   uuid          NULL,
    total_amount numeric(14,4) NOT NULL,
    paid_at      timestamptz   NULL,
    movement_id  uuid          NULL,
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,

    CONSTRAINT uq_commission_batches_code UNIQUE (code),
    CONSTRAINT ck_commission_batches_status CHECK (status IN ('ABIERTO', 'PENDIENTE', 'PAGADO')),
    -- Un lote abierto no tiene fin ni cierre; uno cerrado siempre los tiene.
    CONSTRAINT ck_commission_batches_periodo CHECK (
        ((status = 'ABIERTO') = (period_end IS NULL))
        AND ((status = 'ABIERTO') = (closing_id IS NULL))
        AND (period_end IS NULL OR period_end > period_start)),
    -- Calca ck_movements_voided: PAGADO sin fecha o sin su abono no existe.
    CONSTRAINT ck_commission_batches_pagado CHECK (
        (status = 'PAGADO') = (paid_at IS NOT NULL AND movement_id IS NOT NULL)
        AND (paid_at IS NULL) = (movement_id IS NULL)),
    CONSTRAINT ck_commission_batches_total CHECK (total_amount >= 0),
    CONSTRAINT uq_commission_batches_movement UNIQUE (movement_id),
    -- RN-CM-028 y RN-CM-033. Con el fin nulo el rango no tiene techo, y por eso
    -- la misma restricción impide dos lotes abiertos de la misma persona y
    -- moneda. Semiabierto para que el lote que nace en el instante del cierre no
    -- choque con el cerrado. No trae nombre al violarse: se traduce por estado
    -- SQL, 23P01 y 40P01.
    CONSTRAINT ex_commission_batches_solape EXCLUDE USING gist (
        user_id WITH =,
        currency_id WITH =,
        tstzrange(period_start, period_end, '[)') WITH &&),
    CONSTRAINT fk_commission_batches_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_commission_batches_currency FOREIGN KEY (currency_id) REFERENCES currencies (id),
    CONSTRAINT fk_commission_batches_closing FOREIGN KEY (closing_id) REFERENCES commission_closings (id),
    -- De CM hacia MV y no al revés: MV no depende de CM (requirements/cm.md §3).
    CONSTRAINT fk_commission_batches_movement FOREIGN KEY (movement_id) REFERENCES movements (id)
);

CREATE INDEX ix_commission_batches_abierto ON commission_batches (user_id, currency_id)
    WHERE status = 'ABIERTO';
CREATE INDEX ix_commission_batches_periodo ON commission_batches (period_start DESC, id);

COMMENT ON TABLE commission_batches IS
    'RN-CM-028, RN-CM-033: lo que se le debe a una persona en una moneda. ABIERTO mientras crece; el cierre lo pasa a PENDIENTE; pagarlo lo abona en la billetera (RN-CM-030).';

-- Una fila por línea de venta y por nivel de la cadena (§7.6).
CREATE TABLE commissions (
    id                 uuid          PRIMARY KEY,
    batch_id           uuid          NOT NULL,
    movement_detail_id uuid          NOT NULL,
    user_id            uuid          NOT NULL,
    chain_level        smallint      NOT NULL,
    source             varchar(20)   NOT NULL,
    rate_id            uuid          NOT NULL,
    resolved_on        date          NOT NULL,
    rate_type          varchar(20)   NOT NULL,
    percentage         numeric(5,2)  NULL,
    fixed_amount       numeric(14,4) NULL,
    unit_price         numeric(14,2) NOT NULL,
    quantity           integer       NOT NULL,
    commission_amount  numeric(14,4) NOT NULL,
    accrued_at         timestamptz   NOT NULL,
    created_at         timestamptz   NOT NULL,

    -- RN-CM-027: la pareja y no la línea sola, porque la cadena emite una fila
    -- por nivel; global y no por lote, porque lo que no se repite es «dos veces
    -- en la historia».
    CONSTRAINT uq_commissions_detail_user UNIQUE (movement_detail_id, user_id),
    CONSTRAINT ck_commissions_source CHECK (source IN ('PERSONALIZADA', 'ROL')),
    CONSTRAINT ck_commissions_type CHECK (rate_type IN ('PORCENTAJE', 'FIJO')),
    -- Repite ck_commission_rates_forma a propósito: la fila ya no depende de la tasa.
    CONSTRAINT ck_commissions_forma CHECK (
        (rate_type = 'PORCENTAJE' AND percentage IS NOT NULL AND fixed_amount IS NULL)
        OR (rate_type = 'FIJO' AND fixed_amount IS NOT NULL AND percentage IS NULL)),
    CONSTRAINT ck_commissions_percentage CHECK (
        percentage IS NULL OR (percentage >= 0 AND percentage <= 100)),
    CONSTRAINT ck_commissions_fixed CHECK (fixed_amount IS NULL OR fixed_amount >= 0),
    CONSTRAINT ck_commissions_quantity CHECK (quantity > 0),
    CONSTRAINT ck_commissions_amount CHECK (commission_amount >= 0 AND unit_price >= 0),
    CONSTRAINT ck_commissions_chain_level CHECK (chain_level >= 0),
    CONSTRAINT fk_commissions_batch
        FOREIGN KEY (batch_id) REFERENCES commission_batches (id) ON DELETE CASCADE,
    -- RN-CM-029: una línea con comisión no se borra. Toda suite que limpie
    -- movements limpia antes commissions y commission_accruals.
    CONSTRAINT fk_commissions_detail
        FOREIGN KEY (movement_detail_id) REFERENCES movement_details (id) ON DELETE RESTRICT,
    CONSTRAINT fk_commissions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_commissions_batch ON commissions (batch_id);

COMMENT ON COLUMN commissions.rate_id IS
    'La tasa exacta que ganó (RN-CM-008). Sin clave foránea: apunta a commission_rates o a user_commission_rates según source.';
COMMENT ON COLUMN commissions.resolved_on IS
    'RN-CM-024: el día de la venta en la zona del negocio, con el que se resolvió la tasa.';
COMMENT ON COLUMN commissions.accrued_at IS
    'RN-CM-033: cuándo nació la comisión. Decide el lote, nunca el importe.';

-- Qué pasó con cada línea (§7.7): lo que permite al barrido saber qué no se atendió.
CREATE TABLE commission_accruals (
    movement_detail_id uuid         PRIMARY KEY,
    outcome            varchar(20)  NOT NULL,
    reason             varchar(500) NULL,
    attempts           integer      NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,

    CONSTRAINT ck_commission_accruals_outcome CHECK (
        outcome IN ('DEVENGADA', 'SIN_COMISION', 'RECHAZADA')),
    -- RN-CM-026: un rechazo sin motivo no se puede arreglar.
    CONSTRAINT ck_commission_accruals_reason CHECK (
        (outcome = 'RECHAZADA') = (reason IS NOT NULL)),
    CONSTRAINT ck_commission_accruals_attempts CHECK (attempts > 0),
    CONSTRAINT fk_commission_accruals_detail
        FOREIGN KEY (movement_detail_id) REFERENCES movement_details (id) ON DELETE RESTRICT
);

CREATE INDEX ix_commission_accruals_outcome_updated ON commission_accruals (outcome, updated_at DESC);

COMMENT ON TABLE commission_accruals IS
    'RN-CM-032: un desenlace por línea. SIN_COMISION es definitivo; RECHAZADA se reintenta en cada cierre.';

-- ---------------------------------------------------------------------------
-- Los ocho permisos de la liquidación (requirements/cm.md §6; security.md
-- v0.81.0 §4.4). Serie de CM, a continuación del 000011 de V28.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f7a0-9000-7001-9c4f-5e7ad6000012', 'commission-batches:settle', 'commission-batches', 'settle',
 'Cerrar el periodo de comisiones',
 'Cerrar a mano el periodo: recoger lo pendiente y pasar los lotes abiertos a PENDIENTE, por POST /commission-batches/closing (RF-CM-009). El cierre programado no necesita permiso.'),
('01a0f7a0-9000-7002-9c4f-5e7ad6000013', 'commission-batches:read', 'commission-batches', 'read',
 'Consultar los lotes de comisión',
 'Listar los lotes de todas las personas, abiertos, pendientes y pagados, por GET /commission-batches (RF-CM-010).'),
('01a0f7a0-9000-7003-9c4f-5e7ad6000014', 'commission-batches:read-detail', 'commission-batches', 'read-detail',
 'Consultar un lote de comisión',
 'Ver un lote con cada comisión, línea a línea y nivel a nivel, por GET /commission-batches/{id} (RF-CM-010).'),
('01a0f7a0-9000-7004-9c4f-5e7ad6000015', 'commission-batches:pay', 'commission-batches', 'pay',
 'Pagar un lote de comisión',
 'Marcar como pagado un lote PENDIENTE y abonarlo en la billetera de su persona, por POST /commission-batches/{id}/payment (RF-CM-011).'),
('01a0f7a0-9000-7005-9c4f-5e7ad6000016', 'commission-batches:list-own', 'commission-batches', 'list-own',
 'Consultar mis lotes de comisión',
 'Listar los lotes propios, el abierto incluido, por GET /commission-batches/mine (RF-CM-012).'),
('01a0f7a0-9000-7006-9c4f-5e7ad6000017', 'commission-batches:read-own', 'commission-batches', 'read-own',
 'Consultar uno de mis lotes de comisión',
 'Ver un lote propio con sus comisiones, por GET /commission-batches/mine/{id} (RF-CM-012).'),
('01a0f7a0-9000-7007-9c4f-5e7ad6000018', 'commission-closings:read', 'commission-closings', 'read',
 'Consultar los cierres de comisiones',
 'Listar los cierres del periodo, programados y a mano, con lo que hizo cada uno, por GET /commission-closings (RF-CM-009).'),
('01a0f7a0-9000-7008-9c4f-5e7ad6000019', 'commission-accruals:read', 'commission-accruals', 'read',
 'Consultar el desenlace de las líneas',
 'Listar qué pasó con cada línea de venta a efectos de comisión, con los rechazos y su motivo, por GET /commission-accruals (RF-CM-014).');

-- Los ocho a SUPERADMIN y ADMIN, explícitos.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
  CROSS JOIN permissions p
 WHERE p.id IN ('01a0f7a0-9000-7001-9c4f-5e7ad6000012', '01a0f7a0-9000-7002-9c4f-5e7ad6000013',
                '01a0f7a0-9000-7003-9c4f-5e7ad6000014', '01a0f7a0-9000-7004-9c4f-5e7ad6000015',
                '01a0f7a0-9000-7005-9c4f-5e7ad6000016', '01a0f7a0-9000-7006-9c4f-5e7ad6000017',
                '01a0f7a0-9000-7007-9c4f-5e7ad6000018', '01a0f7a0-9000-7008-9c4f-5e7ad6000019')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- Lo propio, a quien cobra: por tipo de rol y no por nombre (RN-SEG-015).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE p.id IN ('01a0f7a0-9000-7005-9c4f-5e7ad6000016', '01a0f7a0-9000-7006-9c4f-5e7ad6000017')
   AND r.role_type = 'VENDEDOR'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
