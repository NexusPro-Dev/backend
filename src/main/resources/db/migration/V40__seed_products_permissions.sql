-- =============================================================================
-- RF-PM-001 · T-02 — Siembra de los permisos del modulo PM.
--
-- Los cuatro permisos `products:` de requirements/pm.md §4.
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11), y por el mismo motivo
-- que V3: deben ser iguales en todos los entornos para que las pruebas los
-- referencien por constante. Son UUID v7 con marca de tiempo
-- 2026-08-27T00:00:00Z (01a03a6a-9000), version 7 y variante RFC 9562.
--
-- Y SE ASOCIAN A SUPERADMIN Y ADMIN EN ESTA MISMA MIGRACION, que no es una
-- cortesía: security.md §4.4 lo declara OBLIGACION de toda migración que siembre
-- permisos, y V7__seed_system_roles NO PUEDE HACERLO POR ELLA — asocia el
-- catálogo existente en su momento, y estos permisos aún no existían.
--
-- El síntoma de olvidarlo no se parece a la causa: `ADMIN` quedaría incapaz de
-- crear un rol que declare `products:create`, y RN-SEG-003 rechazaría la
-- operación sin decir en ningún sitio que lo que falta es una siembra.
--
-- LOS CUATRO VAN A AMBOS ROLES. A diferencia de `audit:read-security` y
-- `currencies:update`, que V7 excluyó de ADMIN, aquí no hay ninguno que deba
-- quedar reservado al superadministrador: gobernar el catálogo comercial es
-- administración ordinaria.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3: un permiso no tiene línea de
-- tiempo que reconstruir, porque el catálogo es inmutable por API.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

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
 'Retirar un producto del catalogo con eliminacion logica y motivo obligatorio.');


-- -----------------------------------------------------------------------------
-- Asociacion a los dos roles de sistema.
--
-- Se enumeran por codigo y no con `SELECT ... FROM permissions`: ese atajo, que
-- V7 usa para SUPERADMIN, asociaria tambien cualquier permiso que otra
-- migracion hubiera sembrado antes y que alguien hubiera decidido NO conceder.
-- Aqui se dice exactamente cuales.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code IN ('products:create', 'products:read', 'products:update', 'products:delete');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code IN ('products:create', 'products:read', 'products:update', 'products:delete');
