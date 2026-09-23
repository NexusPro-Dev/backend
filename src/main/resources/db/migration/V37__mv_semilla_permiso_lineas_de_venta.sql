-- =============================================================================
-- V37 — Las líneas de venta para administración (RF-MV-017,
-- requirements/mv.md §6, security.md §4.4, 23-09-2026).
--
-- Nace `movements:list-sale-lines`, el permiso de GET /movements/sales/lines:
-- una fila por LÍNEA de venta, que es la pregunta «qué se ha vendido» y que
-- ningún listado por movimiento contestaba sin abrir cada venta.
--
-- EL REPARTO ES EL MÁS ESTRECHO POSIBLE, y aquí está la diferencia con V32.
-- Aquel dio `movements:list-sales` a TODO ROL por su tipo porque era la vista de
-- ventas de cualquiera y lo que cada uno veía lo decidía RN-MV-031. Esta NO es
-- la vista de cualquiera: es la de administración, y con ella se ve TODO el
-- libro sin que ninguna regla lo acote. Por eso va solo a SUPERADMIN y ADMIN,
-- explícitos y no por exclusión —V8 solo asociaba así hasta su fecha—. Un
-- vendedor con este permiso vería las líneas de la empresa entera, que es
-- exactamente lo que la separación de RN-SEG-014 existe para impedir.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 del 23-09-2026 (01a0d7f13800)
-- continuando la serie de MV (5e7ad7) donde V36 la dejó: 000009 → 00000a. El
-- 000009 lo tomó `movements:assign-sellers` (RF-MV-016) en la rama de abajo, y
-- por eso este no lo reutiliza: el sufijo de la serie es lo que dice EN QUÉ ORDEN
-- nacieron los permisos del recurso.
--
-- GUARDAS: 134 en el catálogo / SUPERADMIN 134 / ADMIN 128 / cero filas que
-- rompan la contención de RN-SEG-003. Aborta si no cuadra.
--
-- EL 134 ES EL QUE DEJA V36 (`movements:assign-sellers`, RF-MV-016), que ya va
-- delante EN LA BASE DE ESTA RAMA —apilada sobre `feature/estados-de-comision`—.
-- Es una guarda estricta a propósito: si alguien aplica
-- esta migración sobre un catálogo que no es el que se diseñó, el fallo sale
-- aquí y con su mensaje, en lugar de salir como un recuento raro en cuatro
-- suites de permisos.
--
-- SIN AUDITORÍA, como V8, V22 y V28 a V32: nadie concedió estas filas.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0d7f1-3800-700d-9c4f-5e7ad700000a', 'movements:list-sale-lines', 'movements',
 'list-sale-lines',
 'Consultar las líneas de venta',
 'Ver todas las líneas de venta del libro, paginadas, por GET /movements/sales/lines (RF-MV-017). Es la lectura de ADMINISTRACION: con el se ve todo, de quien sea, y NO aplica RN-MV-031 —el alcance por estructura es de movements:list-sales—. Lo propio se consulta con movements:read-own-products.');

-- Solo SUPERADMIN y ADMIN, EXPLICITOS. Ver la cabecera: no es la vista de
-- cualquiera. ON CONFLICT por si alguien lo concedió a mano entre dos arranques.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, '01a0d7f1-3800-700d-9c4f-5e7ad700000a'
  FROM roles r
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')
ON CONFLICT DO NOTHING;

DO $$
DECLARE
    filas     integer;
    de_raiz   integer;
    de_admin  integer;
    otros     integer;
    sin_padre integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 135 THEN
        RAISE EXCEPTION 'V37: el catálogo debe quedar en 135 permisos; tiene %. Si dice 134, falta V36 (RF-MV-016)', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 135 OR de_admin <> 129 THEN
        RAISE EXCEPTION 'V37: SUPERADMIN debe portar 135 permisos y ADMIN 129 (la reserva son seis); tienen % y %', de_raiz, de_admin;
    END IF;

    -- NINGUN OTRO ROL lo porta, y es la guarda propia de esta migración: el
    -- reparto estrecho es la mitad de la decisión, y sin esto una siembra
    -- posterior podría ensancharlo sin que nada lo notara.
    SELECT count(*) INTO otros
      FROM role_permissions
     WHERE permission_id = '01a0d7f1-3800-700d-9c4f-5e7ad700000a'
       AND role_id NOT IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                           '01a02a33-4c00-7002-9c4f-5e7ad1000002');
    IF otros <> 0 THEN
        RAISE EXCEPTION 'V37: movements:list-sale-lines solo va a SUPERADMIN y ADMIN; lo portan % roles mas', otros;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V37: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
