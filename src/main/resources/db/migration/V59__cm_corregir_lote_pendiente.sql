-- =============================================================================
-- V59 — Un lote pendiente se corrige antes de pagarse (RF-CM-022 a RF-CM-024;
-- requirements/cm.md v0.26.0 §5.10, §7.4 y §7.6; 30-09-2026).
--
-- Todo el esquema del cambio, aunque RF-CM-023 y RF-CM-024 lo usen también:
-- las columnas y los permisos de un cambio nacen juntos, como en V51.
--
--   1. commissions.reverted_at y reverted_by: la comisión revertida al
--      corregirse el vendedor de su línea (RN-CM-047). No se borra: queda en su
--      lote, fuera del total.
--   2. commissions.withdrawn_from_batch_id: el lote pendiente del que se retiró
--      (RN-CM-046). Es lo que permite devolverla.
--   3. uq_commissions_detail_user pasa a ser un índice único PARCIAL entre las
--      vivas, con el mismo nombre (RN-CM-027): quien está en la cadena vieja y
--      en la nueva de una línea reatribuida cobra la nueva.
--   4. Los dos permisos, a SUPERADMIN y ADMIN explícitos.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 30-09-2026 a
-- medianoche UTC —`01a0ef9c6800`—, continuando la serie de permisos de CM de
-- V54: 000028 → 000029 y 000030.
-- =============================================================================

ALTER TABLE commissions
    ADD COLUMN reverted_at             timestamptz NULL,
    ADD COLUMN reverted_by             uuid        NULL,
    ADD COLUMN withdrawn_from_batch_id uuid        NULL;

ALTER TABLE commissions
    ADD CONSTRAINT ck_commissions_reverted CHECK ((reverted_at IS NULL) = (reverted_by IS NULL)),
    ADD CONSTRAINT ck_commissions_withdrawn CHECK (
        withdrawn_from_batch_id IS NULL OR withdrawn_from_batch_id <> batch_id),
    ADD CONSTRAINT fk_commissions_reverted_by FOREIGN KEY (reverted_by) REFERENCES users (id),
    ADD CONSTRAINT fk_commissions_withdrawn_from
        FOREIGN KEY (withdrawn_from_batch_id) REFERENCES commission_batches (id);

-- El nombre se conserva: un índice único viola con su nombre igual que una
-- restricción, y así nada que lo traduzca tiene que cambiar.
ALTER TABLE commissions DROP CONSTRAINT uq_commissions_detail_user;
CREATE UNIQUE INDEX uq_commissions_detail_user
    ON commissions (movement_detail_id, user_id) WHERE reverted_at IS NULL;

CREATE INDEX ix_commissions_withdrawn_from
    ON commissions (withdrawn_from_batch_id) WHERE withdrawn_from_batch_id IS NOT NULL;

COMMENT ON COLUMN commissions.reverted_at IS
    'RN-CM-047: cuándo se revirtió con la cadena de su línea al corregirse el vendedor. Presente: no cuenta en el total de su lote ni en la unicidad.';
COMMENT ON COLUMN commissions.withdrawn_from_batch_id IS
    'RN-CM-046: el lote pendiente del que se retiró. Se vacía al devolverla y se sobrescribe si se retira otra vez.';

-- ---------------------------------------------------------------------------
-- Los dos permisos (requirements/cm.md §6; security.md v0.89.0 §4.4).
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7012-9c4f-5e7ad6000029', 'commission-batches:withdraw-commission',
 'commission-batches', 'withdraw-commission',
 'Retirar una comisión de un lote pendiente',
 'Sacar una comisión de un lote PENDIENTE y pasarla al lote abierto de su persona, para pagarla en el cierre siguiente, por POST /commission-batches/{id}/commissions/{commissionId}/withdrawal (RF-CM-022).'),
('01a0ef9c-6800-7013-9c4f-5e7ad6000030', 'commission-batches:return-commission',
 'commission-batches', 'return-commission',
 'Devolver una comisión a su lote pendiente',
 'Devolver a su lote PENDIENTE de origen una comisión retirada por error, por POST /commission-batches/{id}/commissions/{commissionId}/return (RF-CM-023).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
  CROSS JOIN permissions p
 WHERE p.id IN ('01a0ef9c-6800-7012-9c4f-5e7ad6000029', '01a0ef9c-6800-7013-9c4f-5e7ad6000030')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;
