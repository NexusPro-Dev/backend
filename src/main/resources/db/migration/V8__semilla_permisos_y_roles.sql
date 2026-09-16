-- =============================================================================
-- V8 — Semilla de permisos y roles del sistema.
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11): tienen que ser iguales
-- en todos los entornos, porque las asociaciones de esta misma migración y las
-- pruebas los referencian. Son UUID v7 con marca de tiempo del día en que cada
-- permiso NACIÓ —por eso los prefijos difieren— y con un sufijo por módulo:
-- `5e7ad0` SP, `5e7ad5` PM, `5e7ad6` CM y tipos de documento, `5e7ad7` MV,
-- `5e7ad9` tasas de cambio, `5e7ada` brokers. Al consolidar el esquema el
-- 15-09-2026 NO se renumeró ninguno.
--
-- CADA MÓDULO ES DUEÑO DE LOS SUYOS (security.md §4.4): aquí van juntos por
-- ser una sola migración, y por eso están agrupados y comentados por módulo.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA POR LOS PERMISOS: son catálogo. Sí la emite
-- por los ROLES, porque son entidades del negocio y RF-SP-011 debe poder
-- mostrar su creación.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

-- ---- SP · roles, permisos y auditoría ----------------------------------------
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
('01a029fc-5d80-7005-9c4f-5e7ad0000005', 'permissions:read', 'permissions', 'read',
 'Consultar permisos',
 'Ver el catálogo de permisos del sistema y el detalle de cada uno.'),
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

-- ---- SP · catálogos ---------------------------------------------------------
('01a029fc-5d80-700a-9c4f-5e7ad000000a', 'memberships:read', 'memberships', 'read',
 'Consultar membresías',
 'Ver el listado de membresías y el detalle de cada una.'),
('01a029fc-5d80-700b-9c4f-5e7ad000000b', 'memberships:create', 'memberships', 'create',
 'Registrar membresías',
 'Crear membresías nuevas dentro de la cadena de niveles.'),
('01a029fc-5d80-700c-9c4f-5e7ad000000c', 'countries:read', 'countries', 'read',
 'Consultar países',
 'Ver el listado de países con su estado y su moneda.'),
('01a029fc-5d80-700d-9c4f-5e7ad000000d', 'countries:create', 'countries', 'create',
 'Registrar países',
 'Dar de alta países nuevos en el catálogo.'),
('01a029fc-5d80-700e-9c4f-5e7ad000000e', 'countries:update', 'countries', 'update',
 'Modificar países',
 'Activar o desactivar un país del catálogo.'),
('01a029fc-5d80-700f-9c4f-5e7ad000000f', 'currencies:read', 'currencies', 'read',
 'Consultar monedas',
 'Ver el catálogo de monedas con su estado.'),
('01a029fc-5d80-7010-9c4f-5e7ad0000010', 'currencies:update', 'currencies', 'update',
 'Modificar monedas',
 'Activar o desactivar una moneda. Reservado a SUPERADMIN (security.md §4.4).'),
('01a07e50-8000-7001-9c4f-5e7ad9000001', 'exchange-rates:read', 'exchange-rates', 'read',
 'Consultar las tasas de cambio',
 'Ver que tasas hay, cual rige hoy y cuales rigieron (RF-SP-048).'),
('01a07e50-8000-7002-9c4f-5e7ad9000002', 'exchange-rates:create', 'exchange-rates', 'create',
 'Registrar una tasa de cambio',
 'Declarar a cuanto se cambia una moneda por otra, y desde cuando (RF-SP-047).'),
('01a07e50-8000-7003-9c4f-5e7ad9000003', 'exchange-rates:update', 'exchange-rates', 'update',
 'Corregir una tasa de cambio',
 'Enmendar precio, vigencia y estado sin reescribir lo que ya se convirtio (RF-SP-049).'),
('01a07e50-8000-7004-9c4f-5e7ad9000004', 'exchange-rates:delete', 'exchange-rates', 'delete',
 'Retirar una tasa de cambio',
 'Sacar de circulacion, con motivo, una tasa que no debio existir (RF-SP-050).'),
-- Los tres siguientes son los únicos recursos sin ninguna acción de escritura,
-- y no por falta de tiempo: sus catálogos se pueblan por migración
-- (RN-SP-036, RN-SP-039). Desde el 08-09-2026 sus lecturas son públicas y estos
-- permisos no gobiernan ningún endpoint; se conservan porque retirarlos
-- rompería roles que los declaran.
('01a080e3-ae00-7005-9c4f-5e7ad6000005', 'document-types:read', 'document-types', 'read',
 'Consultar tipos de documento',
 'Consultar el catálogo de documentos de identidad admitidos (RF-SP-051). El catálogo no se administra por API: su contenido es la validación de mayoría de edad (RN-SP-036).'),
('01a081f0-6000-7001-9c4f-5e7ada000001', 'brokers:read', 'brokers', 'read',
 'Consultar el catálogo de brokers',
 'Consultar los brokers con los que opera la plataforma (RF-SP-052). El catálogo se puebla por migración y no se administra por API (RN-SP-039).'),
('01a0889d-3800-7001-9c4f-5e7ada000002', 'broker-accounts:read', 'broker-accounts', 'read',
 'Consultar las cuentas de broker de cualquier persona',
 'Consultar las cuentas de broker de cualquier persona (RF-SP-055). Sin él, cada quien ve solo las de su equipo directo (RN-SP-046).'),

-- ---- SP · personas ----------------------------------------------------------
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
 'Asignar o cambiar el superior comercial de un usuario, y con ello la estructura comercial de la que cuelga.'),

-- ---- PM · productos y paquetes ----------------------------------------------
('01a03a6a-9000-7001-9c4f-5e7ad5000001', 'products:create', 'products', 'create',
 'Registrar productos',
 'Dar de alta productos del catalogo: upgrades de membresia y servicios del sistema.'),
('01a03a6a-9000-7002-9c4f-5e7ad5000002', 'products:read', 'products', 'read',
 'Consultar productos',
 'Ver el catalogo completo, incluido lo inactivo y lo retirado, y el detalle de cada producto.'),
('01a03a6a-9000-7003-9c4f-5e7ad5000003', 'products:update', 'products', 'update',
 'Editar productos',
 'Corregir nombre, descripcion, precio, moneda y vigencia, y publicar o retirar de la venta.'),
('01a03a6a-9000-7004-9c4f-5e7ad5000004', 'products:delete', 'products', 'delete',
 'Eliminar productos',
 'Retirar un producto del catalogo con eliminacion logica y motivo obligatorio.'),
-- Los dos permisos de VISTA: quien vende ve lo que puede comprar (la oferta) y
-- quien reparte enlaces ve lo que puede repartir.
('01a05f6a-5800-7001-9c4f-5e7ad5000005', 'products:sale', 'products', 'sale',
 'Ver la vista de venta',
 'Consultar la oferta disponible para uno mismo: lo que el actor puede comprar hoy.'),
('01a0792a-2400-7001-9c4f-5e7ad5000006', 'products:hotlink', 'products', 'hotlink',
 'Ver la vista de hotlinks',
 'Consultar los productos cuyo alcance llega al canal de hotlinks (RN-PM-019).'),
-- El primer permiso de escritura de PM que no es de administración: habilita,
-- no autoriza — quien lo porta escribe LAS SUYAS (RN-PM-027).
('01a09d97-2400-7001-9c4f-5e7ad5000007', 'products:comment', 'products', 'comment',
 'Reseñar productos',
 'Escribir, corregir y retirar la reseña propia sobre un producto (RN-PM-025 a RN-PM-029).'),
-- Los paquetes son recurso propio, por decisión del responsable del proyecto:
-- portar los cuatro `products:` no habilita ni una operación de paquetes.
('01a0a25d-0400-7001-9c4f-5e7ad5000008', 'packages:create', 'packages', 'create',
 'Registrar paquetes',
 'Dar de alta paquetes de productos, que nacen vacios e inactivos y sin precio propio (RN-PM-036, RN-PM-041).'),
('01a0a25d-0400-7002-9c4f-5e7ad5000009', 'packages:read', 'packages', 'read',
 'Consultar paquetes',
 'Ver todos los paquetes, incluidos los inactivos y los retirados, con su precio calculado y por que no se ofrecen.'),
('01a0a25d-0400-7003-9c4f-5e7ad5000010', 'packages:update', 'packages', 'update',
 'Editar paquetes',
 'Corregir nombre, descripcion y alcance, publicar o despublicar, y asociar, corregir el descuento o desasociar sus productos.'),
('01a0a25d-0400-7004-9c4f-5e7ad5000011', 'packages:delete', 'packages', 'delete',
 'Eliminar paquetes',
 'Retirar un paquete con eliminacion logica y motivo obligatorio; sus productos no cambian.'),

-- ---- CM · comisiones --------------------------------------------------------
('01a03fb4-6800-7001-9c4f-5e7ad6000001', 'commissions:create', 'commissions', 'create',
 'Registrar tarifas de comision',
 'Declarar cuanto gana un rol vendedor, por producto y por persona, y desde cuando rige.'),
('01a03fb4-6800-7002-9c4f-5e7ad6000002', 'commissions:read', 'commissions', 'read',
 'Consultar tarifas de comision',
 'Ver las tarifas declaradas, incluido el historial, y resolver la comision efectiva.'),
('01a03fb4-6800-7003-9c4f-5e7ad6000003', 'commissions:update', 'commissions', 'update',
 'Corregir tarifas de comision',
 'Corregir el porcentaje de una tarifa y cerrar o reabrir su fin de vigencia.'),
('01a03fb4-6800-7004-9c4f-5e7ad6000004', 'commissions:delete', 'commissions', 'delete',
 'Eliminar tarifas de comision',
 'Retirar una tarifa con eliminacion logica y motivo obligatorio.'),

-- ---- MV · movimientos -------------------------------------------------------
('01a05f6a-5800-7001-9c4f-5e7ad7000001', 'movements:read', 'movements', 'read',
 'Consultar ventas',
 'Ver el listado de ventas y el detalle de cada una, con su comprobante.'),
('01a05f6a-5800-7002-9c4f-5e7ad7000002', 'movements:create', 'movements', 'create',
 'Registrar ventas',
 'Registrar una venta a nombre de otra persona, que nace pendiente y no concede nada.'),
('01a05f6a-5800-7003-9c4f-5e7ad7000003', 'movements:confirm', 'movements', 'confirm',
 'Confirmar o rechazar ventas',
 'Dar por pagada, o por no pagada, una venta pendiente.'),
('01a05f6a-5800-7004-9c4f-5e7ad7000004', 'movements:void', 'movements', 'void',
 'Anular ventas',
 'Anular una venta pendiente que no debia existir.');

-- Guarda: cincuenta, ni uno más ni uno menos. Las cuatro suites de SP que
-- cuentan el catálogo (`PermissionsSeedIT` y sus hermanas) esperan este número;
-- un permiso que aparezca sin que nadie las actualice es un permiso que nadie
-- revisó (security.md §4.4).
DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 50 THEN
        RAISE EXCEPTION 'V8: el catálogo de permisos debe tener 50 filas; tiene %', filas;
    END IF;
END $$;

-- =============================================================================
-- Roles de sistema (`is_system = true`): la raíz técnica, el máximo rol de
-- negocio, los tres rangos de la fuerza comercial y el consumidor. Un rol
-- creado por la API nunca es de sistema.
--
-- La jerarquía expresa CONTENCIÓN de privilegios (RN-SEG-003) y, entre los
-- vendedores, el ORDEN COMERCIAL (RN-SP-011): MANAGER > DIRECTOR > AGENTE.
-- Los vendedores y CLIENTE se siembran SIN PERMISOS a propósito: quien
-- administre roles se los concede por la vía normal (RF-SP-005, RF-SP-006).
-- =============================================================================

INSERT INTO roles (id, code, name, description, role_type, parent_role_id, status, is_system) VALUES

('01a02a33-4c00-7001-9c4f-5e7ad1000001', 'SUPERADMIN', 'Superadministrador',
 'Rol técnico del responsable del software. Es la raíz de la contención de privilegios: no tiene rol padre.',
 'FUNCIONARIO', NULL, 'ACTIVO', true),

('01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ADMIN', 'Administrador',
 'Máximo rol de negocio. Posee todo permiso que cualquier rol funcional declare, que es lo que hace viable RN-SEG-003 en la jerarquía que cuelga de él.',
 'FUNCIONARIO', '01a02a33-4c00-7001-9c4f-5e7ad1000001', 'ACTIVO', true),

('01a02a33-4c00-7005-9c4f-5e7ad1000003', 'MANAGER', 'Manager',
 'Rango superior de la fuerza comercial. Se siembra sin permisos, a la espera de RF-SP-005.',
 'VENDEDOR', '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', true),

('01a02a33-4c00-7006-9c4f-5e7ad1000004', 'DIRECTOR', 'Director',
 'Rango intermedio de la fuerza comercial. Se siembra sin permisos, a la espera de RF-SP-005.',
 'VENDEDOR', '01a02a33-4c00-7005-9c4f-5e7ad1000003', 'ACTIVO', true),

('01a02a33-4c00-7007-9c4f-5e7ad1000005', 'AGENTE', 'Agente o vendedor',
 'Rango base de la fuerza comercial. Se siembra sin permisos, a la espera de RF-SP-005.',
 'VENDEDOR', '01a02a33-4c00-7006-9c4f-5e7ad1000004', 'ACTIVO', true),

-- CLIENTE cuelga de la raíz y no de ADMIN: un consumidor no está dentro de la
-- contención de privilegios de la administración, porque no administra nada.
('01a02a33-4c00-7008-9c4f-5e7ad1000008', 'CLIENTE', 'Cliente',
 'Rol de negocio de consumidor. Se siembra sin permisos, a la espera de RF-SP-005.',
 'CONSUMIDOR', '01a02a33-4c00-7001-9c4f-5e7ad1000001', 'ACTIVO', true);

-- ---------------------------------------------------------------------------
-- SUPERADMIN acota el catálogo completo: todo permiso que exista.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id FROM permissions;

-- ---------------------------------------------------------------------------
-- ADMIN: todo salvo las TRES RESERVAS del superadministrador (security.md
-- §4.4) —la auditoría de seguridad, el estado de las monedas— y los cuatro
-- `movements:`, que gobiernan el libro de ventas y son reserva de la raíz.
-- Por exclusión y no por lista, a propósito: un permiso nuevo de
-- administración ordinaria le llega a ADMIN sin tocar esta migración, y solo
-- lo que se decida reservar hay que nombrarlo aquí.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code NOT IN ('audit:read-security', 'currencies:update',
                    'movements:read', 'movements:create', 'movements:confirm', 'movements:void');

DO $$
DECLARE
    de_raiz  integer;
    de_admin integer;
BEGIN
    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 50 OR de_admin <> 44 THEN
        RAISE EXCEPTION 'V8: SUPERADMIN debe declarar 50 permisos y ADMIN 44; tienen % y %', de_raiz, de_admin;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Auditoría de la creación de los roles de sistema (RF-SP-011), con los
-- identificadores que siempre tuvieron: los cinco primeros por orden
-- alfabético de código y CLIENTE, que nació después, con el sufijo 08.
-- ---------------------------------------------------------------------------

INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    (CASE r.code
        WHEN 'ADMIN'      THEN '01a02a33-4c00-7011-9c4f-5e7ad1000001'
        WHEN 'AGENTE'     THEN '01a02a33-4c00-7011-9c4f-5e7ad1000002'
        WHEN 'DIRECTOR'   THEN '01a02a33-4c00-7011-9c4f-5e7ad1000003'
        WHEN 'MANAGER'    THEN '01a02a33-4c00-7011-9c4f-5e7ad1000004'
        WHEN 'SUPERADMIN' THEN '01a02a33-4c00-7011-9c4f-5e7ad1000005'
        WHEN 'CLIENTE'    THEN '01a02a33-4c00-7011-9c4f-5e7ad1000008'
     END)::uuid,
    now(),
    NULL, NULL, NULL, NULL,
    'SP',
    'roles',
    r.id,
    'CREATE',
    jsonb_build_object(
        'code',           r.code,
        'name',           r.name,
        'description',    r.description,
        'role_type',      r.role_type,
        'parent_role_id', r.parent_role_id,
        'status',         r.status,
        'is_system',      r.is_system,
        'permissions',    COALESCE(
            (SELECT jsonb_agg(p.code ORDER BY p.code)
               FROM role_permissions rp
               JOIN permissions p ON p.id = rp.permission_id
              WHERE rp.role_id = r.id),
            '[]'::jsonb)
    )
  FROM roles r
 WHERE r.is_system = true;
