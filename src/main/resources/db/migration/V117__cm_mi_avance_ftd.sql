-- =============================================================================
-- V117 — Mi avance de FTD en el periodo abierto (RF-CM-030 · T-01;
-- requirements/cm.md v0.45.0 §5.13 y §6; security.md v0.144.0; 17-10-2026).
--
-- Nace `afftrack-settlements:read-own-progress`, el de
-- GET /afftrack-settlements/mine/progress: lo que el próximo cierre me
-- liquidaría si corriera ahora (RN-CM-056). Es de lo propio (RN-SEG-015) y,
-- como V81, se da a TODO ROL QUE PORTE `commission-batches:list-own`: quien ve
-- sus lotes ve también su avance, y la contención (RN-SEG-003) se conserva sola.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79 (01a10e829000),
-- secuencia 701f, y la serie de permisos de CM donde V87 la dejó
-- (000035 → 000036).
--
-- GUARDAS: 253 en el catálogo / el permiso exactamente en los roles que portan
-- list-own / cero filas que rompan la contención de RN-SEG-003.
--
-- SIN TABLAS: las consultas son las del cierre (RF-CM-020).
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-701f-9c4f-5e7ad6000036', 'afftrack-settlements:read-own-progress',
 'afftrack-settlements', 'read-own-progress',
 'Consultar mi avance de FTD',
 'Ver, por producto FTD, lo que el próximo cierre me liquidaría si corriera ahora —FTD nuevos directos y de mi red, remanente, escalón alcanzado con su importe estimado y cuántos faltan para el siguiente—, por GET /afftrack-settlements/mine/progress (RF-CM-030).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, '01a10e82-9000-701f-9c4f-5e7ad6000036'::uuid
  FROM role_permissions rp
  JOIN permissions p ON p.id = rp.permission_id
 WHERE p.code = 'commission-batches:list-own'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas      integer;
    distintos  integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 253 THEN
        RAISE EXCEPTION 'V117: el catálogo debe tener 253 permisos; tiene %', filas;
    END IF;

    -- Los roles con el permiso nuevo son exactamente los que portan list-own.
    SELECT count(*) INTO distintos
      FROM ((SELECT rp.role_id FROM role_permissions rp
               JOIN permissions p ON p.id = rp.permission_id
              WHERE p.code = 'commission-batches:list-own'
             EXCEPT
             SELECT role_id FROM role_permissions
              WHERE permission_id = '01a10e82-9000-701f-9c4f-5e7ad6000036')
            UNION ALL
            (SELECT role_id FROM role_permissions
              WHERE permission_id = '01a10e82-9000-701f-9c4f-5e7ad6000036'
             EXCEPT
             SELECT rp.role_id FROM role_permissions rp
               JOIN permissions p ON p.id = rp.permission_id
              WHERE p.code = 'commission-batches:list-own')) AS difieren;
    IF distintos <> 0 THEN
        RAISE EXCEPTION 'V117: % roles difieren entre list-own y el permiso nuevo', distintos;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V117: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
