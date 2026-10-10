-- =============================================================================
-- V98 — Depósito y operaciones desde los avisos de los brokers
-- (RF-SP-078 · T-17; requirements/sp.md v1.120.0, RN-SP-073 y RN-SP-074;
-- modelo-datos.md v0.115.0; 10-10-2026).
--
-- Decisión del responsable del proyecto, 10-10-2026:
--   * El aviso de DEPÓSITO es el FTD completo: la cuenta CONSUMIDOR pasa a
--     FIRST_DEPOSIT y, si su titular está en FTD_PENDIENTE, pasa a ACTIVO y
--     se activa lo que compró al registrarse (RN-SP-057). Los siguientes
--     depósitos no cambian nada.
--   * El aviso de OPERACIÓN se cuenta: cuántas, la primera y la última.
--   * De un número que no tiene cuenta, el aviso solo se guarda.
--
--   * user_brokers.first_deposit_at: cuándo llegó el primer depósito. NULL en
--     las cuentas que ya estaban en FIRST_DEPOSIT antes de hoy: ningún aviso
--     lo dijo, y no se inventa.
--   * user_brokers.operations_count, first_operation_at, last_operation_at.
--   * broker_notifications.event_id: el identificador del aviso que da el
--     broker, para no aplicar dos veces una reentrega. Sin único: el aviso
--     repetido se GUARDA igual (RN-SP-066); lo que no se repite es su efecto.
--     Se rellena desde los avisos ya guardados.
--
-- Ningún permiso: el catálogo no se mueve.
-- =============================================================================

ALTER TABLE user_brokers
    ADD COLUMN first_deposit_at   timestamptz NULL,
    ADD COLUMN operations_count   integer     NOT NULL DEFAULT 0,
    ADD COLUMN first_operation_at timestamptz NULL,
    ADD COLUMN last_operation_at  timestamptz NULL,
    ADD CONSTRAINT ck_user_brokers_operaciones
        CHECK (operations_count >= 0
               AND (operations_count = 0) = (first_operation_at IS NULL)
               AND (first_operation_at IS NULL) = (last_operation_at IS NULL)
               AND (last_operation_at IS NULL OR last_operation_at >= first_operation_at)),
    ADD CONSTRAINT ck_user_brokers_deposito_con_estado
        CHECK (first_deposit_at IS NULL OR status = 'FIRST_DEPOSIT');

COMMENT ON COLUMN user_brokers.first_deposit_at IS
    'Cuándo avisó el broker del primer depósito (RN-SP-073). NULL en las que pasaron a FIRST_DEPOSIT sin aviso.';
COMMENT ON COLUMN user_brokers.operations_count IS
    'Cuántas operaciones avisó el broker (RN-SP-074). Cada aviso cuenta una vez, aunque se reenvíe.';
COMMENT ON COLUMN user_brokers.first_operation_at IS
    'Cuándo llegó el primer aviso de operación (RN-SP-074).';
COMMENT ON COLUMN user_brokers.last_operation_at IS
    'Cuándo llegó el último aviso de operación (RN-SP-074).';

ALTER TABLE broker_notifications ADD COLUMN event_id varchar(100) NULL;

UPDATE broker_notifications
   SET event_id = query_params -> 'event_id' ->> 0
 WHERE jsonb_typeof(query_params -> 'event_id') = 'array'
   AND jsonb_array_length(query_params -> 'event_id') = 1
   AND length(query_params -> 'event_id' ->> 0) BETWEEN 1 AND 100;

CREATE INDEX ix_broker_notifications_evento
    ON broker_notifications (broker_id, event_id) WHERE event_id IS NOT NULL;

COMMENT ON COLUMN broker_notifications.event_id IS
    'El identificador que el broker da al aviso, si lo trae. Sin único: una reentrega se guarda, y su efecto se aplica una vez (RN-SP-073, RN-SP-074).';
