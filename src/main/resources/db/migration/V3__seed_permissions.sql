-- RF-SP-010 · T-03 — Siembra del catálogo de permisos.
--
-- Los veinticuatro permisos de requirements/sp.md §9. El catálogo completo
-- vive aquí, incluidos los ocho de `users:`: al retirarse el módulo USR y
-- absorber SP los usuarios (modules.md v0.9.0), no queda otro módulo que
-- pudiera sembrarlos.
--
-- IDENTIFICADORES LITERALES, no generados. Ni gen_random_uuid() ni ninguna
-- generación en base de datos: el Art. V.11 lo prohíbe, y además el
-- identificador de cada permiso debe ser el mismo en todos los entornos para
-- que V7__seed_system_roles.sql pueda asociarlos y para que las pruebas de
-- RF-SP-001 y RF-SP-005 los referencien por constante.
--
-- Son UUID v7 con marca de tiempo 2026-08-22T15:00:00Z (01a029fc-5d80),
-- versión 7 y variante RFC 9562, y crecen de forma monótona en el orden en
-- que se listan. Se generaron una sola vez al redactar esta migración.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, y la diferencia con V7__seed_system_roles
-- es deliberada: un permiso no tiene línea de tiempo que reconstruir, porque
-- RN-SP-004 lo hace inmutable por API. Su único historial posible es el de las
-- migraciones, y ese lo lleva flyway_schema_history.

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

-- Roles — RF-SP-001 a RF-SP-009
('01a029fc-5d80-7001-9c4f-5e7ad0000001', 'roles:read', 'roles', 'read',
 'Consultar roles',
 'Ver el listado de roles, el detalle de cada uno y los permisos que declara.'),
('01a029fc-5d80-7002-9c4f-5e7ad0000002', 'roles:create', 'roles', 'create',
 'Registrar roles',
 'Crear roles nuevos, siempre dentro de la cota de privilegios del propio actor.'),
('01a029fc-5d80-7003-9c4f-5e7ad0000003', 'roles:update', 'roles', 'update',
 'Modificar roles',
 'Editar nombre y descripción, cambiar el estado, reubicar el rol padre y asignar o retirar permisos.'),
('01a029fc-5d80-7004-9c4f-5e7ad0000004', 'roles:delete', 'roles', 'delete',
 'Eliminar roles',
 'Eliminar lógicamente un rol que no tenga roles hijos vigentes ni usuarios asignados.'),

-- Permisos — RF-SP-010 y RF-SP-015
('01a029fc-5d80-7005-9c4f-5e7ad0000005', 'permissions:read', 'permissions', 'read',
 'Consultar permisos',
 'Ver el catálogo de permisos del sistema y el detalle de cada uno.'),

-- Auditoría — RF-SP-011 a RF-SP-014. Se leen por tipo y no en bloque, porque
-- no tienen la misma sensibilidad (security.md §4.4).
('01a029fc-5d80-7006-9c4f-5e7ad0000006', 'audit:read-changes', 'audit', 'read-changes',
 'Consultar auditoría de cambios',
 'Ver qué se modificó, quién lo modificó y cuándo, con el valor anterior y el nuevo de cada campo.'),
('01a029fc-5d80-7007-9c4f-5e7ad0000007', 'audit:read-deletions', 'audit', 'read-deletions',
 'Consultar auditoría de eliminación',
 'Ver qué se eliminó, con el motivo declarado y el estado que tenía la entidad al eliminarse.'),
('01a029fc-5d80-7008-9c4f-5e7ad0000008', 'audit:read-errors', 'audit', 'read-errors',
 'Consultar auditoría de error',
 'Ver los fallos no controlados y los rechazos por regla de negocio, para investigar incidencias.'),
('01a029fc-5d80-7009-9c4f-5e7ad0000009', 'audit:read-security', 'audit', 'read-security',
 'Consultar auditoría de seguridad',
 'Ver la actividad de autenticación y autorización del sistema. Reservado a SUPERADMIN (security.md §4.4).'),

-- Membresías — RF-SP-016 a RF-SP-018
('01a029fc-5d80-700a-9c4f-5e7ad000000a', 'memberships:read', 'memberships', 'read',
 'Consultar membresías',
 'Ver el listado de membresías y el detalle de cada una.'),
('01a029fc-5d80-700b-9c4f-5e7ad000000b', 'memberships:create', 'memberships', 'create',
 'Registrar membresías',
 'Crear membresías nuevas dentro de la cadena de niveles.'),

-- Países — RF-SP-020 a RF-SP-022
('01a029fc-5d80-700c-9c4f-5e7ad000000c', 'countries:read', 'countries', 'read',
 'Consultar países',
 'Ver el listado de países con su estado y su moneda.'),
('01a029fc-5d80-700d-9c4f-5e7ad000000d', 'countries:create', 'countries', 'create',
 'Registrar países',
 'Dar de alta países nuevos en el catálogo.'),
('01a029fc-5d80-700e-9c4f-5e7ad000000e', 'countries:update', 'countries', 'update',
 'Modificar países',
 'Activar o desactivar un país del catálogo.'),

-- Monedas — RF-SP-019 y RF-SP-023
('01a029fc-5d80-700f-9c4f-5e7ad000000f', 'currencies:read', 'currencies', 'read',
 'Consultar monedas',
 'Ver el catálogo de monedas con su estado.'),
('01a029fc-5d80-7010-9c4f-5e7ad0000010', 'currencies:update', 'currencies', 'update',
 'Modificar monedas',
 'Activar o desactivar una moneda. Reservado a SUPERADMIN (security.md §4.4).'),

-- Usuarios — RF-SP-024 a RF-SP-038, y RF-SP-041
('01a029fc-5d80-7011-9c4f-5e7ad0000011', 'users:read', 'users', 'read',
 'Consultar usuarios',
 'Ver el listado de usuarios, el detalle de cada uno y el equipo comercial a su cargo.'),
('01a029fc-5d80-7012-9c4f-5e7ad0000012', 'users:create', 'users', 'create',
 'Registrar usuarios',
 'Dar de alta usuarios nuevos en el sistema.'),
('01a029fc-5d80-7013-9c4f-5e7ad0000013', 'users:update', 'users', 'update',
 'Modificar usuarios',
 'Editar los datos de un usuario y cambiar su estado.'),
('01a029fc-5d80-7014-9c4f-5e7ad0000014', 'users:delete', 'users', 'delete',
 'Eliminar usuarios',
 'Eliminar lógicamente un usuario, con motivo obligatorio (Art. V.13).'),
('01a029fc-5d80-7015-9c4f-5e7ad0000015', 'users:assign-roles', 'users', 'assign-roles',
 'Asignar roles a usuarios',
 'Asignar y retirar roles de un usuario, dentro de la cota de privilegios del propio actor.'),
('01a029fc-5d80-7016-9c4f-5e7ad0000016', 'users:assign-membership', 'users', 'assign-membership',
 'Asignar membresía a usuarios',
 'Asignar y retirar la membresía de un usuario.'),
('01a029fc-5d80-7017-9c4f-5e7ad0000017', 'users:reset-password', 'users', 'reset-password',
 'Restablecer contraseñas',
 'Restablecer la contraseña de otro usuario, que deberá cambiarla en su siguiente inicio de sesión.'),
('01a029fc-5d80-7018-9c4f-5e7ad0000018', 'users:assign-supervisor', 'users', 'assign-supervisor',
 'Asignar superior comercial',
 'Asignar o cambiar el superior comercial de un usuario, y con ello la estructura comercial de la que cuelga.');

-- OBLIGACIÓN PARA LA PRÓXIMA MIGRACIÓN QUE SIEMBRE PERMISOS (security.md §4.4):
-- sembrar un permiso no basta. La misma migración DEBE asociarlo a SUPERADMIN
-- y a ADMIN, o incumplirá §4.1 desde el momento en que se aplique.
-- V7__seed_system_roles.sql no puede hacerlo por ella, porque asocia el
-- catálogo existente en su momento y un permiso posterior todavía no estará.
--
-- Dos excepciones, reservadas a SUPERADMIN y que ADMIN NO recibe:
--   * audit:read-security   (security.md §4.4)
--   * currencies:update     (RF-SP-023 §5)
--
-- El síntoma de olvidarlo no es evidente: ADMIN quedaría incapaz de crear un
-- rol que declare ese permiso, y RN-SEG-003 rechazaría la operación sin decir
-- que lo que falta es una siembra.
