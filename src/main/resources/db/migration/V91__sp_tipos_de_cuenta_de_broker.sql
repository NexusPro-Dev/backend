-- =============================================================================
-- V91 — Dos tipos de cuenta de broker: de vendedor y de consumidor (RF-SP-053
-- · T-08; requirements/sp.md v1.113.0, RN-SP-068; modelo-datos.md v0.109.0;
-- 09-10-2026).
--
-- user_brokers.kind lo pone el sistema al declarar la cuenta, según el tipo de
-- rol del titular, y no cambia después. SIN DEFAULT, a propósito: cada
-- escritura tiene que decidirlo, y un valor por omisión haría de consumidor la
-- cuenta de un vendedor que alguien olvidó clasificar.
--
-- RELLENO de lo que ya existe, con la misma regla del alta:
--   * CONSUMIDOR toda cuenta ya en FIRST_DEPOSIT: ya contó como FTD, y la
--     segunda restricción no la admitiría de otro modo.
--   * VENDEDOR si el titular porta un rol de tipo VENDEDOR.
--   * CONSUMIDOR las demás.
--
-- ck_user_brokers_ftd_solo_consumidor ata dos columnas: la cuenta de un
-- vendedor no tiene primer depósito. En el motor porque quien moverá el estado
-- es el webhook de RF-SP-054, que todavía no existe.
--
-- SIN AUDITORÍA: es una columna nueva, no un cambio de los datos de nadie.
-- =============================================================================

ALTER TABLE user_brokers ADD COLUMN kind varchar(20);

UPDATE user_brokers ub
   SET kind = CASE
                WHEN ub.status = 'FIRST_DEPOSIT' THEN 'CONSUMIDOR'
                WHEN EXISTS (SELECT 1 FROM user_roles ur
                              WHERE ur.user_id = ub.user_id
                                AND ur.role_type = 'VENDEDOR') THEN 'VENDEDOR'
                ELSE 'CONSUMIDOR'
              END;

ALTER TABLE user_brokers ALTER COLUMN kind SET NOT NULL;

ALTER TABLE user_brokers
    ADD CONSTRAINT ck_user_brokers_kind CHECK (kind IN ('VENDEDOR', 'CONSUMIDOR')),
    ADD CONSTRAINT ck_user_brokers_ftd_solo_consumidor
        CHECK (kind = 'CONSUMIDOR' OR status = 'REGISTER');

COMMENT ON COLUMN user_brokers.kind IS
    'VENDEDOR | CONSUMIDOR (RN-SP-068). Lo pone el sistema al declararla, por el tipo de rol del titular; no cambia. Sin DEFAULT. La de vendedor no tiene FTD.';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM user_brokers WHERE kind IS NULL) THEN
    RAISE EXCEPTION 'V91: quedan cuentas de broker sin tipo';
  END IF;
END $$;
