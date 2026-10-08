-- =============================================================================
-- V86 — Borrar los lotes vacíos (RF-CM-027 · T-01; requirements/cm.md v0.41.0
-- §6, RN-CM-052 enmendada; security.md v0.123.0 §4.4; 08-10-2026).
--
-- Solo el permiso: el borrado ya existe (V84, EmptyBatchRemoval) y cambia de
-- llamador. `commission-batches:delete-empty` es de ADMINISTRACIÓN y sin
-- alcance —borra los lotes vacíos de todas las personas—, y va a SUPERADMIN y
-- ADMIN explícitos, como V60.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79 (01a10e829000),
-- secuencia 7010 —la siguiente a la 7009 de V83— y la serie de permisos de CM
-- donde V81 la dejó (000032 → 000033).
--
-- GUARDA: 207 en el catálogo.
--
-- SIN AUDITORÍA, como V81 y V83.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7010-9c4f-5e7ad6000033', 'commission-batches:delete-empty',
 'commission-batches', 'delete-empty',
 'Borrar los lotes de comisión vacíos',
 'Borrar de una vez todos los lotes ABIERTOS y PENDIENTES que no tienen ninguna comisión, de todas las personas, por DELETE /commission-batches/empty (RF-CM-027).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
  CROSS JOIN permissions p
 WHERE p.id = '01a10e82-9000-7010-9c4f-5e7ad6000033'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 207 THEN
        RAISE EXCEPTION 'V86: el catálogo debe tener 207 permisos; tiene %', filas;
    END IF;
END $$;
