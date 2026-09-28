-- ---------------------------------------------------------------------------
-- V49 — Los saldos y el retiro (RF-MV-019, RN-MV-041 a RN-MV-043, RN-MV-046;
-- requirements/mv.md v0.46.0 §4.3, §7.1, §7.8 y §7.9, 26-09-2026).
--
-- Lo que trae, en este orden:
--
--   1. `accounts`: lo que cada persona tiene —BILLETERA, RETENIDO, PUNTOS— y
--      las contrapartidas de la empresa —COMISIONES, BONOS, RETIROS—, una por
--      titular, tipo y moneda. El saldo de una cuenta de persona no baja de
--      cero, y lo defiende el esquema (ck_accounts_saldo): es lo que hace del
--      saldo de la billetera el disponible.
--   2. `movement_entries`: los asientos, de doble entrada. Cada evento de un
--      movimiento suma cero y es de una sola moneda, y lo comprueba un
--      disparador de restricción DIFERIDO al COMMIT: un CHECK ve una fila.
--   3. Los tres tipos que no venden —RETIRO, PAGO_COMISION, BONO— con un
--      estado del tipo cada uno, REGISTRADO: type_status_id es obligatorio
--      desde V36 y el eje de la venta no existe fuera de ella.
--   4. Lo que el retiro negado y el bono escriben en la cabecera
--      (rejected_at, rejection_reason, concept) y la clave de idempotencia de
--      los movimientos que nacen confirmados sin pago (RN-MV-044, RN-MV-045).
--   5. El permiso de RF-MV-019, a todo rol por su tipo.
--
-- LAS FK VAN EN CASCADE hacia users, movements y accounts: en producción nadie
-- borra personas ni movimientos, y las suites sí —una FK sin ON DELETE rompe
-- suites lejos de aquí—. Por lo mismo NO hay un disparador que prohíba UPDATE
-- ni DELETE sobre los asientos: la inmutabilidad la sostiene el código, que
-- solo inserta.
-- ---------------------------------------------------------------------------

CREATE TABLE accounts (
    id          uuid          PRIMARY KEY,
    user_id     uuid          NULL,
    kind        varchar(30)   NOT NULL,
    name        varchar(100)  NOT NULL,
    number      varchar(30)   NOT NULL,
    currency_id uuid          NOT NULL,
    balance     numeric(14,2) NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT uq_accounts_titular UNIQUE NULLS NOT DISTINCT (user_id, kind, currency_id),
    CONSTRAINT uq_accounts_number UNIQUE (number),
    CONSTRAINT ck_accounts_kind
        CHECK ((kind IN ('BILLETERA', 'RETENIDO', 'PUNTOS') AND user_id IS NOT NULL)
            OR (kind IN ('COMISIONES', 'BONOS', 'RETIROS') AND user_id IS NULL)),
    CONSTRAINT ck_accounts_saldo
        CHECK (user_id IS NULL OR balance >= 0),
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_accounts_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id) ON DELETE RESTRICT
);

COMMENT ON TABLE accounts IS
    'RN-MV-041: lo que cada persona tiene y lo que la empresa ha dado o pagado. user_id nulo = cuenta de la empresa. balance es una COPIA de la suma de sus asientos.';
COMMENT ON CONSTRAINT ck_accounts_saldo ON accounts IS
    'RN-MV-041: el saldo de una persona no baja de cero. Es lo que hace del saldo de la billetera el disponible (RN-MV-043).';

CREATE TABLE movement_entries (
    id            uuid          PRIMARY KEY,
    movement_id   uuid          NOT NULL,
    payment_id    uuid          NULL,
    account_id    uuid          NOT NULL,
    event         varchar(30)   NOT NULL,
    amount        numeric(14,2) NOT NULL,
    balance_after numeric(14,2) NOT NULL,
    created_at    timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_movement_entries_amount CHECK (amount <> 0),
    CONSTRAINT ck_movement_entries_event
        CHECK (event IN ('SOLICITUD', 'APROBACION', 'RECHAZO', 'ABONO')),
    CONSTRAINT fk_movement_entries_movement
        FOREIGN KEY (movement_id) REFERENCES movements (id) ON DELETE CASCADE,
    CONSTRAINT fk_movement_entries_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE CASCADE,
    CONSTRAINT fk_movement_entries_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
);

COMMENT ON TABLE movement_entries IS
    'RN-MV-042: los asientos. Cuelgan del movimiento y no del pago: la retencion de un retiro ocurre antes de que exista ningun pago. amount con signo: positivo entra.';

CREATE INDEX ix_movement_entries_account  ON movement_entries (account_id, created_at DESC, id DESC);
CREATE INDEX ix_movement_entries_movement ON movement_entries (movement_id, event);

-- El cuadre, al COMMIT: los asientos de cada (movimiento, evento) suman cero
-- y son de una sola moneda.
CREATE FUNCTION f_movement_entries_cuadre() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    suma    numeric;
    monedas integer;
BEGIN
    SELECT COALESCE(sum(e.amount), 0), count(DISTINCT a.currency_id)
      INTO suma, monedas
      FROM movement_entries e
      JOIN accounts a ON a.id = e.account_id
     WHERE e.movement_id = NEW.movement_id AND e.event = NEW.event;
    IF suma <> 0 OR monedas <> 1 THEN
        RAISE EXCEPTION 'RN-MV-042: los asientos del evento % del movimiento % no cuadran (suma %, monedas %)',
            NEW.event, NEW.movement_id, suma, monedas
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER tg_movement_entries_cuadre
    AFTER INSERT ON movement_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION f_movement_entries_cuadre();

-- ---------------------------------------------------------------------------
-- Los tipos que no venden, con su estado del tipo.
-- ---------------------------------------------------------------------------

INSERT INTO movement_types (id, code, name, prefix) VALUES
('01a0f6c0-8800-7003-9c4f-5e7ad7000012', 'RETIRO',        'Retiro',           'RET'),
('01a0f6c0-8800-7004-9c4f-5e7ad7000013', 'PAGO_COMISION', 'Pago de comisión', 'PCM'),
('01a0f6c0-8800-7005-9c4f-5e7ad7000014', 'BONO',          'Bono',             'BON');

INSERT INTO movement_type_statuses (id, movement_type_id, code, name) VALUES
('01a0f6c0-8800-7006-9c4f-5e7ad7000033', '01a0f6c0-8800-7003-9c4f-5e7ad7000012', 'REGISTRADO', 'Registrado'),
('01a0f6c0-8800-7007-9c4f-5e7ad7000034', '01a0f6c0-8800-7004-9c4f-5e7ad7000013', 'REGISTRADO', 'Registrado'),
('01a0f6c0-8800-7008-9c4f-5e7ad7000035', '01a0f6c0-8800-7005-9c4f-5e7ad7000014', 'REGISTRADO', 'Registrado');

-- ---------------------------------------------------------------------------
-- La cabecera: el retiro negado, el motivo del bono y la clave de los
-- movimientos que nacen confirmados sin pago.
-- ---------------------------------------------------------------------------

ALTER TABLE movements
    ADD COLUMN rejected_at      timestamptz  NULL,
    ADD COLUMN rejection_reason varchar(500) NULL,
    ADD COLUMN concept          varchar(500) NULL,
    ADD COLUMN idempotency_key  varchar(80)  NULL,
    ADD CONSTRAINT ck_movements_rejected
        CHECK ((status = 'RECHAZADA') = (rejected_at IS NOT NULL AND rejection_reason IS NOT NULL));

CREATE UNIQUE INDEX uq_movements_idempotency_key
    ON movements (idempotency_key) WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN movements.rejected_at IS
    'RN-MV-043: cuando se nego el retiro. Atada a RECHAZADA con su motivo, como voided_at a ANULADA.';
COMMENT ON COLUMN movements.concept IS
    'RN-MV-045: el motivo de un bono; el lote citado en un pago de comision. Nulo en la venta y el retiro.';
COMMENT ON COLUMN movements.idempotency_key IS
    'RN-MV-044 y RN-MV-045: la de los movimientos que nacen confirmados y mueven un saldo sin pago detras. La venta la lleva en su pago.';

-- ---------------------------------------------------------------------------
-- El permiso de RF-MV-019, por tipo de rol.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f6c0-8800-7009-9c4f-5e7ad700000d', 'movements:request-withdrawal', 'movements', 'request-withdrawal',
 'Solicitar un retiro',
 'Pedir un retiro de la billetera propia por POST /movements/mine/withdrawals (RF-MV-019). El importe queda retenido hasta que se apruebe o se niegue.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0f6c0-8800-7009-9c4f-5e7ad700000d'
  FROM roles r
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Y los del resto de la etapa 6, que se construyen en la misma rama
-- (RF-MV-020 a RF-MV-023): sus tasks.md los dejaban cada uno en «la siguiente
-- migración libre», y una por requerimiento solo añadiría números.
--
--   · movements:approve-withdrawal, movements:reject-withdrawal y
--     movements:grant-bonus: a SUPERADMIN y ADMIN explícito. Pagar, negar y
--     regalar dinero son tareas de administración.
--   · movements:read-own-balances y movements:list-own-entries: por tipo de
--     rol, como todo lo que es sobre uno mismo.
--   · El método MANUAL (ACTIVO + INTERNO): el del pago que liquida un retiro
--     mientras no haya pasarela de salida (RF-MV-020). Nadie lo elige.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0f6c0-8800-700a-9c4f-5e7ad700000e', 'movements:approve-withdrawal', 'movements', 'approve-withdrawal',
 'Aprobar un retiro',
 'Declarar que el dinero de un retiro pendiente salió, con la referencia de la transferencia si la hay, por POST /movements/{id}/withdrawal-approval (RF-MV-020).'),
('01a0f6c0-8800-700b-9c4f-5e7ad700000f', 'movements:reject-withdrawal', 'movements', 'reject-withdrawal',
 'Negar un retiro',
 'Negar un retiro pendiente, con motivo, devolviendo lo retenido a la billetera, por POST /movements/{id}/withdrawal-rejection (RF-MV-021).'),
('01a0f6c0-8800-700c-9c4f-5e7ad7000010', 'movements:read-own-balances', 'movements', 'read-own-balances',
 'Consultar mis saldos',
 'Ver la billetera, lo retenido y los puntos propios, por moneda, por GET /movements/mine/balances (RF-MV-022).'),
('01a0f6c0-8800-700d-9c4f-5e7ad7000011', 'movements:list-own-entries', 'movements', 'list-own-entries',
 'Consultar el historial de mis saldos',
 'Ver, asiento a asiento, los cambios en los saldos propios por GET /movements/mine/balances/entries (RF-MV-022).'),
('01a0f6c0-8800-700e-9c4f-5e7ad7000012', 'movements:grant-bonus', 'movements', 'grant-bonus',
 'Otorgar un bono',
 'Abonar un bono en la billetera de una persona, con motivo y Idempotency-Key, por POST /movements/bonuses (RF-MV-023).');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0f6c0-8800-700a-9c4f-5e7ad700000e'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0f6c0-8800-700a-9c4f-5e7ad700000e'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0f6c0-8800-700b-9c4f-5e7ad700000f'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0f6c0-8800-700b-9c4f-5e7ad700000f'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0f6c0-8800-700e-9c4f-5e7ad7000012'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0f6c0-8800-700e-9c4f-5e7ad7000012')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE p.id IN ('01a0f6c0-8800-700c-9c4f-5e7ad7000010', '01a0f6c0-8800-700d-9c4f-5e7ad7000011')
   AND r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO payment_methods (id, code, name, is_active, visibility) VALUES
('01a0f6c0-8800-700f-9c4f-5e7adb000002', 'MANUAL', 'Pago registrado a mano', true, 'INTERNO');

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payment_methods WHERE code = 'MANUAL' AND visibility = 'INTERNO') THEN
        RAISE EXCEPTION 'V49: falta el método MANUAL';
    END IF;
END $$;
