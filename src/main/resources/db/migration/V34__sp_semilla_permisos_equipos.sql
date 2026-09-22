-- ---------------------------------------------------------------------------
-- V34 — Los ocho permisos `teams:` del submódulo Equipos (RF-SP-063 a
-- RF-SP-070, requirements/sp.md §6.1 y §9, security.md §4.4, 22-09-2026).
--
-- UNO POR OPERACIÓN (RN-SEG-014): listar y ver el detalle son dos, y asignar y
-- retirar miembros también. Quien reorganiza la cúspide puede necesitar mover
-- gente sin poder dejar a nadie fuera de todo equipo, y el frontend enseña dos
-- acciones distintas.
--
-- IDENTIFICADORES LITERALES (Art. V.11), continuando la SERIE DE SP —sufijo
-- `5e7ad0`— donde `users:read-own-clients` la dejó en …00002b, y la secuencia
-- que V32 dejó en 700c. Del …00002c al …000033.
--
-- FORMA DE V19 Y V22 —un recurso nuevo con varios permisos— y NO la de V28,
-- que repartía hijos de un padre: estas ocho operaciones no existían y no hay
-- código que dividir, de modo que no hay INSERT … SELECT por pareja rol-padre.
--
-- A SUPERADMIN Y ADMIN, EXPLÍCITAS, y a ningún otro rol —ni a MANAGER—:
-- administrar cómo se organiza la cúspide es tarea de administración, y ningún
-- manager organiza su propio equipo (RF-SP-063 §14.3). El día que un manager
-- deba ver el suyo, la vía es RF-SP-005 o un permiso de alcance propio
-- (RN-SEG-015), no esta migración.
--
-- GUARDAS: 133 en el catálogo / SUPERADMIN 133 / ADMIN 127 —la reserva de seis
-- de MV le sigue faltando— / dieciséis asociaciones de `teams` y ninguna a otro
-- rol. SIN AUDITORÍA, como V8, V19, V22 y V28 a V32.
-- ---------------------------------------------------------------------------

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a0c143-2c00-700d-9c4f-5e7ad000002c', 'teams:list', 'teams', 'list',
 'Consultar equipos',
 'Ver el listado de equipos con su estado y cuántos miembros vigentes tiene cada uno (RF-SP-064). No devuelve quiénes son: eso es teams:read.'),

('01a0c143-2c00-700e-9c4f-5e7ad000002d', 'teams:read', 'teams', 'read',
 'Consultar el detalle de un equipo',
 'Ver la ficha de un equipo y quiénes lo forman hoy (RF-SP-065), incluidos los equipos eliminados, con su motivo.'),

('01a0c143-2c00-700f-9c4f-5e7ad000002e', 'teams:create', 'teams', 'create',
 'Registrar equipos',
 'Crear un equipo, que nace vacío y activo (RF-SP-063). Los miembros se asignan después.'),

('01a0c143-2c00-7010-9c4f-5e7ad000002f', 'teams:update', 'teams', 'update',
 'Editar equipos',
 'Corregir el nombre y la descripción de un equipo (RF-SP-066). No cambia su estado ni sus miembros.'),

('01a0c143-2c00-7011-9c4f-5e7ad0000030', 'teams:change-status', 'teams', 'change-status',
 'Cambiar el estado de un equipo',
 'Suspender o reactivar un equipo (RF-SP-067). Un equipo INACTIVO no recibe miembros nuevos y conserva los que tiene.'),

('01a0c143-2c00-7012-9c4f-5e7ad0000031', 'teams:delete', 'teams', 'delete',
 'Eliminar equipos',
 'Eliminar lógicamente un equipo, con motivo y solo si está vacío (RF-SP-068, RN-SP-054). El historial de pertenencias sobrevive.'),

('01a0c143-2c00-7013-9c4f-5e7ad0000032', 'teams:assign-members', 'teams', 'assign-members',
 'Asignar miembros a un equipo',
 'Asignar managers a un equipo (RF-SP-069). Solo entra quien porta el rol vendedor de mayor rango, y asignar a otro equipo cierra la pertenencia anterior.'),

('01a0c143-2c00-7014-9c4f-5e7ad0000033', 'teams:remove-members', 'teams', 'remove-members',
 'Retirar miembros de un equipo',
 'Cerrar la pertenencia de uno o varios miembros, con motivo (RF-SP-070). Es la vía para vaciar un equipo antes de eliminarlo.');

-- ---------------------------------------------------------------------------
-- Obligación de security.md §4.4: la misma migración que siembra un permiso lo
-- asocia a SUPERADMIN y a ADMIN, o ADMIN quedaría incapaz de conceder lo que no
-- tiene sin que nada fallara. EXPLÍCITAS: V8 asocia por exclusión solo hasta su
-- fecha.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',   -- SUPERADMIN
                '01a02a33-4c00-7002-9c4f-5e7ad1000002')   -- ADMIN
   AND p.resource = 'teams'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas      integer;
    de_raiz    integer;
    de_admin   integer;
    de_equipos integer;
    de_otros   integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 133 THEN
        RAISE EXCEPTION 'V34: el catálogo debe tener 133 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 133 OR de_admin <> 127 THEN
        RAISE EXCEPTION 'V34: SUPERADMIN debe portar 133 permisos y ADMIN 127 (la reserva son seis); tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO de_equipos
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.resource = 'teams';
    IF de_equipos <> 16 THEN
        RAISE EXCEPTION 'V34: los ocho teams: deben quedar asociados a SUPERADMIN y ADMIN (16 filas); hay %', de_equipos;
    END IF;

    -- Y a nadie más: administrar la cúspide es tarea de administración.
    SELECT count(*) INTO de_otros
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.resource = 'teams'
       AND rp.role_id NOT IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                              '01a02a33-4c00-7002-9c4f-5e7ad1000002');
    IF de_otros <> 0 THEN
        RAISE EXCEPTION 'V34: ningún rol distinto de SUPERADMIN y ADMIN debe portar un teams:; hay % filas', de_otros;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V34: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
