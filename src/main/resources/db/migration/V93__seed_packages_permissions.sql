-- =============================================================================
-- Siembra de los cuatro `packages:` (`requirements/pm.md` §4, 15-09-2026).
--
-- EL SEGUNDO RECURSO DE `PM`, por decisión del responsable del proyecto: el
-- paquete NO reutiliza los `products:`. Quien administre roles tiene que poder
-- conceder «arma combos» sin conceder «toca el catálogo», y al revés. Y es
-- verificable en el otro sentido: portar los cuatro `products:` no habilita
-- ni una operación de paquetes (`CA-PM-268`).
--
-- CUATRO Y NO MÁS. Registrar, consultar (lista y detalle), editar —nombre,
-- descripción, alcance, estado, y TODO lo que pasa con sus productos:
-- asociar, corregir el descuento, desasociar— y retirar. No hay `packages:sale`
-- ni `packages:hotlink`: la oferta y el hotlink publican el paquete donde
-- publican los productos, con las reglas de acceso que ya tienen esas dos
-- lecturas (`requirements/pm.md` §5.2.10).
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11), por lo mismo que V40,
-- V48, V60 y V88. UUID v7 con marca de tiempo 2026-09-15T00:00:00Z
-- (01a0a25d-0400), versión 7 y variante RFC 9562, y CONTINÚAN LA SERIE DE `PM`
-- con el sufijo `5e7ad5`: del `...000001` al `...000007` ya están; estos son
-- del OCTAVO al UNDÉCIMO.
--
-- SE ASOCIAN A SUPERADMIN Y A ADMIN EN ESTA MISMA MIGRACIÓN, como security.md
-- §4.4 exige. Sin reserva: armar un combo es administración ordinaria, como
-- el catálogo que lo compone desde `V40`.
--
-- NO SE ASOCIAN A `CLIENTE`, por lo mismo que V48, V60 y V88: `V30` siembra
-- ese rol SIN PERMISOS a propósito.
--
-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, igual que V3, V40, V48, V60 y V88.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a0a25d-0400-7001-9c4f-5e7ad5000008', 'packages:create', 'packages', 'create',
 'Registrar paquetes',
 'Dar de alta paquetes de productos, que nacen vacios e inactivos y sin precio propio (RN-PM-036, RN-PM-041).'),

('01a0a25d-0400-7002-9c4f-5e7ad5000009', 'packages:read', 'packages', 'read',
 'Consultar paquetes',
 'Ver todos los paquetes, incluidos los inactivos y los retirados, con su precio calculado y por que no se ofrecen.'),

('01a0a25d-0400-7003-9c4f-5e7ad5000010', 'packages:update', 'packages', 'update',
 'Editar paquetes',
 'Corregir nombre, descripcion y alcance, publicar o despublicar, y asociar, corregir el descuento o desasociar sus productos.'),

('01a0a25d-0400-7004-9c4f-5e7ad5000011', 'packages:delete', 'packages', 'delete',
 'Eliminar paquetes',
 'Retirar un paquete con eliminacion logica y motivo obligatorio; sus productos no cambian.');


-- -----------------------------------------------------------------------------
-- Asociación a los dos roles de sistema. Por código y no con
-- `SELECT ... FROM permissions`, por lo mismo que V40, V48, V60 y V88: ese
-- atajo asociaría también cualquier permiso sembrado antes que alguien
-- hubiera decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code IN ('packages:create', 'packages:read', 'packages:update', 'packages:delete');

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code IN ('packages:create', 'packages:read', 'packages:update', 'packages:delete');


-- -----------------------------------------------------------------------------
-- Guarda: si alguna de las ocho asociaciones no entró, la migración ABORTA.
-- Olvidar las filas de `ADMIN` no falla al aplicar la migración — deja a
-- `ADMIN` incapaz de conceder lo que no tiene.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code IN ('packages:create', 'packages:read', 'packages:update', 'packages:delete')
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 8 THEN
        RAISE EXCEPTION
            'V93: los cuatro packages: deben quedar asociados a SUPERADMIN y a ADMIN; se insertaron %',
            filas;
    END IF;
END $$;
