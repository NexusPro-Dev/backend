-- =============================================================================
-- V66 — El código de una entidad de cobro puede empezar por dígito (RF-MV-032).
--
-- requirements/mv.md v0.74.0 §7.11 · specs/mv/032-registrar-entidad-de-cobro/
-- tasks.md T-10.
--
-- Muchos bancos se identifican por su código de compensación, que es numérico, y
-- la forma anterior —la de los demás códigos del sistema, que empiezan por letra—
-- obligaba a inventarles un prefijo. Se conserva el mínimo de dos caracteres y el
-- guion bajo sigue sin poder ir primero. Relajar un CHECK no invalida ninguna fila.
--
-- Este número era de RF-MV-043 (consultar los pagos), que no está construido y
-- pasa a V67: Flyway no aplica migraciones fuera de orden.
-- =============================================================================

ALTER TABLE payout_institutions DROP CONSTRAINT ck_payout_institutions_code;

ALTER TABLE payout_institutions
    ADD CONSTRAINT ck_payout_institutions_code
        CHECK (code ~ '^[A-Z0-9][A-Z0-9_]{1,29}$');
