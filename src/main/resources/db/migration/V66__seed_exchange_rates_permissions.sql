-- =============================================================================
-- RF-SP-047 · T-02 — Los cuatro permisos del submodulo de tasas de cambio.
--
-- El catalogo pasa de treinta y ocho a CUARENTA Y DOS
-- (`security.md` §4.4 v0.41.0).
--
-- ES EL PRIMER RECURSO DEL CATALOGO CON GUION EN EL NOMBRE: hasta hoy el guion
-- solo aparecia en la ACCION —`audit:read-changes`, `users:assign-roles`—. Se
-- admite porque el recurso es de dos palabras y `exchangerates` no se lee.
--
-- SE ASOCIAN A SUPERADMIN Y A ADMIN EN ESTA MISMA MIGRACION, que
-- `security.md` §4.4 exige de toda migracion que siembre permisos. NO HAY
-- RESERVA: administrar a cuanto se cambia una moneda es administracion
-- ordinaria, y la reserva de la raiz existe para lo que condiciona todo calculo
-- financiero de forma irreversible — una tasa mal puesta se corrige
-- (`RF-SP-049`) o se retira (`RF-SP-050`).
--
-- CONVIENE LEER LA DIFERENCIA CON `currencies:`: aquel gobierna un catalogo que
-- `RN-SP-010` deja FUERA del alcance de la API —y `currencies:update` es ademas
-- una de las tres reservas del superadministrador—, mientras que una tasa SE
-- ADMINISTRA POR API porque cambia, y cambia seguido. Que las monedas sean
-- estables y sus tasas no es exactamente la razon de que sean dos recursos y no
-- uno.
--
-- IDENTIFICADOR LITERAL, no generado (Art. V.11): debe ser igual en todos los
-- entornos para que las pruebas lo referencien por constante. Son UUID v7 con
-- marca de tiempo 2026-09-08T00:00:00Z (01a07e50-8000), version 7 y variante
-- RFC 9562, y ABREN LA SERIE `5e7ad9` — la primera libre.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3, V40, V48 y V60: un permiso
-- no tiene linea de tiempo que reconstruir, porque el catalogo es inmutable por
-- API.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

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
 'Sacar de circulacion, con motivo, una tasa que no debio existir (RF-SP-050).');


-- -----------------------------------------------------------------------------
-- Asociacion a los dos roles de sistema.
--
-- Se enumera por codigo y no con `SELECT ... FROM permissions`, por el mismo
-- motivo que V40, V48 y V60: ese atajo asociaria tambien cualquier permiso
-- sembrado antes que alguien hubiera decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE resource = 'exchange-rates';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE resource = 'exchange-rates';


-- -----------------------------------------------------------------------------
-- Guarda: si alguna de las OCHO asociaciones no entro, la migracion ABORTA.
--
-- Es la misma que `V51` estreno y `V60` reutilizo, y aqui vale por lo contrario
-- de lo que valia en aquella: `V51` comprobaba una reserva deliberada, y esta
-- comprueba que NO hay reserva. Olvidar las filas de `ADMIN` no falla al
-- aplicar la migracion — deja a `ADMIN` incapaz de conceder lo que no tiene, y
-- `RN-SEG-003` rechazaria la operacion sin decir que lo que falta es una
-- siembra.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.resource = 'exchange-rates'
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 8 THEN
        RAISE EXCEPTION
            'V66: los cuatro exchange-rates: deben quedar asociados a SUPERADMIN y a ADMIN; se insertaron %',
            filas;
    END IF;
END $$;
