-- =============================================================================
-- RF-MV-001 · T-04 — Siembra de los permisos del modulo MV.
--
-- Los cuatro permisos `movements:` de requirements/mv.md §6, adelantados al
-- resto del modulo: `T-04` no depende de ninguna otra tarea, y sembrar permisos
-- es de lo que mas facil se da por hecho sin comprobarse porque una migracion
-- que se aplica PARECE correcta.
--
-- SE ADELANTA LA SIEMBRA Y NO EL MODULO. Aqui no se crea `movements`, ni
-- `movement_types`, ni `movement_details`, ni `payment_methods`: esas cuatro
-- tablas son `T-01` a `T-03` y viven en su propia migracion. Un permiso sin
-- endpoint que lo exija no rompe nada — el catalogo es datos y su unico efecto
-- es poder concederse—; una tabla sin el caso de uso que la escribe, si.
--
-- POR QUE ESTA MIGRACION SE LLAMA `V51` Y NO `V53`. El numero lo toma quien se
-- aplica primero, que es la regla que modelo-datos.md §1 dejo escrita el
-- 02-09-2026. `RN-SP-025` habia reservado el `51` y `RF-MV-001` el `52`, y
-- NINGUNA DE LAS DOS ESTABA ESCRITA: reservar un numero por adelantado y
-- aplicarlo despues es justo lo que Flyway no perdona — una migracion con un
-- numero por debajo del ultimo aplicado se queda fuera—. Las dos corren un
-- puesto, y los documentos de ambas se enmendaron en el mismo pase.
--
-- Y EL 03-09-2026 TODA LA SERIE CORRE UN PUESTO MAS, por un motivo distinto:
-- `develop` fusiono su propio `V48` (`products:sale`, PR #56) mientras esta
-- rama tenia el suyo desde el 01-09-2026 (`RF-CM-001` a `RF-CM-008`). Los dos
-- eran legitimos por separado — cada uno tomo el siguiente numero libre en su
-- propia linea—, y el que no estaba en `origin` es el que se movio: de `V48` a
-- `V52` a `V49`-`V53`. Esta migracion pasa de `V50` a `V51` en ese mismo pase.
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11), por el mismo motivo que
-- V3, V40 y V45: deben ser iguales en todos los entornos para que las pruebas
-- los referencien por constante. Son UUID v7 con marca de tiempo
-- 2026-09-02T00:00:00Z (01a05f6a-5800), version 7 y variante RFC 9562, y
-- continuan la serie de sufijos por modulo: `5e7ad5` fue de PM, `5e7ad6` de CM
-- y `5e7ad7` es de MV.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3, V40 y V45: un permiso no
-- tiene linea de tiempo que reconstruir, porque el catalogo es inmutable por
-- API (`RN-SP-004`). Su unico historial posible es flyway_schema_history.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES

('01a05f6a-5800-7001-9c4f-5e7ad7000001', 'movements:read', 'movements', 'read',
 'Consultar ventas',
 'Ver el listado de ventas y el detalle de cada una, con su comprobante.'),

('01a05f6a-5800-7002-9c4f-5e7ad7000002', 'movements:create', 'movements', 'create',
 'Registrar ventas',
 'Registrar una venta a nombre de otra persona, que nace pendiente y no concede nada.'),

('01a05f6a-5800-7003-9c4f-5e7ad7000003', 'movements:confirm', 'movements', 'confirm',
 'Confirmar o rechazar ventas',
 'Dar por pagada, o por no pagada, una venta pendiente.'),

('01a05f6a-5800-7004-9c4f-5e7ad7000004', 'movements:void', 'movements', 'void',
 'Anular ventas',
 'Anular una venta pendiente que no debia existir.');

-- NO HAY `movements:update`, y no es un olvido. Una venta no se actualiza nunca
-- (`RN-MV-001`), de modo que un permiso con ese nombre prometeria una operacion
-- que no existe. Por eso `confirm` y `void` son permisos propios en lugar de
-- reutilizar `update`, y por eso son dos y no uno: confirmar es caja diaria,
-- anular BORRA DEL EMBUDO una venta que otro registro.


-- -----------------------------------------------------------------------------
-- Asociacion — SOLO A `SUPERADMIN`
--
-- ESTO SE APARTA DE security.md §4.4, que obliga a toda migracion que siembre
-- permisos a asociarlos a `SUPERADMIN` **y a `ADMIN`**. La excepcion es una
-- decision del responsable del proyecto del 02-09-2026, y §4.4 se enmendo en el
-- mismo pase para declararla: los cuatro `movements:` pasan a ser la TERCERA
-- reserva del superadministrador, junto a `audit:read-security` y
-- `currencies:update`.
--
-- LO QUE CUESTA, ESCRITO AQUI PARA QUE NO HAYA QUE DEDUCIRLO. La reserva no es
-- del mismo tipo que las otras dos. Aquellas cubren operaciones que de verdad
-- solo hace el superadministrador; estas cubren el trabajo diario de la fuerza
-- comercial, y `V7` la cuelga entera de `ADMIN`:
--
--     SUPERADMIN -> ADMIN -> MANAGER -> DIRECTOR -> AGENTE
--
-- `RN-SEG-003` exige que los permisos de un rol sean subconjunto de los de su
-- padre. Con `ADMIN` fuera, NINGUN ROL DE ESA CADENA PODRA DECLARAR
-- `movements:create` — no es que `ADMIN` no pueda delegarlo: es que no hay a
-- quien delegarselo—. Mientras esta reserva siga en pie, registrar, confirmar y
-- anular ventas lo puede hacer el superadministrador y nadie mas.
--
-- Revertirlo el dia que se decida es una migracion de dos INSERT: las filas de
-- `role_permissions` de `ADMIN` son lo unico que falta.
--
-- Se enumeran por codigo y no con `SELECT ... FROM permissions`, por lo mismo
-- que V40 y V45: ese atajo asociaria tambien cualquier permiso sembrado antes
-- que alguien hubiera decidido NO conceder — que es exactamente el caso que
-- esta migracion estrena.
-- -----------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001', id
  FROM permissions
 WHERE code IN ('movements:read', 'movements:create',
                'movements:confirm', 'movements:void');


-- -----------------------------------------------------------------------------
-- Guarda de la asociacion
--
-- `RN-SEG-007` declara que la raiz esta acotada por el CATALOGO COMPLETO. Si el
-- INSERT de arriba no insertara las cuatro filas —un codigo mal escrito basta—,
-- la migracion terminaria con exito y `SUPERADMIN` quedaria por detras del
-- catalogo, que es la unica cota que el modelo de contencion tiene. El sintoma
-- llegaria mucho despues y en otro sitio: `RN-SEG-003` rechazando un rol sin
-- decir que lo que falta es una siembra.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    sin_asociar int;
BEGIN
    SELECT count(*)
      INTO sin_asociar
      FROM permissions p
     WHERE p.resource = 'movements'
       AND NOT EXISTS (
           SELECT 1
             FROM role_permissions rp
            WHERE rp.permission_id = p.id
              AND rp.role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001');

    IF sin_asociar > 0 THEN
        RAISE EXCEPTION
            'Quedaron % permisos movements: sin asociar a SUPERADMIN: la raiz dejaria de acotar el catalogo completo (RN-SEG-007).',
            sin_asociar;
    END IF;
END $$;
