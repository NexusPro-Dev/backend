-- ---------------------------------------------------------------------------
-- V48 — El pago como intento (RF-MV-018, RN-MV-039, RN-MV-040;
-- requirements/mv.md v0.44.0 §4.3 y §7.7, 26-09-2026).
--
-- Decisión del responsable del proyecto: el método de pago deja de ser de la
-- venta y pasa a ser de CADA INTENTO de pagarla, con sus propios estados
-- —PENDIENTE, CONFIRMADO, RECHAZADO—. Una venta cuyo cobro falló se vuelve a
-- pagar sin registrarse otra vez.
--
-- EL ORDEN IMPORTA, como en V12: crear, copiar, comprobar, y solo entonces
-- borrar la columna de la cabecera.
--
--   · Las filas trasladadas llevan un UUID v4 (gen_random_uuid): la aplicación
--     genera v7, pero nadie ordena por el identificador de un pago —se ordena
--     por occurred_at— y reescribirlos no aporta nada.
--   · Su clave de idempotencia es 'migracion-' || id del movimiento: única,
--     y dice de dónde salió.
--   · Ninguna venta está RECHAZADA (RF-MV-004 nunca se construyó); el ELSE la
--     cubriría igual que a la ANULADA.
--
-- V47 la tomó AC (el aula) el mismo día, en otra rama.
-- ---------------------------------------------------------------------------

CREATE TABLE payments (
    id                 uuid          PRIMARY KEY,
    movement_id        uuid          NOT NULL,
    payment_method_id  uuid          NOT NULL,
    status             varchar(20)   NOT NULL,
    amount             numeric(14,2) NOT NULL,
    idempotency_key    varchar(80)   NOT NULL,
    provider_reference varchar(120)  NULL,
    occurred_at        timestamptz   NOT NULL,
    confirmed_at       timestamptz   NULL,
    rejected_at        timestamptz   NULL,
    rejection_reason   varchar(500)  NULL,
    created_at         timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_status
        CHECK (status IN ('PENDIENTE', 'CONFIRMADO', 'RECHAZADO')),
    CONSTRAINT ck_payments_confirmed
        CHECK ((status = 'CONFIRMADO') = (confirmed_at IS NOT NULL)),
    CONSTRAINT ck_payments_rejected
        CHECK ((status = 'RECHAZADO') = (rejected_at IS NOT NULL)),
    CONSTRAINT ck_payments_amount
        CHECK (amount >= 0),
    -- CASCADE y no RESTRICT: una FK sin ON DELETE rompe las suites que limpian
    -- con DELETE FROM movements, lejos de aquí. En producción nadie borra
    -- movimientos (RN-MV-001).
    CONSTRAINT fk_payments_movement
        FOREIGN KEY (movement_id) REFERENCES movements (id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id) ON DELETE RESTRICT
);

COMMENT ON TABLE payments IS
    'RN-MV-039: cada intento de cobrar o de pagar un movimiento. Sin updated_at ni deleted_at: lo unico que cambia es su estado, una vez.';
COMMENT ON COLUMN payments.idempotency_key IS
    'RN-MV-040: la misma peticion repetida no crea dos pagos. La manda el cliente; si no la manda (compras), la pone el sistema.';
COMMENT ON COLUMN payments.provider_reference IS
    'RN-MV-040: la referencia de quien cobra. Nula cuando confirma una persona mirando un extracto.';

-- RN-MV-039: a lo sumo un pago pendiente por movimiento a la vez, y a lo sumo
-- uno confirmado en toda su vida. La segunda es la que impide cobrar dos veces.
CREATE UNIQUE INDEX uq_payments_uno_pendiente  ON payments (movement_id) WHERE status = 'PENDIENTE';
CREATE UNIQUE INDEX uq_payments_uno_confirmado ON payments (movement_id) WHERE status = 'CONFIRMADO';

-- El «último pago» de los tres listados y del detalle.
CREATE INDEX ix_payments_ultimo ON payments (movement_id, occurred_at DESC, id DESC);
CREATE INDEX ix_payments_metodo ON payments (payment_method_id);

-- ---------------------------------------------------------------------------
-- El traslado.
-- ---------------------------------------------------------------------------

INSERT INTO payments (id, movement_id, payment_method_id, status, amount, idempotency_key,
                      occurred_at, confirmed_at, rejected_at, rejection_reason, created_at)
SELECT gen_random_uuid(), m.id, m.payment_method_id,
       CASE m.status WHEN 'CONFIRMADA' THEN 'CONFIRMADO'
                     WHEN 'PENDIENTE'  THEN 'PENDIENTE'
                     ELSE 'RECHAZADO' END,
       m.payable_amount,
       'migracion-' || m.id,
       m.occurred_at,
       m.confirmed_at,
       CASE WHEN m.status IN ('ANULADA', 'RECHAZADA') THEN COALESCE(m.voided_at, m.created_at) END,
       CASE WHEN m.status = 'ANULADA'   THEN 'Venta anulada: ' || m.void_reason
            WHEN m.status = 'RECHAZADA' THEN 'Venta rechazada' END,
       m.created_at
  FROM movements m;

DO $$
DECLARE
    sin_pago integer;
BEGIN
    SELECT count(*) INTO sin_pago
      FROM movements m
     WHERE (SELECT count(*) FROM payments p WHERE p.movement_id = m.id) <> 1;
    IF sin_pago <> 0 THEN
        RAISE EXCEPTION 'V48: % movimientos no quedaron con exactamente un pago', sin_pago;
    END IF;
END $$;

ALTER TABLE movements DROP CONSTRAINT fk_movements_payment_method;
ALTER TABLE movements DROP COLUMN payment_method_id;

-- ---------------------------------------------------------------------------
-- El permiso de RF-MV-018, como V31 sembró los de alcance propio: a todo rol
-- por su tipo, porque volver a pagar lo suyo lo tiene que poder hacer
-- cualquiera que compre.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f6c0-8800-7001-9c4f-5e7ad700000b', 'movements:retry-payment', 'movements', 'retry-payment',
 'Volver a pagar una compra propia',
 'Abrir un nuevo intento de pago sobre una venta propia pendiente cuyo ultimo pago se rechazo, por POST /movements/mine/{id}/payments (RF-MV-018). Exige Idempotency-Key.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0f6c0-8800-7001-9c4f-5e7ad700000b'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Y el de RF-MV-004, rechazar el pago pendiente de una venta: a SUPERADMIN y a
-- ADMIN explícito, como movements:confirm. Conciliar es administración. Va en
-- esta migración y no en una propia porque los dos requerimientos se
-- construyen juntos sobre la misma tabla (RF-MV-004 · tasks.md §3).
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f6c0-8800-7002-9c4f-5e7ad700000c', 'movements:reject-payment', 'movements', 'reject-payment',
 'Rechazar el pago pendiente de una venta',
 'Dar por no entrado el cobro pendiente de una venta, con motivo, por POST /movements/{id}/rejection (RF-MV-004). La venta sigue pendiente y se puede volver a pagar. Confirmar es movements:confirm.');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0f6c0-8800-7002-9c4f-5e7ad700000c'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0f6c0-8800-7002-9c4f-5e7ad700000c')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
