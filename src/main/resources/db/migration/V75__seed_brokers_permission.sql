-- =============================================================================
-- `brokers:read` — el permiso de lectura del catálogo de brokers.
--
-- `RF-SP-052` · `T-03`. El catálogo pasa de cuarenta y tres permisos a
-- CUARENTA Y CUATRO.
--
-- IDENTIFICADOR LITERAL Y NO GENERADO (Art. V.11): debe ser el mismo en todos
-- los entornos. La marca de tiempo del UUID v7 —`01a081f0-6000`— corresponde al
-- 08-09-2026, versión 7 y variante RFC 4122.
--
-- UN SOLO PERMISO Y NO CUATRO, al revés que las tasas de cambio: este catálogo
-- NO se administra por API (`RN-SP-039`). Sembrar `brokers:create` «por
-- simetría» dejaría un permiso que nadie puede ejercer y que alguien acabaría
-- concediendo, creyendo que existe el endpoint.
--
-- SE ASOCIA A SUPERADMIN Y A ADMIN en esta misma migración, que `security.md`
-- §4.4 exige de toda migración que siembre permisos. Sin reserva para la raíz:
-- leer con qué brokers opera la plataforma es administración ordinaria.
--
-- NO SE ASOCIA A `CLIENTE`, y aquí eso deja un hueco que conviene nombrar: si
-- `RF-SP-053` acaba dejando que la persona declare su propia cuenta, necesitará
-- este catálogo para elegir el broker — y hoy no lo puede leer. Es el mismo
-- hueco que ya tienen países y tipos de documento con el registro público, y
-- ahora son tres. La decisión es del responsable del proyecto.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que `V3`, `V40`, `V48`, `V60` y
-- `V72`: un permiso no tiene línea de tiempo que reconstruir.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a081f0-6000-7001-9c4f-5e7ada000001', 'brokers:read', 'brokers', 'read',
 'Consultar el catálogo de brokers',
 'Consultar los brokers con los que opera la plataforma (RF-SP-052). El catálogo se puebla por migración y no se administra por API (RN-SP-039).');


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
 WHERE code = 'brokers:read';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code = 'brokers:read';


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
     WHERE p.code = 'brokers:read'
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 2 THEN
        RAISE EXCEPTION
            'V75: se esperaban 2 asociaciones de brokers:read y hay %. '
            'Sin la de ADMIN, ese rol no puede conceder lo que no tiene.', filas;
    END IF;
END $$;
