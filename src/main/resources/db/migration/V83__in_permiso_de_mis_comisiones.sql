-- =============================================================================
-- V83 — El permiso del resumen de mis comisiones (RF-IN-008 · T-01;
-- requirements/in.md v0.18.0, security.md §4.4; 07-10-2026).
--
-- Nace `indicators:read-own-commissions-summary`, el de
-- GET /indicators/commissions/mine/summary. Es un indicador DE LO PROPIO
-- (RN-IN-013, RN-SEG-015): la persona la pone el token. Se da a TODO ROL QUE
-- PORTE `commission-batches:list-own`, como V81: quien ve sus lotes ve también
-- sus cifras, y la contención (RN-SEG-003) se conserva sola, porque el padre de
-- un rol que porta list-own también lo porta.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79 (01a10e829000),
-- secuencia 7009 —la siguiente a la 7008 de V81— y la serie de IN donde V79 la
-- dejó (000007 → 000008).
--
-- GUARDAS: 206 en el catálogo / el permiso exactamente en los roles que portan
-- list-own / cero filas que rompan la contención de RN-SEG-003.
--
-- SIN AUDITORÍA, como V79 y V81.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7009-9c4f-5e7ad8000008', 'indicators:read-own-commissions-summary', 'indicators',
 'read-own-commissions-summary',
 'Consultar el resumen de mis comisiones',
 'Ver cuántas comisiones propias hay y cuánto suman, en total y según su lote esté abierto, pendiente de pago o pagado, por GET /indicators/commissions/mine/summary (RF-IN-008). Solo las de quien pregunta.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, '01a10e82-9000-7009-9c4f-5e7ad8000008'::uuid
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
    IF filas <> 206 THEN
        RAISE EXCEPTION 'V83: el catálogo debe tener 206 permisos; tiene %', filas;
    END IF;

    -- Los roles con el permiso nuevo son exactamente los que portan list-own.
    SELECT count(*) INTO distintos
      FROM ((SELECT rp.role_id FROM role_permissions rp
               JOIN permissions p ON p.id = rp.permission_id
              WHERE p.code = 'commission-batches:list-own'
             EXCEPT
             SELECT role_id FROM role_permissions
              WHERE permission_id = '01a10e82-9000-7009-9c4f-5e7ad8000008')
            UNION ALL
            (SELECT role_id FROM role_permissions
              WHERE permission_id = '01a10e82-9000-7009-9c4f-5e7ad8000008'
             EXCEPT
             SELECT rp.role_id FROM role_permissions rp
               JOIN permissions p ON p.id = rp.permission_id
              WHERE p.code = 'commission-batches:list-own')) AS diferencia;
    IF distintos <> 0 THEN
        RAISE EXCEPTION 'V83: % roles difieren entre list-own y el permiso nuevo', distintos;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V83: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
