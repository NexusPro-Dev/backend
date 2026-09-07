-- =============================================================================
-- Siembra de `products:hotlink` (`requirements/pm.md` §4, 07-09-2026).
--
-- EL SEGUNDO PERMISO DE VISTA DE `PM`. Gobierna la VISTA DE HOTLINKS —los
-- productos cuyo alcance llega a ese canal (`RN-PM-019`)—, exactamente como
-- `products:sale` gobierna la vista de venta.
--
-- NO REUTILIZA `products:read`, y por el mismo motivo que aquel: ese permiso
-- abre el catalogo administrativo entero, con lo inactivo y lo retirado dentro,
-- y concederlo para que alguien vea un canal comercial seria dar la lectura de
-- todo el catalogo para ver tres lineas.
--
-- NACE SIN ENDPOINT QUE LO EXIJA, y es deliberado. El canal de hotlinks
-- todavia no esta construido; sembrar el permiso antes NO ROMPE NADA —el
-- catalogo es datos, y su unico efecto es poder concederse— y es exactamente
-- lo que hizo `V51` con los cuatro `movements:`, adelantados al resto de su
-- modulo. Lo que evita es la alternativa: llegar al requerimiento que lo
-- necesite y tener que sembrar el permiso Y construir la vista en el mismo
-- Pull Request.
--
-- IDENTIFICADOR LITERAL, no generado (Art. V.11), por el mismo motivo que V40 y
-- V48: debe ser igual en todos los entornos para que las pruebas lo referencien
-- por constante. Es un UUID v7 con marca de tiempo 2026-09-07T00:00:00Z
-- (01a0792a-2400), version 7 y variante RFC 9562, y CONTINUA LA SERIE DE `PM`
-- con el sufijo `5e7ad5`: `V40` sembro del `...000001` al `...000004`, `V48` el
-- `...000005`, y este es el SEXTO.
--
-- SE ASOCIA A SUPERADMIN Y A ADMIN EN ESTA MISMA MIGRACION, que security.md
-- §4.4 exige de toda migracion que siembre permisos. NO HAY RESERVA, y no es un
-- olvido: decidirlo de otro modo habria creado la CUARTA reserva del
-- superadministrador, y ver que se publica en un canal comercial no es una
-- operacion que deba quedar exclusiva de la raiz — `V40` ya establecio que el
-- catalogo comercial de `PM` es administracion ordinaria.
--
-- NO SE ASOCIA A `CLIENTE`, y es la decision que esta migracion NO toma a
-- proposito, igual que `V48`. `V30__seed_client_role.sql` siembra ese rol SIN
-- PERMISOS porque "sembrarlos a ojo produciria un catalogo que nadie aprobo".
-- Quien administre roles lo concede por la via normal, `RF-SP-006`.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3, V40 y V48: un permiso no
-- tiene linea de tiempo que reconstruir, porque el catalogo es inmutable por
-- API (`RN-SP-010` es su hermana en las monedas).
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a0792a-2400-7001-9c4f-5e7ad5000006', 'products:hotlink', 'products', 'hotlink',
 'Ver la vista de hotlinks',
 'Consultar los productos cuyo alcance llega al canal de hotlinks (RN-PM-019).');


-- -----------------------------------------------------------------------------
-- Asociacion a los dos roles de sistema.
--
-- Se enumera por codigo y no con `SELECT ... FROM permissions`, por el mismo
-- motivo que V40 y V48: ese atajo asociaria tambien cualquier permiso sembrado
-- antes que alguien hubiera decidido no conceder.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code = 'products:hotlink';

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002', id
  FROM permissions
 WHERE code = 'products:hotlink';


-- -----------------------------------------------------------------------------
-- Guarda: si alguna de las dos asociaciones no entro, la migracion ABORTA.
--
-- Es la misma que `V51` estreno, y aqui vale por lo contrario de lo que valia
-- alli: aquella comprobaba una reserva deliberada, y esta comprueba que NO hay
-- reserva. Olvidar la fila de `ADMIN` no falla al aplicar la migracion — deja a
-- `ADMIN` incapaz de conceder lo que no tiene, y `RN-SEG-003` rechazaria la
-- operacion sin decir que lo que falta es una siembra.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas
      FROM role_permissions rp
      JOIN permissions p ON p.id = rp.permission_id
     WHERE p.code = 'products:hotlink'
       AND rp.role_id IN ('01a02a33-4c00-7001-9c4f-5e7ad1000001',
                          '01a02a33-4c00-7002-9c4f-5e7ad1000002');

    IF filas <> 2 THEN
        RAISE EXCEPTION
            'V60: products:hotlink debe quedar asociado a SUPERADMIN y a ADMIN; se insertaron %',
            filas;
    END IF;
END $$;
