-- =============================================================================
-- V81 — Consultar todas mis comisiones (RF-CM-026 · T-01; requirements/cm.md
-- v0.36.0 §6 y §7.6, security.md §4.4; 07-10-2026).
--
-- Nace `commission-batches:list-own-commissions`, el de
-- GET /commission-batches/mine/commissions: todas las comisiones propias en
-- una lista, sin pasar por los lotes. Es de lo propio (RN-SEG-015) y se da a
-- TODO ROL QUE PORTE `commission-batches:list-own`: quien ya ve sus lotes ve
-- también la lista, y la contención (RN-SEG-003) se conserva sola, porque el
-- padre de un rol que porta list-own también lo porta.
--
-- Y `ix_commissions_user`: el WHERE y el ORDER BY de esa página.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V79 (01a10e829000),
-- secuencia 7008, y la serie de permisos de CM donde V60 la dejó
-- (000031 → 000032).
--
-- GUARDAS: 205 en el catálogo / el permiso exactamente en los roles que portan
-- list-own / cero filas que rompan la contención de RN-SEG-003.
--
-- SIN AUDITORÍA, como V74, V76, V78 y V79.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7008-9c4f-5e7ad6000032', 'commission-batches:list-own-commissions',
 'commission-batches', 'list-own-commissions',
 'Consultar todas mis comisiones',
 'Listar todas las comisiones propias, una fila por comisión y cada una con su lote, el estado de este, su moneda y el cliente de la venta, por GET /commission-batches/mine/commissions (RF-CM-026).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, '01a10e82-9000-7008-9c4f-5e7ad6000032'::uuid
  FROM role_permissions rp
  JOIN permissions p ON p.id = rp.permission_id
 WHERE p.code = 'commission-batches:list-own'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

CREATE INDEX ix_commissions_user ON commissions (user_id, accrued_at DESC, id DESC);

DO $$
DECLARE
    filas      integer;
    distintos  integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 205 THEN
        RAISE EXCEPTION 'V81: el catálogo debe tener 205 permisos; tiene %', filas;
    END IF;

    -- Los roles con el permiso nuevo son exactamente los que portan list-own.
    SELECT count(*) INTO distintos
      FROM (SELECT rp.role_id FROM role_permissions rp
              JOIN permissions p ON p.id = rp.permission_id
             WHERE p.code = 'commission-batches:list-own'
            EXCEPT
            SELECT role_id FROM role_permissions
             WHERE permission_id = '01a10e82-9000-7008-9c4f-5e7ad6000032') AS faltan;
    IF distintos <> 0 THEN
        RAISE EXCEPTION 'V81: % roles portan list-own y no el permiso nuevo', distintos;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V81: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
