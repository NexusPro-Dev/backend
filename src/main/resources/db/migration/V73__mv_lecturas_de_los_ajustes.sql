-- =============================================================================
-- V73 — Las dos lecturas de la pantalla de ajustes (RF-MV-053, RF-MV-054;
-- requirements/mv.md v0.84.0 §4.11, §6 y §7.1; 05-10-2026).
--
--   1. movements.recorded_by: quién registró un ajuste de puntos. SET NULL:
--      borrar una persona no borra lo que hizo. Los ajustes anteriores quedan
--      nulos: no se reconstruyen desde la auditoría.
--   2. El índice del listado de ajustes, parcial por el tipo, con su
--      identificador literal (V72).
--   3. movements:list-points-adjustments y movements:read-user-balances, a
--      SUPERADMIN y ADMIN explícito.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V58 —`01a0ef9c6800`—,
-- continuando la serie de permisos de MV: 000062 → 000063, 000064.
-- =============================================================================

ALTER TABLE movements
    ADD COLUMN recorded_by uuid NULL,
    ADD CONSTRAINT fk_movements_recorded_by
        FOREIGN KEY (recorded_by) REFERENCES users (id) ON DELETE SET NULL;

COMMENT ON COLUMN movements.recorded_by IS
    'RF-MV-053: quién registró un ajuste de puntos. Nula en lo demás y en los ajustes anteriores a V73. No sustituye a la auditoría.';

CREATE INDEX ix_movements_ajustes ON movements (occurred_at DESC, id DESC)
    WHERE movement_type_id = '01a0ef9c-6800-7025-9c4f-5e7ad7000016';

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7028-9c4f-5e7ad7000063', 'movements:list-points-adjustments', 'movements',
 'list-points-adjustments',
 'Consultar los ajustes de puntos',
 'Ver los ajustes de puntos de cualquier persona, con quién los hizo, por GET /movements/points-adjustments (RF-MV-053).'),
('01a0ef9c-6800-7029-9c4f-5e7ad7000064', 'movements:read-user-balances', 'movements',
 'read-user-balances',
 'Consultar los saldos de una persona',
 'Ver la billetera, lo retenido y los puntos de cualquier persona, por GET /movements/users/{userId}/balances (RF-MV-054).');

INSERT INTO role_permissions (role_id, permission_id) VALUES
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7028-9c4f-5e7ad7000063'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7028-9c4f-5e7ad7000063'),
('01a02a33-4c00-7001-9c4f-5e7ad1000001', '01a0ef9c-6800-7029-9c4f-5e7ad7000064'),
('01a02a33-4c00-7002-9c4f-5e7ad1000002', '01a0ef9c-6800-7029-9c4f-5e7ad7000064')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
