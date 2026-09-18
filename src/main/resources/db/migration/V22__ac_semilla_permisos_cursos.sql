-- ---------------------------------------------------------------------------
-- V22 — Los seis permisos `courses:` del módulo AC (RF-AC-008,
-- requirements/ac.md §7, security.md §4.4, 18-09-2026).
--
-- IDENTIFICADORES LITERALES (Art. V.11), la serie de AC continúa: del …000005
-- al …000010, tras los cuatro `course-categories:` de V19.
--
-- DOS NO SON <recurso>:<acción> SOBRE UNA ESCRITURA, como products:sale y
-- products:hotlink no lo son: `courses:teach` habilita a FIGURAR como
-- instructor (RN-AC-006; lo comprueba AC al asignar, por PermissionHolderLookup)
-- y no gobierna ninguna ruta; `courses:learn` gobierna la vista del alumno
-- —catálogo, detalle y contenido de lo que se ofrece— y NO abre el catálogo
-- administrativo.
--
-- LOS MÓDULOS, LAS LECCIONES Y LAS TRES RELACIONES VAN BAJO `courses:update`
-- (ac.md §7): no existen sin su curso, y quien puede corregir un curso puede
-- armarlo. Separar «corregir» de «armar» tendrá sentido el día que alguien
-- deba poder lo uno sin lo otro, y ese día lo que se separa es la propiedad.
--
-- Los seis se asocian a SUPERADMIN y a ADMIN EXPLÍCITAMENTE —la lección de
-- V19: V8 asocia a ADMIN por exclusión solo sobre el catálogo de aquel día—,
-- también `teach` y `learn`: que un administrador pueda ser instructor o
-- recorrer el aula no es una operación que deba quedar exclusiva de la raíz.
-- A CLIENTE no: quien administre roles concede `courses:learn` a los de tipo
-- CONSUMIDOR por RF-SP-006. El catálogo pasa de 54 a 60.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

-- ---- AC · cursos ------------------------------------------------------------
('01a0b3c7-1000-7005-9c4f-5e7adc000005', 'courses:read', 'courses', 'read',
 'Consultar cursos',
 'Ver el listado de cursos y el detalle completo de cada uno —con lo inactivo, lo retirado y lo que no se ofrece— y el contenido de sus lecciones.'),
('01a0b3c7-1000-7006-9c4f-5e7adc000006', 'courses:create', 'courses', 'create',
 'Registrar cursos',
 'Crear cursos nuevos en el catálogo de la academia.'),
('01a0b3c7-1000-7007-9c4f-5e7adc000007', 'courses:update', 'courses', 'update',
 'Modificar cursos',
 'Corregir un curso, cambiar su estado y su portada, clasificarlo, recomendarle cursos previos, darle visibilidad a membresías, y registrar, corregir, cambiar de estado, retirar y poner portada a sus módulos y lecciones.'),
('01a0b3c7-1000-7008-9c4f-5e7adc000008', 'courses:delete', 'courses', 'delete',
 'Eliminar cursos',
 'Retirar un curso del catálogo, con motivo, arrastrando sus módulos y lecciones.'),
('01a0b3c7-1000-7009-9c4f-5e7adc000009', 'courses:teach', 'courses', 'teach',
 'Ser instructor de cursos',
 'Poder figurar como instructor de un curso. No gobierna ninguna ruta: lo comprueba la academia al asignar.'),
('01a0b3c7-1000-700a-9c4f-5e7adc000010', 'courses:learn', 'courses', 'learn',
 'Estudiar en la academia',
 'La vista del alumno: el catálogo de cursos que se ofrecen, el detalle de cada uno y el contenido de las lecciones que su membresía abre o que son demostración.');

-- ---------------------------------------------------------------------------
-- Obligación de security.md §4.4: la misma migración que siembra un permiso lo
-- asocia a SUPERADMIN y a ADMIN. A CLIENTE no.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')   -- ADMIN
   AND p.resource = 'courses';

DO $$
DECLARE
    asociadas integer;
BEGIN
    SELECT count(*) INTO asociadas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.resource = 'courses';
    IF asociadas <> 12 THEN
        RAISE EXCEPTION 'V22: los seis courses: deben quedar asociados a SUPERADMIN y ADMIN (12 filas); hay %', asociadas;
    END IF;
END $$;
