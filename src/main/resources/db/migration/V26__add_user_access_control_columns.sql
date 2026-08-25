-- =============================================================================
-- RF-SP-034 · T-01 — Columnas de control de acceso.
--
-- LAS TRES LAS CREA ESTE REQUERIMIENTO Y NINGUNO MÁS, según el reparto que
-- `security.md` §9 cerró: `RF-SP-028` únicamente LEE `locked_until` y LIMPIA las
-- dos primeras al reactivar una cuenta.
--
-- Nacen aquí y no en `V18` por el criterio de `requirements/sp.md` §10.10: una
-- columna disponible antes de que exista la regla que la gobierna se acaba
-- usando por un camino que nadie diseñó.
-- =============================================================================

ALTER TABLE users
    ADD COLUMN failed_attempts smallint    NOT NULL DEFAULT 0,
    ADD COLUMN locked_until    timestamptz NULL,
    ADD COLUMN last_login_at   timestamptz NULL;

-- `locked_until` NULO SIGNIFICA NO BLOQUEADA, y no hace falta una columna de
-- «origen del bloqueo»: el bloqueo MANUAL de `RF-SP-028` se distingue porque
-- pone `status = 'BLOQUEADO'` sin `locked_until`, y el AUTOMÁTICO porque pone
-- `locked_until` sin tocar `status`. `CA-SP-378` exige distinguirlos en el
-- mensaje, y esta forma basta sin añadir una columna más.
COMMENT ON COLUMN users.locked_until IS
    'Nulo = no bloqueada. Bloqueo automático por intentos; el manual va en status.';
COMMENT ON COLUMN users.last_login_at IS
    'Dato informativo, no señal de intrusión: RF-SP-034 lo sobrescribe en cada entrada.';
