-- =============================================================================
-- V56 — El detalle de cualquier movimiento (RF-MV-007, requirements/mv.md
-- v0.53.0, security.md §4.4 v0.86.0, 30-09-2026).
--
-- `requirements/mv.md` §6 le declaraba a RF-MV-007 el mismo permiso que al
-- listado, `movements:read`, desde el 02-09-2026. Desde el 19-09-2026 no puede:
-- RN-SEG-014 (una operación, un permiso) separó el listado y el detalle de los
-- `read` de ocho recursos, y `EndpointPermissionsIT` lo hace cumplir. Nace
-- `movements:read-detail`, como `commission-batches:read-detail`.
--
-- SE REPARTE A QUIEN YA PORTA `movements:read`, como V47 con `courses:learn`:
-- quien ve el libro entero puede abrir cada fila. Hoy son SUPERADMIN, ADMIN,
-- MANAGER y DIRECTOR (V40). La contención de RN-SEG-003 se conserva por
-- construcción —el hijo va donde va el padre— y se comprueba igual.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 30-09-2026 a
-- medianoche UTC —`01a0ef9c6800`— continuando la serie de `movements:` (5e7ad7)
-- donde V50 la dejó: 000040 → 000041.
--
-- GUARDA POR CONJUNTO Y NO POR RECUENTO, como V40, V41 y V47.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0ef9c-6800-7001-9c4f-5e7ad7000041', 'movements:read-detail', 'movements', 'read-detail',
 'Consultar el detalle de un movimiento',
 'Abrir cualquier movimiento con su comprobante, sin alcance, por GET /movements/{id} (RF-MV-007). El listado es movements:read (RN-SEG-014).');

-- ---------------------------------------------------------------------------
-- El reparto: a todo rol que porte `movements:read`. `ON CONFLICT` por si
-- alguien lo concedió a mano entre dos arranques.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, '01a0ef9c-6800-7001-9c4f-5e7ad7000041'
  FROM role_permissions rp
  JOIN permissions padre ON padre.id = rp.permission_id AND padre.code = 'movements:read'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Guardas.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    sin_detalle integer;
    sin_padre   integer;
BEGIN
    -- Todo rol con `movements:read` puede abrir lo que lista.
    SELECT count(*) INTO sin_detalle
      FROM role_permissions rp
      JOIN permissions padre ON padre.id = rp.permission_id AND padre.code = 'movements:read'
     WHERE NOT EXISTS (SELECT 1 FROM role_permissions x
                        JOIN permissions p ON p.id = x.permission_id
                       WHERE x.role_id = rp.role_id
                         AND p.code = 'movements:read-detail');
    IF sin_detalle <> 0 THEN
        RAISE EXCEPTION 'V56: % roles con movements:read se quedaron sin el detalle', sin_detalle;
    END IF;

    -- Contención (RN-SEG-003).
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V56: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
