-- =============================================================================
-- V84 — Un lote sin pagar que se queda vacío se borra (RN-CM-052; RF-CM-022 ·
-- T-08; requirements/cm.md v0.40.0 §7.4, modelo-datos.md v0.105.0; 07-10-2026).
--
-- Al retirar la última comisión de un pendiente, este se borra; lo que se
-- retiró de él PIERDE SU ORIGEN y queda en el abierto como una comisión más.
-- La clave lo hace en el mismo DELETE, sin un UPDATE previo que alguien pueda
-- olvidar. No borra ningún lote: los vacíos de antes de hoy se quedan.
-- =============================================================================

ALTER TABLE commissions DROP CONSTRAINT fk_commissions_withdrawn_from;
ALTER TABLE commissions
    ADD CONSTRAINT fk_commissions_withdrawn_from
        FOREIGN KEY (withdrawn_from_batch_id) REFERENCES commission_batches (id)
        ON DELETE SET NULL;

COMMENT ON COLUMN commissions.withdrawn_from_batch_id IS
    'RN-CM-046: el lote pendiente del que se retiró. Se vacía al devolverla, o al borrarse ese lote por quedarse vacío (RN-CM-052), y se sobrescribe si se retira otra vez.';
