-- =============================================================================
-- V90 — Los permisos para gestionar cuentas de broker (RF-SP-053, RF-SP-080 y
-- RF-SP-081 · T-01; requirements/sp.md v1.111.0, RN-SP-067; security.md §4.4
-- v0.126.0; 08-10-2026).
--
-- Registrar, corregir y borrar cuentas de broker, las propias y las de
-- cualquiera. Seis permisos, uno por operación y alcance (RN-SEG-014):
--
--   * Tres PROPIOS —create-own, update-own, delete-own— a TODO ROL POR SU TIPO,
--     los tres tipos, como broker-accounts:read-own de V89.
--   * Tres AMPLIOS —create, update, delete— a SUPERADMIN y ADMIN explícitos.
--
-- El superior comercial no recibe ninguno: ve (RN-SP-046), no gestiona.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 de V79 (01a10e829000),
-- secuencias 7015 a 701a, y la serie de broker-accounts donde V89 la dejó
-- (000006 → 000007 a 00000c).
--
-- GUARDAS: 216 en el catálogo / SUPERADMIN 216 / ADMIN 214 / CLIENTE porta los
-- tres propios y ninguno amplio / cero filas que rompan RN-SEG-003.
--
-- SIN AUDITORÍA, como V89.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a10e82-9000-7015-9c4f-5e7ada000007', 'broker-accounts:create-own', 'broker-accounts',
 'create-own',
 'Registrar una cuenta de broker propia',
 'Declarar una cuenta de broker a nombre propio por POST /users/me/broker-accounts (RF-SP-053). A nombre de otra persona es broker-accounts:create.'),
('01a10e82-9000-7016-9c4f-5e7ada000008', 'broker-accounts:update-own', 'broker-accounts',
 'update-own',
 'Corregir una cuenta de broker propia',
 'Corregir el identificador de una cuenta propia sin depósito confirmado por PATCH /users/me/broker-accounts/{id} (RF-SP-080, RN-SP-067).'),
('01a10e82-9000-7017-9c4f-5e7ada000009', 'broker-accounts:delete-own', 'broker-accounts',
 'delete-own',
 'Eliminar una cuenta de broker propia',
 'Borrar una cuenta propia sin depósito confirmado por DELETE /users/me/broker-accounts/{id} (RF-SP-081, RN-SP-067).'),
('01a10e82-9000-7018-9c4f-5e7ada00000a', 'broker-accounts:create', 'broker-accounts',
 'create',
 'Registrar una cuenta de broker a una persona',
 'Declarar una cuenta de broker a nombre de cualquier persona por POST /users/{id}/broker-accounts (RF-SP-053).'),
('01a10e82-9000-7019-9c4f-5e7ada00000b', 'broker-accounts:update', 'broker-accounts',
 'update',
 'Corregir una cuenta de broker de una persona',
 'Corregir el identificador de cualquier cuenta, también con depósito confirmado, por PATCH /users/{id}/broker-accounts/{id} (RF-SP-080).'),
('01a10e82-9000-701a-9c4f-5e7ada00000c', 'broker-accounts:delete', 'broker-accounts',
 'delete',
 'Eliminar una cuenta de broker de una persona',
 'Borrar cualquier cuenta, también con depósito confirmado, por DELETE /users/{id}/broker-accounts/{id} (RF-SP-081).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
 CROSS JOIN permissions p
 WHERE r.role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')
   AND p.id IN ('01a10e82-9000-7015-9c4f-5e7ada000007',
                '01a10e82-9000-7016-9c4f-5e7ada000008',
                '01a10e82-9000-7017-9c4f-5e7ada000009')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
 CROSS JOIN permissions p
 WHERE p.id IN ('01a10e82-9000-7018-9c4f-5e7ada00000a',
                '01a10e82-9000-7019-9c4f-5e7ada00000b',
                '01a10e82-9000-701a-9c4f-5e7ada00000c')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas       integer;
    de_raiz     integer;
    de_admin    integer;
    propios     integer;
    amplios     integer;
    sin_padre   integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 216 THEN
        RAISE EXCEPTION 'V90: el catálogo debe tener 216 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 216 OR de_admin <> 214 THEN
        RAISE EXCEPTION 'V90: SUPERADMIN debe portar 216 permisos y ADMIN 214; tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) FILTER (WHERE p.code LIKE '%-own'),
           count(*) FILTER (WHERE p.code IN ('broker-accounts:create', 'broker-accounts:update',
                                             'broker-accounts:delete'))
      INTO propios, amplios
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE rp.role_id = '01a02a33-4c00-7008-9c4f-5e7ad1000008'
       AND p.code IN ('broker-accounts:create-own', 'broker-accounts:update-own',
                      'broker-accounts:delete-own', 'broker-accounts:create',
                      'broker-accounts:update', 'broker-accounts:delete');
    IF propios <> 3 OR amplios <> 0 THEN
        RAISE EXCEPTION 'V90: CLIENTE debe portar los tres propios y ninguno amplio; porta % y %', propios, amplios;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V90: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
