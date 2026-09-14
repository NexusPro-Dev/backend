-- =============================================================================
-- `PENDIENTE` pasa a llamarse `FTD_PENDIENTE`.
--
-- `RF-SP-045` · `T-01`. Se planificó como `V49` el 01-09-2026 y el número se lo
-- llevaron veintiocho migraciones desde entonces.
--
-- ES UN RENOMBRADO SIN DATOS QUE MIGRAR, y por eso cabe en un `CHECK`:
-- `PENDIENTE` está declarado desde `V18` y NUNCA se usó — el alta administrativa
-- deja la cuenta `ACTIVO` con el cambio obligatorio puesto. La sentencia de
-- actualización va igualmente, porque una migración que asuma que una tabla
-- está vacía es una migración que falla el día que no lo esté.
--
-- POR QUÉ EL NOMBRE CAMBIA: `PENDIENTE` no dice pendiente DE QUÉ. El estado que
-- este requerimiento estrena significa una cosa muy concreta —la cuenta existe,
-- autentica y NO OPERA hasta que haya un primer depósito confirmado—, y un
-- nombre que no lo diga acabará usándose para «pendiente de revisión»,
-- «pendiente de correo» o cualquier otra espera. FTD es el primer depósito.
-- =============================================================================

ALTER TABLE users DROP CONSTRAINT ck_users_status;

UPDATE users SET status = 'FTD_PENDIENTE' WHERE status = 'PENDIENTE';

ALTER TABLE users
    ADD CONSTRAINT ck_users_status
    CHECK (status IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO', 'FTD_PENDIENTE'));

COMMENT ON COLUMN users.status IS
    'ACTIVO | INACTIVO | BLOQUEADO | FTD_PENDIENTE. El último autentica y NO opera: espera el primer depósito (RF-SP-045).';
