-- =============================================================================
-- RF-PM-007 · T-15 — Siembra de `products:sale` (`requirements/pm.md` §4, enmienda
-- del 02-09-2026 bajo Art. I.7).
--
-- `GET /api/v1/products/available` respondia hasta hoy a cualquier persona
-- autenticada, sin exigir nada. Por decision del responsable del proyecto pasa
-- a exigir este permiso, que gobierna la VISTA DE VENTA y no el catalogo
-- administrativo: no reutiliza `products:read`, porque ese abre lo inactivo y
-- lo retirado y obligaria a conceder el catalogo entero para que alguien viera
-- tres lineas — el mismo argumento que ya sostenia el "sin permiso" original.
--
-- IDENTIFICADOR LITERAL, no generado (Art. V.11), por el mismo motivo que V40:
-- debe ser igual en todos los entornos para que las pruebas lo referencien por
-- constante. Es un UUID v7 con marca de tiempo 2026-09-02T00:00:00Z
-- (01a05f6a-5800), version 7 y variante RFC 9562, y continua la serie de `PM`
-- que V40 abrio con el sufijo `5e7ad5`: aquellos cuatro llegaron hasta
-- `...000004`, y este es el quinto.
--
-- SE ASOCIA A SUPERADMIN Y A ADMIN EN ESTA MISMA MIGRACION, que security.md
-- §4.4 exige de toda migracion que siembre permisos. No hay aqui ninguna
-- reserva: gobernar quien ve la oferta de venta no es una operacion que deba
-- quedar exclusiva del superadministrador, y V40 ya establecio que el catalogo
-- comercial de PM es administracion ordinaria.
--
-- NO SE ASOCIA A `CLIENTE`, y es la decision que esta migracion NO toma a
-- proposito. `V30__seed_client_role.sql` siembra ese rol SIN PERMISOS porque
-- "sembrarlos a ojo produciria un catalogo que nadie aprobo": concederle este
-- de oficio aqui repetiria exactamente lo que aquella migracion decidio no
-- hacer. Quien administre roles se lo concede a `CLIENTE`, a `ESTUDIANTE` o a
-- cualquier rol de tipo CONSUMIDOR por la via normal, `RF-SP-006` — hasta
-- entonces, ningun consumidor ve la vista de venta.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3 y V40: un permiso no tiene
-- linea de tiempo que reconstruir, porque el catalogo es inmutable por API.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a05f6a-5800-7001-9c4f-5e7ad5000005', 'products:sale', 'products', 'sale',
 'Ver la vista de venta',
 'Consultar la oferta disponible para uno mismo: lo que el actor puede comprar hoy.');


-- -----------------------------------------------------------------------------
-- Asociacion a los dos roles de sistema.
--
-- Se enumera por codigo y no con `SELECT ... FROM permissions`, por el mismo
-- motivo que V40: ese atajo asociaria tambien cualquier permiso sembrado antes
-- que alguien hubiera decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code = 'products:sale';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code = 'products:sale';
