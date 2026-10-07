-- =============================================================================
-- V80 — Corregir el vendedor de una línea BORRA su cadena vieja (RN-CM-047
-- enmendada; requirements/cm.md v0.34.0 §5.10 y §7.4; 07-10-2026).
--
-- Decisión del responsable del proyecto, 07-10-2026: las comisiones de la
-- cadena vieja ya no se marcan con reverted_at, se borran. Lo que fueron
-- —persona, lote e importe de cada una— lo guarda la auditoría desde V59.
--
--   1. Se borran las comisiones ya revertidas. No cuentan en ningún total
--      (RF-CM-024 rebajó cada lote al revertirlas), así que ningún
--      total_amount cambia.
--   2. Se retiran reverted_at, reverted_by, su CHECK y su FK.
--   3. uq_commissions_detail_user vuelve a ser la RESTRICCIÓN de V51, sin
--      parcial: con las revertidas fuera, todas las comisiones son vivas, y
--      el nombre se conserva para que nada de lo que lo traduce cambie.
-- =============================================================================

DELETE FROM commissions WHERE reverted_at IS NOT NULL;

DROP INDEX uq_commissions_detail_user;

ALTER TABLE commissions
    DROP CONSTRAINT ck_commissions_reverted,
    DROP CONSTRAINT fk_commissions_reverted_by,
    DROP COLUMN reverted_at,
    DROP COLUMN reverted_by,
    ADD CONSTRAINT uq_commissions_detail_user UNIQUE (movement_detail_id, user_id);
