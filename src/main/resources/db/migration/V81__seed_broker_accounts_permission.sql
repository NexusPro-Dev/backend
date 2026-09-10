-- =============================================================================
-- `broker-accounts:read` — ver las cuentas de broker de CUALQUIER persona.
--
-- `RF-SP-055` · `T-02`. El catálogo pasa de cuarenta y cuatro permisos a
-- CUARENTA Y CINCO.
--
-- IDENTIFICADOR LITERAL Y NO GENERADO (Art. V.11): debe ser el mismo en todos
-- los entornos. La marca de tiempo del UUID v7 —`01a0889d-3800`— corresponde al
-- 10-09-2026, versión 7 y variante RFC 4122.
--
-- UN SOLO PERMISO, y aquí el motivo NO es el de los catálogos:
--
--   * No hay `broker-accounts:create` porque DECLARAR UNA CUENTA NO PASA POR
--     NINGÚN PERMISO: la declara su titular al registrarse por enlace, sin
--     sesión (`RN-SP-042`).
--   * No hay `broker-accounts:update` porque quien completa la cuenta es el
--     WEBHOOK DEL BROKER (`RF-SP-054`), que no es una persona y no porta roles.
--   * No hay `broker-accounts:delete` porque desvincular una cuenta sigue sin
--     decidirse, y por eso `user_brokers` tampoco lleva `deleted_at`.
--
-- ES EL PRIMER PERMISO DEL CATÁLOGO CUYO RECURSO NO ES UN CATÁLOGO ni un
-- agregado administrable: gobierna una LECTURA de datos ajenos. Y gobierna solo
-- una de las dos que nacen hoy — el listado del equipo (`RF-SP-056`) NO lo
-- exige, porque allí el alcance lo pone la estructura comercial y el actor no
-- puede nombrar a nadie (`RN-SP-046`).
--
-- SE ASOCIA A SUPERADMIN Y A ADMIN en esta misma migración, que `security.md`
-- §4.4 exige de toda migración que siembre permisos. Sin reserva para la raíz:
-- revisar las cuentas de broker de la red es administración ordinaria.
--
-- NO SE ASOCIA A NINGÚN ROL VENDEDOR, y es deliberado: el vendedor ya ve las
-- cuentas de SU gente sin permiso alguno. Concederle este le daría las de
-- TODAS, que es exactamente lo que `RN-SP-046` acota.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que `V3`, `V40`, `V48`, `V60`, `V72`
-- y `V75`: un permiso no tiene línea de tiempo que reconstruir.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a0889d-3800-7001-9c4f-5e7ada000002', 'broker-accounts:read', 'broker-accounts', 'read',
 'Consultar las cuentas de broker de cualquier persona',
 'Consultar las cuentas de broker de cualquier persona (RF-SP-055). Sin él, cada quien ve solo las de su equipo directo (RN-SP-046).');


-- -----------------------------------------------------------------------------
-- Asociación a los dos roles de sistema.
--
-- Se enumera por código y no con `SELECT ... FROM permissions`: ese atajo
-- asociaría también cualquier permiso sembrado antes que alguien hubiera
-- decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code = 'broker-accounts:read';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code = 'broker-accounts:read';


-- -----------------------------------------------------------------------------
-- Guarda: si alguna de las dos asociaciones no entró, la migración ABORTA.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'broker-accounts:read'
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 2 THEN
        RAISE EXCEPTION
            'V81: se esperaban 2 asociaciones de broker-accounts:read y hay %. '
            'Sin la de ADMIN, ese rol no puede conceder lo que no tiene.', filas;
    END IF;
END $$;
