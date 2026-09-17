-- ---------------------------------------------------------------------------
-- V19 — Los cuatro permisos `course-categories:` del módulo AC (RF-AC-001,
-- requirements/ac.md §7, security.md §4.4, 17-09-2026).
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11), como en V8: iguales en
-- todos los entornos, porque las asociaciones de esta migración y las pruebas
-- los referencian. SERIE PROPIA DE AC —sufijo `5e7adc`—, del …000001 al
-- …000004; los seis `courses:` seguirán del …000005 al …000010 en RF-AC-008.
--
-- RECURSO PROPIO Y NO `courses:`: una categoría existe sin cursos y la
-- administra quien organiza el catálogo, que puede no ser quien arma un curso
-- (requirements/ac.md §7). Es la misma diferencia que PM hizo entre `packages:`
-- y la portada.
--
-- Primera migración posterior a la consolidación (V1..V9) que siembra
-- permisos: V8 sigue guardando 50 y 44 en su guarda, y esta suma los suyos
-- —SUPERADMIN pasa a 54 y ADMIN a 48— con su propia guarda, que cuenta OCHO
-- asociaciones nuevas y ninguna a CLIENTE.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

-- ---- AC · categorías --------------------------------------------------------
('01a0b3c7-1000-7001-9c4f-5e7adc000001', 'course-categories:read', 'course-categories', 'read',
 'Consultar categorías de cursos',
 'Ver el listado de categorías del catálogo de cursos y el detalle de cada una, incluidas las retiradas.'),
('01a0b3c7-1000-7002-9c4f-5e7adc000002', 'course-categories:create', 'course-categories', 'create',
 'Registrar categorías de cursos',
 'Crear categorías nuevas del catálogo de cursos.'),
('01a0b3c7-1000-7003-9c4f-5e7adc000003', 'course-categories:update', 'course-categories', 'update',
 'Modificar categorías de cursos',
 'Corregir nombre, descripción, color, icono y orden de una categoría, y subir o quitar su portada.'),
('01a0b3c7-1000-7004-9c4f-5e7adc000004', 'course-categories:delete', 'course-categories', 'delete',
 'Eliminar categorías de cursos',
 'Retirar una categoría del catálogo, con motivo. No arrastra los cursos que contenía.');

-- ---------------------------------------------------------------------------
-- Obligación de security.md §4.4: la misma migración que siembra un permiso lo
-- asocia a SUPERADMIN y a ADMIN, o ADMIN quedaría incapaz de conceder lo que
-- no tiene sin que nada fallara. A CLIENTE no: nace sin permisos a propósito.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')   -- ADMIN
   AND p.resource = 'course-categories';

DO $$
DECLARE
    asociadas integer;
BEGIN
    SELECT count(*) INTO asociadas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.resource = 'course-categories';
    IF asociadas <> 8 THEN
        RAISE EXCEPTION 'V19: los cuatro course-categories: deben quedar asociados a SUPERADMIN y ADMIN (8 filas); hay %', asociadas;
    END IF;
END $$;
