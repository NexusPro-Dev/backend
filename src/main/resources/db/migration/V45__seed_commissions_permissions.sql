-- =============================================================================
-- RF-CM-001 · T-02 — Siembra de los permisos del modulo CM.
--
-- Los cuatro permisos `commissions:` de requirements/cm.md §6.
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11), por el mismo motivo que
-- V3 y V40: deben ser iguales en todos los entornos para que las pruebas los
-- referencien por constante. Son UUID v7 con marca de tiempo
-- 2026-08-28T00:00:00Z (01a03fb4-6800), version 7 y variante RFC 9562.
--
-- Y SE ASOCIAN A SUPERADMIN Y ADMIN EN ESTA MISMA MIGRACION: security.md §4.4
-- lo declara obligacion de toda migracion que siembre permisos, y V7 no puede
-- hacerlo por ella porque estos permisos no existian entonces. El sintoma de
-- olvidarlo no se parece a la causa — ADMIN quedaria incapaz de crear un rol
-- que declare `commissions:create`, y RN-SEG-003 rechazaria la operacion sin
-- decir en ningun sitio que lo que falta es una siembra.
--
-- CUATRO PERMISOS Y NO UNO POR GRADO. El grado de una tarifa —del rol, del
-- producto, de la persona— es un DATO de la tarifa, no una operacion distinta.
-- Distinguirlo en el permiso obligaria a mantener sincronizados el modelo de
-- permisos y la forma de la tabla.
--
-- LOS CUATRO VAN A AMBOS ROLES: declarar cuanto se paga por vender es
-- administracion ordinaria, y ninguno merece quedar reservado al
-- superadministrador.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3 y V40.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

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
 'Retirar una tarifa con eliminacion logica y motivo obligatorio.');


-- -----------------------------------------------------------------------------
-- Asociacion a los dos roles de sistema.
--
-- Se enumeran por codigo y no con `SELECT ... FROM permissions`, por lo mismo
-- que V40: ese atajo asociaria tambien cualquier permiso sembrado antes que
-- alguien hubiera decidido NO conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code IN ('commissions:create', 'commissions:read',
                'commissions:update', 'commissions:delete');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code IN ('commissions:create', 'commissions:read',
                'commissions:update', 'commissions:delete');
