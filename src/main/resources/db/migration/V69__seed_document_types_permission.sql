-- =============================================================================
-- RF-SP-051 · T-03 — Siembra de `document-types:read`.
--
-- ES EL ÚNICO RECURSO DEL CATÁLOGO DE PERMISOS CON UNA SOLA ACCIÓN Y NINGUNA DE
-- ESCRITURA, y no es que falten por escribir: `RN-SP-036` las prohíbe.
--
-- El motivo no es simetría con `RN-SP-010` —que deja las monedas fuera de la
-- API porque son un catálogo estable— sino una NECESIDAD: el contenido de
-- `document_types` ES la validación de mayoría de edad (`V67`), de modo que un
-- `document-types:create` dejaría que cualquiera con ese permiso añadiera
-- «Tarjeta de Identidad» y LA VALIDACIÓN DESAPARECERÍA sin cambiar ninguna
-- regla, sin migración y sin que nadie lo notara.
--
-- TAMPOCO SE SIEMBRA `document-types:update`, que sería lo simétrico con
-- `countries:update` y `currencies:update` para mover `is_active`. Retirar un
-- tipo de documento es raro y grave; que cueste una migración revisada es el
-- precio correcto. Y un permiso declarado sin operación acaba concediéndose en
-- una revisión de roles — y el día que alguien escriba la operación se
-- encontrará con que ya hay quien la puede usar.
--
-- IDENTIFICADOR LITERAL, no generado (Art. V.11). UUID v7 con marca de tiempo
-- 2026-09-08T12:00:00Z (01a080e3-ae00), versión 7 y variante RFC 9562,
-- CONTINUANDO LA SERIE DE `V67` con el sufijo `5e7ad6`: aquella sembró del
-- `...000001` al `...000004`, y este es el QUINTO.
--
-- SE ASOCIA A SUPERADMIN Y A ADMIN EN ESTA MISMA MIGRACIÓN, que `security.md`
-- §4.4 exige de toda migración que siembre permisos. Sin reserva para la raíz:
-- leer qué documentos admite el sistema es administración ordinaria — es lo que
-- necesita cualquiera que dé de alta a una persona.
--
-- NO SE ASOCIA A `CLIENTE`, igual que `V48` y `V60`. Y eso deja abierto un
-- hueco que NO se resuelve aquí: el registro público de `RF-SP-045` necesita
-- este catálogo y no tiene ningún permiso — es el mismo bloqueo que el de
-- países, y ahora son dos. La decisión de forma es del responsable del proyecto
-- (`RF-SP-051` `tasks.md` §4, bloqueo 3).
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que `V3`, `V40`, `V48` y `V60`: un
-- permiso no tiene línea de tiempo que reconstruir.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a080e3-ae00-7005-9c4f-5e7ad6000005', 'document-types:read', 'document-types', 'read',
 'Consultar tipos de documento',
 'Consultar el catálogo de documentos de identidad admitidos (RF-SP-051). El catálogo no se administra por API: su contenido es la validación de mayoría de edad (RN-SP-036).');


-- -----------------------------------------------------------------------------
-- Asociación a los dos roles de sistema.
--
-- Se enumera por código y no con `SELECT ... FROM permissions`, por el mismo
-- motivo que `V40`, `V48` y `V60`: ese atajo asociaría también cualquier permiso
-- sembrado antes que alguien hubiera decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code = 'document-types:read';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code = 'document-types:read';


-- -----------------------------------------------------------------------------
-- Guarda: si alguna de las dos asociaciones no entró, la migración ABORTA.
--
-- Misma que `V51` estrenó. Olvidar la fila de `ADMIN` no falla al aplicar la
-- migración — deja a `ADMIN` incapaz de conceder lo que no tiene, y
-- `RN-SEG-003` rechazaría la operación sin decir que lo que falta es una
-- siembra.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'document-types:read'
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 2 THEN
        RAISE EXCEPTION
            'V69: se esperaban 2 asociaciones de document-types:read y hay %. '
            'Sin la de ADMIN, ese rol no puede conceder lo que no tiene.', filas;
    END IF;
END $$;
