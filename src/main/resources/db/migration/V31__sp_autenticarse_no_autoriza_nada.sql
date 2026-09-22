-- =============================================================================
-- V31 — Autenticarse no autoriza nada (RF-SP-062, RN-SEG-015, security.md §4.3
-- y §4.4, 21-09-2026).
--
-- Hasta hoy once operaciones se atendían con solo el token —el propio perfil
-- y su corrección, la propia contraseña, mis vendedores, mis clientes, las
-- cuentas de broker de mi equipo y las de una persona a cargo, mis
-- movimientos y su detalle, mis productos comprados y la compra propia de un
-- paquete—, cada una con el argumento «alcance sobre uno mismo: no hay nada
-- que autorizar más allá de estar autenticado». El responsable del proyecto lo
-- rebatió con el uso que le da al catálogo: «cada endpoint debe tener su
-- propio permiso, ya que uso esto para saber qué vista o consulta mostrar en
-- el front; no basta con solo tener el token».
--
-- LO QUE HACE ESTA MIGRACIÓN, y en este orden:
--   1. Siembra los ONCE permisos, con `own` para el alcance sobre uno mismo
--      (el catálogo pasa de 113 a 124).
--   2. Los da a TODO ROL —de sistema o creado a mano— POR SU TIPO: FUNCIONARIO
--      y VENDEDOR reciben los once; CONSUMIDOR recibe ocho, no los tres de
--      vendedor (mis clientes, las cuentas de mi equipo, las de una persona a
--      cargo), que el frontend no debe ofrecerle a un cliente. Es la lógica de
--      V28 —cada hijo a todo rol que portara el padre— con «estar autenticado»
--      como padre: nadie pierde nada de lo que podía hacer ayer.
--   3. Comprueba, y aborta si no: 124 / SUPERADMIN 124 / ADMIN 118 / CLIENTE
--      ocho de los once / cero roles con un permiso que su padre no porte
--      (RN-SEG-003).
--
-- NINGÚN CÓDIGO SE RENOMBRA NI SE RETIRA: los once son nuevos y ninguno
-- gobierna otra operación (RN-SEG-014).
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 21-09-2026 que V29
-- estrenó (01a0c1432c00), secuencias 7001 a 700b, y la serie de cada módulo
-- continuando donde quedó: SP 5e7ad0 desde 000027 (cinco de users);
-- broker-accounts 5e7ada desde 000004 (dos); MV 5e7ad7 desde 000005 (tres);
-- PM 5e7ad5 en 000026 (packages:buy).
--
-- SIN AUDITORÍA, como V8, V22, V28, V29 y V30: las filas de role_permissions
-- que nacen aquí no las concedió nadie — las tenía todo el mundo bajo el token.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0c143-2c00-7001-9c4f-5e7ad0000027', 'users:read-own-profile', 'users', 'read-own-profile',
 'Consultar el propio perfil',
 'Ver la propia ficha por GET /users/me (RF-SP-039). Alcance sobre uno mismo: no abre la ficha de nadie más.'),
('01a0c143-2c00-7002-9c4f-5e7ad0000028', 'users:update-own-profile', 'users', 'update-own-profile',
 'Corregir el propio perfil',
 'Corregir los datos propios por PATCH /users/me (RF-SP-044). La ficha ajena es users:update.'),
('01a0c143-2c00-7003-9c4f-5e7ad0000029', 'users:change-own-password', 'users', 'change-own-password',
 'Cambiar la propia contraseña',
 'Cambiar la contraseña propia por POST /auth/password (RF-SP-037), también cuando el cambio es obligatorio. Restablecer la ajena es users:reset-password.'),
('01a0c143-2c00-7004-9c4f-5e7ad000002a', 'users:read-own-sellers', 'users', 'read-own-sellers',
 'Consultar mis vendedores',
 'Ver los propios vendedores —el principal y los vinculados— por GET /users/me/sellers (RF-SP-059). Los de otra persona son users:read-sellers.'),
('01a0c143-2c00-7005-9c4f-5e7ad000002b', 'users:read-own-clients', 'users', 'read-own-clients',
 'Consultar mis clientes',
 'Ver la propia cartera por GET /users/me/clients (RF-SP-061). La de otro vendedor es users:read-clients.'),
('01a0c143-2c00-7006-9c4f-5e7ada000004', 'broker-accounts:read-own-team', 'broker-accounts', 'read-own-team',
 'Consultar las cuentas de broker de mi equipo',
 'Ver, paginadas, las cuentas de broker del equipo directo y de los clientes propios por GET /users/me/team/broker-accounts (RF-SP-056). Todas las cuentas son broker-accounts:read.'),
('01a0c143-2c00-7007-9c4f-5e7ada000005', 'broker-accounts:read-team-member', 'broker-accounts', 'read-team-member',
 'Consultar las cuentas de broker de una persona a cargo',
 'Ver las cuentas de broker de una persona por GET /users/{id}/broker-accounts (RF-SP-055). El permiso abre la ruta; quién es visible lo decide la estructura (RN-SP-046): el subordinado directo o el cliente propio, y nadie más.'),
('01a0c143-2c00-7008-9c4f-5e7ad7000005', 'movements:list-own', 'movements', 'list-own',
 'Consultar mis movimientos',
 'Ver el listado de los propios movimientos por GET /movements/mine (RF-MV-008). El detalle es movements:read-own; el libro entero, movements:read.'),
('01a0c143-2c00-7009-9c4f-5e7ad7000006', 'movements:read-own', 'movements', 'read-own',
 'Consultar el detalle de un movimiento propio',
 'Ver el detalle de un movimiento propio por GET /movements/mine/{id} (RF-MV-008). El listado es movements:list-own.'),
('01a0c143-2c00-700a-9c4f-5e7ad7000007', 'movements:read-own-products', 'movements', 'read-own-products',
 'Consultar mis productos comprados',
 'Ver los productos propios comprados y su estado por GET /movements/mine/products (RF-MV-014).'),
('01a0c143-2c00-700b-9c4f-5e7ad5000026', 'packages:buy', 'packages', 'buy',
 'Comprar un paquete para uno mismo',
 'Comprar un paquete por la tienda para la propia cuenta por POST /packages/{code}/purchases (RF-MV-012). Registrar una venta a otra persona es movements:create.');

-- ---------------------------------------------------------------------------
-- El reparto, por TIPO de rol y no por código: alcanza a los roles creados a
-- mano, que V8 no siembra. ON CONFLICT por si alguien concedió uno a mano
-- entre dos arranques.
-- ---------------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r
  CROSS JOIN permissions p
 WHERE p.id IN ('01a0c143-2c00-7001-9c4f-5e7ad0000027',
                    '01a0c143-2c00-7002-9c4f-5e7ad0000028',
                    '01a0c143-2c00-7003-9c4f-5e7ad0000029',
                    '01a0c143-2c00-7004-9c4f-5e7ad000002a',
                    '01a0c143-2c00-7005-9c4f-5e7ad000002b',
                    '01a0c143-2c00-7006-9c4f-5e7ada000004',
                    '01a0c143-2c00-7007-9c4f-5e7ada000005',
                    '01a0c143-2c00-7008-9c4f-5e7ad7000005',
                    '01a0c143-2c00-7009-9c4f-5e7ad7000006',
                    '01a0c143-2c00-700a-9c4f-5e7ad7000007',
                    '01a0c143-2c00-700b-9c4f-5e7ad5000026')
   AND (r.role_type IN ('FUNCIONARIO', 'VENDEDOR')
        OR (r.role_type = 'CONSUMIDOR'
            AND p.code NOT IN ('users:read-own-clients', 'broker-accounts:read-own-team', 'broker-accounts:read-team-member')))
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas      integer;
    de_raiz    integer;
    de_admin   integer;
    de_cliente integer;
    sin_padre  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 124 THEN
        RAISE EXCEPTION 'V31: el catálogo debe tener 124 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 124 OR de_admin <> 118 THEN
        RAISE EXCEPTION 'V31: SUPERADMIN debe portar 124 permisos y ADMIN 118 (la reserva son seis); tienen % y %', de_raiz, de_admin;
    END IF;

    SELECT count(*) INTO de_cliente
      FROM role_permissions rp
     WHERE rp.role_id = '01a02a33-4c00-7008-9c4f-5e7ad1000008'
       AND rp.permission_id IN ('01a0c143-2c00-7001-9c4f-5e7ad0000027',
                    '01a0c143-2c00-7002-9c4f-5e7ad0000028',
                    '01a0c143-2c00-7003-9c4f-5e7ad0000029',
                    '01a0c143-2c00-7004-9c4f-5e7ad000002a',
                    '01a0c143-2c00-7005-9c4f-5e7ad000002b',
                    '01a0c143-2c00-7006-9c4f-5e7ada000004',
                    '01a0c143-2c00-7007-9c4f-5e7ada000005',
                    '01a0c143-2c00-7008-9c4f-5e7ad7000005',
                    '01a0c143-2c00-7009-9c4f-5e7ad7000006',
                    '01a0c143-2c00-700a-9c4f-5e7ad7000007',
                    '01a0c143-2c00-700b-9c4f-5e7ad5000026');
    IF de_cliente <> 8 THEN
        RAISE EXCEPTION 'V31: CLIENTE debe portar ocho de los once; porta %', de_cliente;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V31: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
