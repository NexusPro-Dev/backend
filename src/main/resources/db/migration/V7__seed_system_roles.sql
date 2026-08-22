-- =============================================================================
-- RF-SP-001 · T-04 — Siembra de los roles de sistema.
--
-- Puebla el catálogo aprobado de `requirements/sp.md` §4.1 para los roles con
-- is_system = true. `ESTUDIANTE` y `CLIENTE` quedan fuera: están marcados
-- is_system = false, es decir, son roles de negocio que se crean por la API,
-- que es precisamente lo que este requerimiento habilita.
--
-- El rol RAÍZ se puebla aquí y no por la API: `RN-SP-002` exige rol padre y
-- `RN-SEG-007` admite uno solo sin él, de modo que crearlo por el endpoint
-- obligaría a aceptar parentRoleId nulo y a añadir una rama que se ejecuta una
-- sola vez en la vida del sistema pero queda expuesta para siempre. Es un dato
-- de instalación y su lugar es una migración.
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11). El identificador de
-- SUPERADMIN debe ser el mismo en todos los entornos para que las pruebas y las
-- migraciones posteriores puedan referenciarlo por constante. Son UUID v7 con
-- marca de tiempo 2026-08-22T16:00:00Z (01a02a33-4c00), versión 7 y variante
-- RFC 9562, generados una sola vez al redactar esta migración y monótonos en el
-- orden en que se listan.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Los siete roles
--
-- `role_type` no lo declara §4.1, que solo fija código, nombre, padre e
-- is_system. Se deriva del dominio de `requirements/sp.md` §10.2: FUNCIONARIO es
-- personal interno y VENDEDOR es la fuerza comercial, y §10.2 identifica como
-- fuerza comercial exactamente a manager, director y agente.
--
-- El orden de inserción respeta la clave foránea al padre: cada rol se inserta
-- después del suyo.
-- -----------------------------------------------------------------------------
INSERT INTO roles (id, code, name, description, role_type, parent_role_id, status, is_system) VALUES

('01a02a33-4c00-7001-9c4f-5e7ad1000001', 'SUPERADMIN', 'Superadministrador',
 'Rol técnico del responsable del software. Es la raíz de la contención de privilegios: no tiene rol padre.',
 'FUNCIONARIO', NULL, 'ACTIVO', true),

('01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ADMIN', 'Administrador',
 'Máximo rol de negocio. Posee todo permiso que cualquier rol funcional declare, que es lo que hace viable RN-SEG-003 en la jerarquía que cuelga de él.',
 'FUNCIONARIO', '01a02a33-4c00-7001-9c4f-5e7ad1000001', 'ACTIVO', true),

('01a02a33-4c00-7003-9c4f-5e7ad1000003', 'CONTABILIDAD', 'Contabilidad',
 'Área contable. Consume roles y lee la auditoría de cambios y de eliminación; no administra roles.',
 'FUNCIONARIO', '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', true),

('01a02a33-4c00-7004-9c4f-5e7ad1000004', 'LIDER_ACADEMICO', 'Líder académico',
 'Responsable del área académica. Se siembra sin permisos, a la espera de RF-SP-005.',
 'FUNCIONARIO', '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', true),

('01a02a33-4c00-7005-9c4f-5e7ad1000005', 'MANAGER', 'Manager',
 'Rango superior de la fuerza comercial. Se siembra sin permisos, a la espera de RF-SP-005.',
 'VENDEDOR', '01a02a33-4c00-7002-9c4f-5e7ad1000002', 'ACTIVO', true),

('01a02a33-4c00-7006-9c4f-5e7ad1000006', 'DIRECTOR', 'Director',
 'Rango intermedio de la fuerza comercial. Se siembra sin permisos, a la espera de RF-SP-005.',
 'VENDEDOR', '01a02a33-4c00-7005-9c4f-5e7ad1000005', 'ACTIVO', true),

('01a02a33-4c00-7007-9c4f-5e7ad1000007', 'AGENTE', 'Agente o vendedor',
 'Rango base de la fuerza comercial. Se siembra sin permisos, a la espera de RF-SP-005.',
 'VENDEDOR', '01a02a33-4c00-7006-9c4f-5e7ad1000006', 'ACTIVO', true);


-- -----------------------------------------------------------------------------
-- 2. Permisos de SUPERADMIN — el catálogo completo
--
-- Lo exige `RN-SEG-007`: la raíz de la contención debe poder acotar a
-- cualquiera. Se asocia por SELECT y no enumerando identificadores para que un
-- permiso añadido al catálogo no deje a la raíz por detrás de sus hijos.
-- -----------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id FROM permissions;


-- -----------------------------------------------------------------------------
-- 3. Permisos de ADMIN — el catálogo completo SALVO DOS
--
-- Cumple la obligación de `security.md` §4.1 —poseer todo permiso que cualquier
-- rol de negocio declare— y deja a SUPERADMIN una reserva propia. Sin esa
-- reserva, ADMIN y SUPERADMIN serían indistinguibles salvo por ser uno la raíz.
--
--   audit:read-security  Es el registro donde quedan los intentos de escalada
--                        de privilegios. Un ADMIN que pudiera leerlo comprobaría
--                        si su propio intento quedó registrado.
--   currencies:update    `RF-SP-023` declara un único actor, el Super
--                        Administrador: el estado de una moneda condiciona todo
--                        cálculo financiero.
--
-- La consecuencia se acepta en ambos casos: ADMIN no puede crear un rol que
-- declare un permiso que él no tiene, porque `RN-SEG-003` lo rechazaría. Quien
-- no puede hacer algo tampoco debería poder delegarlo.
-- -----------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code NOT IN ('audit:read-security', 'currencies:update');


-- -----------------------------------------------------------------------------
-- 4. Permisos de CONTABILIDAD
--
-- Lo único que `requirements/sp.md` §4 documenta para él. Los cuatro registros
-- de auditoría no tienen la misma sensibilidad y no se leen en bloque
-- (`security.md` §4.4).
--
-- LIDER_ACADEMICO, MANAGER, DIRECTOR y AGENTE se siembran SIN permisos, a la
-- espera de `RF-SP-005`: sembrarlos a ojo produciría un catálogo que nadie
-- aprobó y que quedaría como referencia.
-- -----------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7003-9c4f-5e7ad1000003', id
  FROM permissions
 WHERE code IN ('audit:read-changes', 'audit:read-deletions');


-- -----------------------------------------------------------------------------
-- 5. Auditoría del poblado — una fila por rol
--
-- Con actor_id, correlation_id e ip_address en NULL: es la forma correcta de
-- decir «lo creó el sistema, no una persona» (Art. V.15), y evita que los
-- únicos roles del sistema sean también los únicos sin respuesta a «quién los
-- creó» (Art. V.7, V.8).
--
-- `changes` lleva el ESTADO INICIAL completo y no un diff con `before` en null
-- (architecture.md §6.6.2), incluidos los códigos de permiso declarados: las
-- filas de role_permissions son parte del estado inicial del agregado. Se
-- construye leyendo lo que quedó insertado y no repitiendo los literales, de
-- modo que el evento no pueda describir algo distinto de lo que hay.
--
-- Los identificadores de estas filas comparten la marca de tiempo de los roles
-- y usan la serie 7011 en adelante para no colisionar con ellos.
-- -----------------------------------------------------------------------------
INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    ('01a02a33-4c00-7011-9c4f-5e7ad10000'
        || lpad(row_number() OVER (ORDER BY r.created_at, r.code)::text, 2, '0'))::uuid,
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
