-- =============================================================================
-- V28 — Un permiso por operación (RF-SP-060, RN-SEG-014, security.md §4.4,
-- 19-09-2026).
--
-- Hasta hoy veintiún códigos gobernaban más de una operación: roles:update
-- cinco —editar el nombre, y también cambiar el estado, reubicar, ASIGNAR y
-- REVOCAR permisos—, courses:update diez, packages:update siete. Un permiso
-- que agrupa es un permiso que no se puede conceder a medias, y el catálogo
-- existe para los roles que se crean con una parte. Por decisión del
-- responsable del proyecto, ESTRICTO: también listado y detalle.
--
-- LO QUE HACE ESTA MIGRACIÓN, y en este orden:
--   1. Siembra los CINCUENTA Y UN permisos nuevos (el catálogo pasa de 60 a 111).
--   2. Da cada hijo a TODO ROL que porte el padre —los dos de sistema y
--      cualquiera creado a mano—, por pareja (rol, padre): nadie pierde nada y
--      RN-SEG-003 se conserva por construcción.
--   3. Reescribe nombre y descripción de los veintiún códigos que se estrechan,
--      para que no nombren lo que perdieron.
--   4. Comprueba, y aborta si no: 111 / SUPERADMIN 111 / ADMIN 105 / cero
--      parejas (rol, padre) sin alguno de sus hijos.
--
-- NINGÚN CÓDIGO SE RENOMBRA NI SE RETIRA: cada uno se queda con UNA operación.
--
-- IDENTIFICADORES LITERALES (Art. V.11): 01a0b6f6-7400-7NNN-9c4f-<serie><n>.
-- `01a0b6f67400` es la marca v7 del 19-09-2026; `7NNN` numera los cincuenta y
-- uno del 7001 al 7033 en el orden de spec.md §6.2; la serie es la del módulo
-- y continúa donde cada una quedó, en su propio estilo: SP `5e7ad0` (hex, como
-- V8) desde 000019; broker-accounts `5e7ada` desde 000003; PM `5e7ad5` desde
-- 000012; CM `5e7ad6` desde 000006 —la comparte con document-types:read—; AC
-- `5e7adc` desde 000011.
--
-- AC SE SIEMBRA AQUÍ Y SE ANOTA EN SUS CONTROLADORES DESPUÉS (tramo 3 del
-- plan): sus rutas siguen con el padre hasta que el bloque 4 de AC esté
-- construido. Sembrar sin ruta no rompe nada —products:hotlink y movements:
-- nacieron así— y evita partir la migración.
--
-- SIN AUDITORÍA, como V8, V19 y V22: el catálogo es datos, y las filas de
-- role_permissions que nacen aquí no las concedió nadie — las tenía todo el
-- mundo bajo otro nombre.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

-- ---- SP · roles (roles:read y roles:update se dividen) ----------------------
('01a0b6f6-7400-7001-9c4f-5e7ad0000019', 'roles:list', 'roles', 'list',
 'Listar roles',
 'Ver el listado de roles con sus filtros. El detalle de un rol es roles:read.'),
('01a0b6f6-7400-7002-9c4f-5e7ad000001a', 'roles:assign-permissions', 'roles', 'assign-permissions',
 'Asignar permisos a un rol',
 'Añadir permisos a un rol, dentro de la cota de su rol padre (RN-SEG-003) y de lo que el propio actor porta (RN-SEG-010). Hasta el 19-09-2026 lo cubría roles:update.'),
('01a0b6f6-7400-7003-9c4f-5e7ad000001b', 'roles:revoke-permissions', 'roles', 'revoke-permissions',
 'Revocar permisos de un rol',
 'Retirar permisos a un rol, siempre que ningún rol hijo los declare (RN-SEG-005). Hasta el 19-09-2026 lo cubría roles:update.'),
('01a0b6f6-7400-7004-9c4f-5e7ad000001c', 'roles:change-status', 'roles', 'change-status',
 'Cambiar el estado de un rol',
 'Activar o desactivar un rol. Hasta el 19-09-2026 lo cubría roles:update.'),
('01a0b6f6-7400-7005-9c4f-5e7ad000001d', 'roles:assign-parent', 'roles', 'assign-parent',
 'Cambiar el rol padre de un rol',
 'Reubicar un rol bajo otro padre, revalidando la contención (RN-SEG-013). Hasta el 19-09-2026 lo cubría roles:update.'),

-- ---- SP · permisos y membresías (los read se dividen en list y read) --------
('01a0b6f6-7400-7006-9c4f-5e7ad000001e', 'permissions:list', 'permissions', 'list',
 'Listar permisos',
 'Ver el catálogo de permisos del sistema. El detalle de uno es permissions:read.'),
('01a0b6f6-7400-7007-9c4f-5e7ad000001f', 'memberships:list', 'memberships', 'list',
 'Listar membresías',
 'Ver el listado de membresías. El detalle de una es memberships:read.'),

-- ---- SP · usuarios ----------------------------------------------------------
('01a0b6f6-7400-7008-9c4f-5e7ad0000020', 'users:list', 'users', 'list',
 'Listar usuarios',
 'Ver el listado de usuarios con sus filtros y su búsqueda. El detalle de uno es users:read.'),
('01a0b6f6-7400-7009-9c4f-5e7ad0000021', 'users:change-status', 'users', 'change-status',
 'Cambiar el estado de un usuario',
 'Activar o desactivar una cuenta y liberar un bloqueo. Hasta el 19-09-2026 lo cubría users:update.'),
('01a0b6f6-7400-700a-9c4f-5e7ad0000022', 'users:revoke-roles', 'users', 'revoke-roles',
 'Retirar roles de un usuario',
 'Quitar roles a una persona. Hasta el 19-09-2026 lo cubría users:assign-roles.'),
('01a0b6f6-7400-700b-9c4f-5e7ad0000023', 'users:revoke-membership', 'users', 'revoke-membership',
 'Devolver la membresía de un usuario al suelo',
 'Cerrar la membresía vigente de una persona y dejarla en el nivel suelo (RN-SP-018). Hasta el 19-09-2026 lo cubría users:assign-membership.'),
('01a0b6f6-7400-700c-9c4f-5e7ad0000024', 'users:read-team', 'users', 'read-team',
 'Consultar el equipo a cargo de un usuario',
 'Ver quiénes dependen comercialmente de una persona (RF-SP-042). Hasta el 19-09-2026 lo cubría users:read.'),

-- ---- SP · cuentas de broker -------------------------------------------------
('01a0b6f6-7400-700d-9c4f-5e7ada000003', 'broker-accounts:read-indicators', 'broker-accounts', 'read-indicators',
 'Consultar los indicadores de la red comercial',
 'Ver el árbol de la fuerza comercial con sus cuentas, primeros depósitos y conversión (RF-SP-058). Hasta el 19-09-2026 lo cubría broker-accounts:read.'),

-- ---- PM · productos ---------------------------------------------------------
('01a0b6f6-7400-700e-9c4f-5e7ad5000012', 'products:list', 'products', 'list',
 'Listar productos',
 'Ver el catálogo completo, incluido lo inactivo y lo retirado, con los dos precios. El detalle de uno es products:read.'),
('01a0b6f6-7400-700f-9c4f-5e7ad5000013', 'products:change-status', 'products', 'change-status',
 'Cambiar el estado de un producto',
 'Publicar o retirar de la venta un producto. Hasta el 19-09-2026 lo cubría products:update.'),
('01a0b6f6-7400-7010-9c4f-5e7ad5000014', 'products:set-cover', 'products', 'set-cover',
 'Subir o reemplazar la portada de un producto',
 'Poner portada a un producto o cambiarla. Hasta el 19-09-2026 lo cubría products:update.'),
('01a0b6f6-7400-7011-9c4f-5e7ad5000015', 'products:remove-cover', 'products', 'remove-cover',
 'Quitar la portada de un producto',
 'Dejar un producto sin portada. Hasta el 19-09-2026 lo cubría products:update.'),
('01a0b6f6-7400-7012-9c4f-5e7ad5000016', 'products:read-own-comments', 'products', 'read-own-comments',
 'Consultar la reseña propia sobre un producto',
 'Leer la reseña que uno mismo dejó sobre un producto (RF-PM-013). Hasta el 19-09-2026 lo cubría products:comment.'),
('01a0b6f6-7400-7013-9c4f-5e7ad5000017', 'products:update-comment', 'products', 'update-comment',
 'Corregir la reseña propia',
 'Corregir la reseña propia sobre un producto. Habilita, no autoriza: sobre una ajena responde 403 aunque lo porte un administrador (RN-PM-027). Hasta el 19-09-2026 lo cubría products:comment.'),
('01a0b6f6-7400-7014-9c4f-5e7ad5000018', 'products:delete-comment', 'products', 'delete-comment',
 'Retirar la reseña propia',
 'Retirar la reseña propia sobre un producto. Habilita, no autoriza (RN-PM-027). Hasta el 19-09-2026 lo cubría products:comment.'),

-- ---- PM · paquetes ----------------------------------------------------------
('01a0b6f6-7400-7015-9c4f-5e7ad5000019', 'packages:list', 'packages', 'list',
 'Listar paquetes',
 'Ver todos los paquetes, incluidos los inactivos y los retirados, con su precio calculado. El detalle de uno es packages:read.'),
('01a0b6f6-7400-7016-9c4f-5e7ad5000020', 'packages:change-status', 'packages', 'change-status',
 'Cambiar el estado de un paquete',
 'Publicar o despublicar un paquete. Hasta el 19-09-2026 lo cubría packages:update.'),
('01a0b6f6-7400-7017-9c4f-5e7ad5000021', 'packages:set-cover', 'packages', 'set-cover',
 'Subir o reemplazar la portada de un paquete',
 'Poner portada a un paquete o cambiarla. Hasta el 19-09-2026 lo cubría packages:update.'),
('01a0b6f6-7400-7018-9c4f-5e7ad5000022', 'packages:remove-cover', 'packages', 'remove-cover',
 'Quitar la portada de un paquete',
 'Dejar un paquete sin portada. Hasta el 19-09-2026 lo cubría packages:update.'),
('01a0b6f6-7400-7019-9c4f-5e7ad5000023', 'packages:add-product', 'packages', 'add-product',
 'Asociar un producto a un paquete',
 'Meter un producto en un paquete con su descuento (RF-PM-023). Hasta el 19-09-2026 lo cubría packages:update.'),
('01a0b6f6-7400-701a-9c4f-5e7ad5000024', 'packages:update-product', 'packages', 'update-product',
 'Corregir el descuento de un producto del paquete',
 'Cambiar el descuento con que un producto entra en un paquete (RF-PM-024). Hasta el 19-09-2026 lo cubría packages:update.'),
('01a0b6f6-7400-701b-9c4f-5e7ad5000025', 'packages:remove-product', 'packages', 'remove-product',
 'Desasociar un producto de un paquete',
 'Sacar un producto de un paquete (RF-PM-025). Hasta el 19-09-2026 lo cubría packages:update.'),

-- ---- CM · comisiones (commissions: se queda con las tasas de rol) -----------
('01a0b6f6-7400-701c-9c4f-5e7ad6000006', 'commissions:read-effective', 'commissions', 'read-effective',
 'Resolver la comisión efectiva',
 'Consultar qué comisión rige para una persona sobre un producto en una fecha (RF-CM-005). Hasta el 19-09-2026 lo cubría commissions:read.'),
('01a0b6f6-7400-701d-9c4f-5e7ad6000007', 'user-commission-rates:read', 'user-commission-rates', 'read',
 'Consultar tasas de comisión personalizadas',
 'Ver las tasas personalizadas por persona, con su vigencia. Hasta el 19-09-2026 lo cubría commissions:read.'),
('01a0b6f6-7400-701e-9c4f-5e7ad6000008', 'user-commission-rates:create', 'user-commission-rates', 'create',
 'Registrar tasas de comisión personalizadas',
 'Declarar la tasa personalizada de una persona sobre un producto (RF-CM-006). Hasta el 19-09-2026 lo cubría commissions:create.'),
('01a0b6f6-7400-701f-9c4f-5e7ad6000009', 'user-commission-rates:update', 'user-commission-rates', 'update',
 'Corregir tasas de comisión personalizadas',
 'Corregir el valor o la vigencia de una tasa personalizada. Hasta el 19-09-2026 lo cubría commissions:update.'),
('01a0b6f6-7400-7020-9c4f-5e7ad6000010', 'user-commission-rates:delete', 'user-commission-rates', 'delete',
 'Retirar tasas de comisión personalizadas',
 'Retirar una tasa personalizada con motivo. Hasta el 19-09-2026 lo cubría commissions:delete.'),
('01a0b6f6-7400-7021-9c4f-5e7ad6000011', 'product-commission-rates:read', 'product-commission-rates', 'read',
 'Consultar qué comisiona un producto',
 'Ver, por producto, qué tasas de rol lo comisionan. Hasta el 19-09-2026 lo cubría commissions:read.'),

-- ---- AC · categorías y cursos -----------------------------------------------
('01a0b6f6-7400-7022-9c4f-5e7adc000011', 'course-categories:list', 'course-categories', 'list',
 'Listar categorías de cursos',
 'Ver el listado de categorías, incluidas las retiradas. El detalle de una es course-categories:read.'),
('01a0b6f6-7400-7023-9c4f-5e7adc000012', 'courses:list', 'courses', 'list',
 'Listar cursos',
 'Ver el listado de cursos, con lo inactivo, lo retirado y lo que no se ofrece. El detalle de uno es courses:read.'),
('01a0b6f6-7400-7024-9c4f-5e7adc000013', 'courses:change-status', 'courses', 'change-status',
 'Cambiar el estado de un curso',
 'Activar o desactivar un curso. Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7025-9c4f-5e7adc000014', 'courses:assign-category', 'courses', 'assign-category',
 'Clasificar un curso',
 'Poner un curso en una categoría (RF-AC-016). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7026-9c4f-5e7adc000015', 'courses:revoke-category', 'courses', 'revoke-category',
 'Desclasificar un curso',
 'Sacar un curso de una categoría (RF-AC-017). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7027-9c4f-5e7adc000016', 'courses:assign-recommendation', 'courses', 'assign-recommendation',
 'Recomendar un curso previo',
 'Recomendar un curso como previo de otro (RF-AC-018). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7028-9c4f-5e7adc000017', 'courses:revoke-recommendation', 'courses', 'revoke-recommendation',
 'Retirar la recomendación de un curso previo',
 'Dejar de recomendar un curso como previo de otro (RF-AC-019). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7029-9c4f-5e7adc000018', 'courses:assign-membership', 'courses', 'assign-membership',
 'Dar visibilidad de un curso a una membresía',
 'Abrir un curso a una membresía (RF-AC-020). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-702a-9c4f-5e7adc000019', 'courses:revoke-membership', 'courses', 'revoke-membership',
 'Quitar la visibilidad de un curso a una membresía',
 'Cerrar un curso a una membresía (RF-AC-021). Hasta el 19-09-2026 lo cubría courses:update.'),

-- ---- AC · módulos y lecciones, recurso propio -------------------------------
('01a0b6f6-7400-702b-9c4f-5e7adc000020', 'course-modules:create', 'course-modules', 'create',
 'Registrar módulos de un curso',
 'Crear un módulo dentro de un curso (RF-AC-022). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-702c-9c4f-5e7adc000021', 'course-modules:update', 'course-modules', 'update',
 'Editar módulos de un curso',
 'Corregir un módulo (RF-AC-023). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-702d-9c4f-5e7adc000022', 'course-modules:change-status', 'course-modules', 'change-status',
 'Cambiar el estado de un módulo',
 'Activar o desactivar un módulo (RF-AC-024). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-702e-9c4f-5e7adc000023', 'course-modules:delete', 'course-modules', 'delete',
 'Eliminar módulos de un curso',
 'Retirar un módulo con motivo, arrastrando sus lecciones (RF-AC-025). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-702f-9c4f-5e7adc000024', 'lessons:create', 'lessons', 'create',
 'Registrar lecciones',
 'Crear una lección dentro de un módulo (RF-AC-028). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7030-9c4f-5e7adc000025', 'lessons:read', 'lessons', 'read',
 'Consultar el detalle de una lección',
 'Ver una lección con su contenido desde administración (RF-AC-036). Hasta el 19-09-2026 lo cubría courses:read.'),
('01a0b6f6-7400-7031-9c4f-5e7adc000026', 'lessons:update', 'lessons', 'update',
 'Editar lecciones',
 'Corregir una lección y su contenido (RF-AC-029). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7032-9c4f-5e7adc000027', 'lessons:change-status', 'lessons', 'change-status',
 'Cambiar el estado de una lección',
 'Activar o desactivar una lección (RF-AC-030). Hasta el 19-09-2026 lo cubría courses:update.'),
('01a0b6f6-7400-7033-9c4f-5e7adc000028', 'lessons:delete', 'lessons', 'delete',
 'Eliminar lecciones',
 'Retirar una lección con motivo (RF-AC-031). Hasta el 19-09-2026 lo cubría courses:update.');

-- -----------------------------------------------------------------------------
-- El reparto: cada hijo a TODO ROL que porte el padre, por pareja (rol, padre).
-- No hay lista de roles de sistema que mantener: SUPERADMIN y ADMIN reciben lo
-- suyo por la misma vía que cualquier rol creado a mano. ON CONFLICT permite
-- reejecutar el reparto si un módulo futuro lo necesita.
-- -----------------------------------------------------------------------------

CREATE TEMPORARY TABLE reparto_v28 (padre varchar(100) NOT NULL, hijo varchar(100) NOT NULL) ON COMMIT DROP;

INSERT INTO reparto_v28 (padre, hijo) VALUES
('roles:read',              'roles:list'),
('roles:update',            'roles:assign-permissions'),
('roles:update',            'roles:revoke-permissions'),
('roles:update',            'roles:change-status'),
('roles:update',            'roles:assign-parent'),
('permissions:read',        'permissions:list'),
('memberships:read',        'memberships:list'),
('users:read',              'users:list'),
('users:read',              'users:read-team'),
('users:update',            'users:change-status'),
('users:assign-roles',      'users:revoke-roles'),
('users:assign-membership', 'users:revoke-membership'),
('broker-accounts:read',    'broker-accounts:read-indicators'),
('products:read',           'products:list'),
('products:update',         'products:change-status'),
('products:update',         'products:set-cover'),
('products:update',         'products:remove-cover'),
('products:comment',        'products:read-own-comments'),
('products:comment',        'products:update-comment'),
('products:comment',        'products:delete-comment'),
('packages:read',           'packages:list'),
('packages:update',         'packages:change-status'),
('packages:update',         'packages:set-cover'),
('packages:update',         'packages:remove-cover'),
('packages:update',         'packages:add-product'),
('packages:update',         'packages:update-product'),
('packages:update',         'packages:remove-product'),
('commissions:read',        'commissions:read-effective'),
('commissions:read',        'user-commission-rates:read'),
('commissions:read',        'product-commission-rates:read'),
('commissions:create',      'user-commission-rates:create'),
('commissions:update',      'user-commission-rates:update'),
('commissions:delete',      'user-commission-rates:delete'),
('course-categories:read',  'course-categories:list'),
('courses:read',            'courses:list'),
('courses:read',            'lessons:read'),
('courses:update',          'courses:change-status'),
('courses:update',          'courses:assign-category'),
('courses:update',          'courses:revoke-category'),
('courses:update',          'courses:assign-recommendation'),
('courses:update',          'courses:revoke-recommendation'),
('courses:update',          'courses:assign-membership'),
('courses:update',          'courses:revoke-membership'),
('courses:update',          'course-modules:create'),
('courses:update',          'course-modules:update'),
('courses:update',          'course-modules:change-status'),
('courses:update',          'course-modules:delete'),
('courses:update',          'lessons:create'),
('courses:update',          'lessons:update'),
('courses:update',          'lessons:change-status'),
('courses:update',          'lessons:delete');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p_hijo.id
  FROM reparto_v28 r
  JOIN permissions p_padre ON p_padre.code = r.padre
  JOIN permissions p_hijo  ON p_hijo.code  = r.hijo
  JOIN role_permissions rp ON rp.permission_id = p_padre.id
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- -----------------------------------------------------------------------------
-- Los veintiún códigos que se estrechan: nombre y descripción de lo que queda.
-- -----------------------------------------------------------------------------

UPDATE permissions SET name = 'Consultar el detalle de un rol',
  description = 'Ver el detalle de un rol y los permisos que declara. El listado es roles:list.'
  WHERE code = 'roles:read';
UPDATE permissions SET name = 'Editar roles',
  description = 'Corregir nombre y descripción de un rol. El estado, el padre y los permisos tienen cada uno su código desde el 19-09-2026.'
  WHERE code = 'roles:update';
UPDATE permissions SET name = 'Consultar el detalle de un permiso',
  description = 'Ver el detalle de un permiso del catálogo. El listado es permissions:list.'
  WHERE code = 'permissions:read';
UPDATE permissions SET name = 'Consultar el detalle de una membresía',
  description = 'Ver el detalle de una membresía. El listado es memberships:list.'
  WHERE code = 'memberships:read';
UPDATE permissions SET name = 'Consultar el detalle de un usuario',
  description = 'Ver el detalle de una persona. El listado es users:list y el equipo a cargo users:read-team.'
  WHERE code = 'users:read';
UPDATE permissions SET name = 'Editar usuarios',
  description = 'Corregir los datos de una persona. El estado es users:change-status.'
  WHERE code = 'users:update';
UPDATE permissions SET name = 'Asignar roles a un usuario',
  description = 'Dar roles a una persona, dentro de lo que el propio actor porta (RN-SEG-010). Retirarlos es users:revoke-roles.'
  WHERE code = 'users:assign-roles';
UPDATE permissions SET name = 'Asignar membresía a un usuario',
  description = 'Fijar o cambiar la membresía vigente de una persona (RN-SP-014). Devolverla al suelo es users:revoke-membership.'
  WHERE code = 'users:assign-membership';
UPDATE permissions SET name = 'Consultar las cuentas de broker de cualquier persona',
  description = 'Consultar las cuentas de broker de cualquier persona (RF-SP-055, RF-SP-057). Sin él, cada quien ve solo las de su equipo directo (RN-SP-046). Los indicadores son broker-accounts:read-indicators.'
  WHERE code = 'broker-accounts:read';
UPDATE permissions SET name = 'Consultar el detalle de un producto',
  description = 'Ver el detalle de un producto, con los dos precios. El listado es products:list.'
  WHERE code = 'products:read';
UPDATE permissions SET name = 'Editar productos',
  description = 'Corregir nombre, descripción, precio, moneda, alcance y vigencia. El estado y la portada tienen cada uno su código desde el 19-09-2026.'
  WHERE code = 'products:update';
UPDATE permissions SET name = 'Reseñar productos',
  description = 'Escribir la reseña propia sobre un producto (RN-PM-025 a RN-PM-029). Leerla, corregirla y retirarla tienen cada una su código desde el 19-09-2026.'
  WHERE code = 'products:comment';
UPDATE permissions SET name = 'Consultar el detalle de un paquete',
  description = 'Ver el detalle de un paquete, con su precio calculado y por qué no se ofrece. El listado es packages:list.'
  WHERE code = 'packages:read';
UPDATE permissions SET name = 'Editar paquetes',
  description = 'Corregir nombre, descripción, alcance y vigencia. El estado, la portada y los productos del paquete tienen cada uno su código desde el 19-09-2026.'
  WHERE code = 'packages:update';
UPDATE permissions SET name = 'Consultar tasas de comisión por rol',
  description = 'Ver las tasas de rol, cada una con su producto. Las personalizadas son user-commission-rates:read, la vista por producto product-commission-rates:read y la resolución commissions:read-effective.'
  WHERE code = 'commissions:read';
UPDATE permissions SET name = 'Registrar tasas de comisión por rol',
  description = 'Declarar cuánto gana un rol vendedor por un producto. Las personalizadas son user-commission-rates:create.'
  WHERE code = 'commissions:create';
UPDATE permissions SET name = 'Corregir tasas de comisión por rol',
  description = 'Corregir el valor de una tasa de rol. Las personalizadas son user-commission-rates:update.'
  WHERE code = 'commissions:update';
UPDATE permissions SET name = 'Eliminar tasas de comisión por rol',
  description = 'Retirar una tasa de rol con motivo. Las personalizadas son user-commission-rates:delete.'
  WHERE code = 'commissions:delete';
UPDATE permissions SET name = 'Consultar el detalle de una categoría de cursos',
  description = 'Ver el detalle de una categoría, incluidas las retiradas. El listado es course-categories:list.'
  WHERE code = 'course-categories:read';
UPDATE permissions SET name = 'Consultar el detalle de un curso',
  description = 'Ver el detalle completo de un curso —con lo inactivo, lo retirado y lo que no se ofrece— y su árbol. El listado es courses:list y el contenido de una lección lessons:read.'
  WHERE code = 'courses:read';
UPDATE permissions SET name = 'Editar cursos',
  description = 'Corregir un curso. El estado, las relaciones, los módulos y las lecciones tienen cada uno su código desde el 19-09-2026.'
  WHERE code = 'courses:update';

-- -----------------------------------------------------------------------------
-- Las guardas.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas     integer;
    de_raiz   integer;
    de_admin  integer;
    a_medias  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 111 THEN
        RAISE EXCEPTION 'V28: el catálogo debe tener 111 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 111 OR de_admin <> 105 THEN
        RAISE EXCEPTION 'V28: SUPERADMIN debe portar 111 permisos y ADMIN 105 (la reserva son seis); tienen % y %', de_raiz, de_admin;
    END IF;

    -- Parejas (rol, padre) sin alguno de sus hijos: es la guarda que vale para
    -- los roles creados a mano, que V8 no siembra.
    SELECT count(*) INTO a_medias
      FROM reparto_v28 r
      JOIN permissions p_padre ON p_padre.code = r.padre
      JOIN permissions p_hijo  ON p_hijo.code  = r.hijo
      JOIN role_permissions rp ON rp.permission_id = p_padre.id
     WHERE NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = rp.role_id AND x.permission_id = p_hijo.id);
    IF a_medias <> 0 THEN
        RAISE EXCEPTION 'V28: % parejas (rol, padre) quedaron sin alguno de sus hijos', a_medias;
    END IF;
END $$;
