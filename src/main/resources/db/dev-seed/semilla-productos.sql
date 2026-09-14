-- =============================================================================
-- Semilla de DESARROLLO: el catálogo de productos de prueba — once upgrades y
-- cinco bots.
--
-- LA APLICA `DevelopmentDataSeeder` AL ARRANCAR, DESPUÉS de la semilla de
-- personas y bajo las mismas condiciones: solo cuando `ENVIRONMENT` NO es
-- `production` y `DEV_SEED_ENABLED` no la apaga. Todo lo que la cabecera de
-- `semilla-desarrollo.sql` dice de aquella vale aquí: no es una migración y no
-- debe serlo nunca, no deja rastro en la auditoría, no pasa por las reglas de
-- negocio, es repetible y no lleva `BEGIN`/`COMMIT`.
--
-- LO QUE HAY QUE PODER PROBAR, y de ahí salen los dieciséis:
--
--   · LA OFERTA COINCIDE POR ORIGEN (`RN-PM-011`): hay un upgrade declarado
--     DESDE cada una de las cuatro membresías, de modo que `cliente1` (BECA),
--     `cliente2` (VIP) y `cliente3` (PLATINO) ven ofertas DISTINTAS, y quien
--     esté en ORO ve solo su renovación.
--   · EL SALTO (`RN-PM-018`): BECA → ORO existe junto a BECA → VIP, y cuesta
--     distinto.
--   · LA RENOVACIÓN (`RN-PM-017`, 07-09-2026): las cuatro membresías tienen su
--     `X → X`, y la de BECA vale CERO — el producto gratuito que `RN-PM-006`
--     admite y sobre el que `RN-CM-020` solo deja comisiones de importe fijo.
--   · EL ALCANCE FILTRA EN EL HOTLINK (`RN-PM-021`): hay productos `TIENDA`
--     que un enlace público NO resuelve, y `HOTLINKS` que sí.
--   · LA IMPLEMENTACIÓN (`RN-PM-020`): hay bots `MANUAL`, cuya venta confirmada
--     queda esperando autorización (`RF-MV-010`).
--   · EL CATÁLOGO ADMINISTRATIVO (`RF-PM-002`): hay un INACTIVO —el mismo par
--     BECA → VIP que el activo, que `uq_products_upgrade_target` admite porque
--     el índice es parcial— y un RETIRADO, para el filtro `includeDeleted`.
--   · EL PRECIO DE COMPRA (`RN-PM-023`): un bot declara lo que NEXUS pagó por
--     él, y los demás lo dejan en nulo — «no se conoce».
--
-- TODO EN USD, que es la moneda por omisión de `V15`: así `exchange` llega
-- nulo y presente en las cuatro lecturas (`FA-002` de `RF-PM-008`), y quien
-- quiera ver una conversión declara una tasa con `RF-SP-047`.
--
-- LOS PRECIOS SON DE PRUEBA y no significan nada: crecen con el salto para que
-- el orden por precio del catálogo se distinga del orden por nombre.
--
-- LOS ACTIVOS NACEN ACTIVOS AQUÍ, y eso contradice a `RN-PM-012` a propósito:
-- la regla dice que un producto NACE inactivo y se publica con `RF-PM-005`, y
-- esta semilla escribe el estado FINAL —como si alguien ya los hubiera
-- activado— porque una oferta vacía no ejercita nada. Todos los activos llevan
-- descripción, que es lo que `RN-PM-014` exige para activar.
--
-- ES REPETIBLE: el código es único e inmutable (`RN-PM-013`), y si ya existe
-- la fila se deja como está — también si alguien la corrigió por la API.
--
-- LAS MEMBRESÍAS SE RESUELVEN POR CÓDIGO (`V46`/`V47`/`V79`: ORO arriba,
-- BECA abajo) y NO por identificador literal, para que la semilla siga
-- valiendo en una base donde la cadena se hubiera vuelto a sembrar.
--
-- Para lanzarla A MANO contra el entorno local:
--
--   docker exec -i nexus-db psql -U nexus -d nexus -v ON_ERROR_STOP=1 -1 \
--     < src/main/resources/db/dev-seed/semilla-productos.sql
-- =============================================================================

CREATE OR REPLACE FUNCTION pg_temp.uuid_v7() RETURNS uuid AS $$
  SELECT (
      substr(ts, 1, 8) || '-' || substr(ts, 9, 4) || '-7' || substr(r, 1, 3)
      || '-a' || substr(r, 4, 3) || '-' || substr(r, 7, 12)
  )::uuid
  FROM (
    SELECT lpad(to_hex((extract(epoch FROM clock_timestamp()) * 1000)::bigint), 12, '0') AS ts,
           md5(random()::text || clock_timestamp()::text) AS r
  ) AS partes;
$$ LANGUAGE sql VOLATILE;


-- -----------------------------------------------------------------------------
-- Los dieciséis, en una sola tabla de valores. Cada columna que el dominio exige
-- se declara explícita: `scope` e `implementation` no tienen DEFAULT en el
-- esquema (`RN-PM-019`, `RN-PM-020`), y dejarlos fuera haría fallar el INSERT.
--
-- `origen`/`destino` van por código de membresía y NULOS en los bots
-- (`RN-PM-002`). `icono` solo en los upgrades (`RN-PM-016`) y con la forma que
-- `ck_products_icon_format` admite. `retirado` marca `deleted_at`.
-- -----------------------------------------------------------------------------

INSERT INTO products (id, code, type, name, description, icon,
                      source_membership_id, target_membership_id,
                      price, purchase_price, currency_id, validity_days,
                      status, scope, implementation, video_url,
                      created_at, updated_at, deleted_at)
SELECT pg_temp.uuid_v7(),
       p.codigo,
       p.tipo,
       p.nombre,
       p.descripcion,
       p.icono,
       origen.id,
       destino.id,
       p.precio,
       p.costo,
       (SELECT id FROM currencies WHERE is_default = true),
       p.dias,
       p.estado,
       p.alcance,
       p.implementacion,
       p.video,
       now(),
       now(),
       CASE WHEN p.retirado THEN now() ELSE NULL END
  FROM (VALUES
      -- codigo, tipo, nombre, descripcion, icono, origen, destino,
      -- precio, costo, dias, estado, alcance, implementacion, video, retirado

      -- ---- Los saltos desde BECA: uno, dos y tres peldaños ------------------
      ('UPGRADE_BECA_VIP', 'UPGRADE_MEMBRESIA', 'Ascenso a VIP',
       'Pasa de Beca a VIP durante treinta días: señales diarias y acceso al canal VIP.',
       'arrow-up-circle', 'BECA', 'VIP',
       49.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'AUTOMATICA', NULL, false),

      ('UPGRADE_BECA_PLATINO', 'UPGRADE_MEMBRESIA', 'Ascenso a Platino',
       'Salta de Beca a Platino durante treinta días, sin pasar por VIP.',
       'trending-up', 'BECA', 'PLATINO',
       129.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'AUTOMATICA', NULL, false),

      ('UPGRADE_BECA_ORO', 'UPGRADE_MEMBRESIA', 'Ascenso a Oro',
       'El salto completo: de Beca a Oro durante treinta días, con mentoría incluida.',
       'crown', 'BECA', 'ORO',
       299.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'AUTOMATICA',
       'https://videos.factech.co/productos/ascenso-a-oro.mp4', false),

      -- ---- Desde VIP y desde PLATINO ----------------------------------------
      ('UPGRADE_VIP_PLATINO', 'UPGRADE_MEMBRESIA', 'De VIP a Platino',
       'Sube un peldaño: de VIP a Platino durante treinta días.',
       'arrow-up-circle', 'VIP', 'PLATINO',
       89.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'AUTOMATICA', NULL, false),

      ('UPGRADE_VIP_ORO', 'UPGRADE_MEMBRESIA', 'De VIP a Oro',
       'Dos peldaños de golpe: de VIP a Oro durante treinta días.',
       'crown', 'VIP', 'ORO',
       259.00, NULL, 30, 'ACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      ('UPGRADE_PLATINO_ORO', 'UPGRADE_MEMBRESIA', 'De Platino a Oro',
       'El último peldaño: de Platino a Oro durante treinta días.',
       'crown', 'PLATINO', 'ORO',
       179.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'MANUAL', NULL, false),

      -- ---- Las cuatro renovaciones: se vende TIEMPO, no nivel ---------------
      ('RENOVAR_BECA', 'UPGRADE_MEMBRESIA', 'Renovar Beca',
       'Renueva la Beca treinta días más. No cuesta nada: es el producto gratuito del catálogo.',
       'refresh-cw', 'BECA', 'BECA',
       0.00, NULL, 30, 'ACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      ('RENOVAR_VIP', 'UPGRADE_MEMBRESIA', 'Renovar VIP',
       'Treinta días más de VIP.',
       'refresh-cw', 'VIP', 'VIP',
       49.00, NULL, 30, 'ACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      ('RENOVAR_PLATINO', 'UPGRADE_MEMBRESIA', 'Renovar Platino',
       'Treinta días más de Platino.',
       'refresh-cw', 'PLATINO', 'PLATINO',
       99.00, NULL, 30, 'ACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      ('RENOVAR_ORO', 'UPGRADE_MEMBRESIA', 'Renovar Oro',
       'Treinta días más de Oro.',
       'refresh-cw', 'ORO', 'ORO',
       199.00, NULL, 30, 'ACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      -- ---- Uno INACTIVO sobre un par que ya tiene activo: el índice único es
      --      parcial y lo admite. Es lo que el catálogo administrativo lista y
      --      la oferta no. -------------------------------------------------------
      ('UPGRADE_BECA_VIP_ANUAL', 'UPGRADE_MEMBRESIA', 'Ascenso a VIP anual',
       'Un año entero de VIP desde Beca. Todavía sin publicar.',
       'calendar', 'BECA', 'VIP',
       399.00, NULL, 365, 'INACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      -- ---- Los bots: una prestación del sistema, sin membresía ---------------
      ('BOT_SENALES', 'BOT', 'Bot de señales',
       'Señales automáticas en tu canal, durante treinta días.',
       NULL, NULL, NULL,
       39.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'AUTOMATICA', NULL, false),

      ('BOT_COPY_TRADING', 'BOT', 'Bot de copy trading',
       'Replica las operaciones de la mesa en tu cuenta. Un funcionario lo activa tras verificar el broker.',
       NULL, NULL, NULL,
       79.00, NULL, 30, 'ACTIVO', 'HOTLINKS', 'MANUAL', NULL, false),

      ('BOT_ALERTAS', 'BOT', 'Bot de alertas',
       'Alertas de mercado gratuitas, sin caducidad.',
       NULL, NULL, NULL,
       0.00, NULL, NULL, 'ACTIVO', 'TIENDA', 'AUTOMATICA', NULL, false),

      ('BOT_PRO_ANUAL', 'BOT', 'Bot Pro anual',
       'La licencia anual del bot profesional. NEXUS la compra a un tercero: el precio de compra es lo que pagó.',
       NULL, NULL, NULL,
       499.00, 250.00, 365, 'ACTIVO', 'TIENDA', 'MANUAL',
       'https://videos.factech.co/productos/bot-pro.mp4', false),

      -- ---- Uno RETIRADO, para `includeDeleted` y el `404` del hotlink -------
      ('BOT_LEGADO', 'BOT', 'Bot legado',
       'La primera versión del bot de señales. Retirado del catálogo.',
       NULL, NULL, NULL,
       19.00, NULL, 30, 'INACTIVO', 'HOTLINKS', 'AUTOMATICA', NULL, true)
  ) AS p(codigo, tipo, nombre, descripcion, icono, origen, destino,
         precio, costo, dias, estado, alcance, implementacion, video, retirado)
  LEFT JOIN memberships origen  ON origen.code  = p.origen
  LEFT JOIN memberships destino ON destino.code = p.destino
 WHERE NOT EXISTS (SELECT 1 FROM products x WHERE x.code = p.codigo)
   -- Un upgrade cuya membresía no exista NO se siembra a medias: sin las dos
   -- filas, `ck_products_type_target` lo rechazaría y la semilla entera
   -- fallaría. Se deja fuera y la guarda de abajo lo dice.
   AND (p.tipo = 'BOT' OR (origen.id IS NOT NULL AND destino.id IS NOT NULL));


-- -----------------------------------------------------------------------------
-- Guarda: si la cadena de membresías no está completa, los upgrades no
-- entraron y conviene saberlo en el log del arranque en vez de descubrirlo con
-- una oferta vacía. Un NOTICE y no una EXCEPTION: la semilla de personas ya
-- corrió, y tumbar el arranque por datos de prueba sería desproporcionado.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    upgrades integer;
BEGIN
    SELECT count(*) INTO upgrades
      FROM products
     WHERE type = 'UPGRADE_MEMBRESIA'
       AND code IN ('UPGRADE_BECA_VIP', 'UPGRADE_BECA_PLATINO', 'UPGRADE_BECA_ORO',
                    'UPGRADE_VIP_PLATINO', 'UPGRADE_VIP_ORO', 'UPGRADE_PLATINO_ORO',
                    'RENOVAR_BECA', 'RENOVAR_VIP', 'RENOVAR_PLATINO', 'RENOVAR_ORO',
                    'UPGRADE_BECA_VIP_ANUAL');

    IF upgrades <> 11 THEN
        RAISE NOTICE
            'semilla-productos: solo % de 11 upgrades en el catálogo; falta alguna membresía (BECA, VIP, PLATINO, ORO).',
            upgrades;
    END IF;
END $$;
