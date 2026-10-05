-- =============================================================================
-- Semilla de DESARROLLO: las tasas de comisión de rol de los productos de
-- prueba (`commission_rates`, `RF-CM-001`).
--
-- LA APLICA `DevelopmentDataSeeder` AL ARRANCAR, DESPUÉS de la semilla de
-- productos y bajo las mismas condiciones: solo cuando `ENVIRONMENT` NO es
-- `production` y `DEV_SEED_ENABLED` no la apaga. Todo lo que la cabecera de
-- `semilla-desarrollo.sql` dice de aquella vale aquí: no es una migración y no
-- debe serlo nunca, no deja rastro en la auditoría, no pasa por las reglas de
-- negocio, es repetible y no lleva `BEGIN`/`COMMIT`.
--
-- Pedida por el responsable del proyecto el 01-10-2026, con sus decisiones:
--
--   · UNA TASA POR ROL DE LA CADENA COMERCIAL —AGENTE, DIRECTOR y MANAGER—
--     en cada producto VIVO, activo o inactivo. `BOT_LEGADO` está retirado y
--     no lleva ninguna.
--   · MEZCLA DE PORCENTAJE Y FIJO, para poder probar los dos cálculos de
--     `RF-CM-013`:
--       - los upgrades con precio y `BOT_PRO_ANUAL`, por PORCENTAJE:
--         10 / 7 / 5 % (AGENTE / DIRECTOR / MANAGER);
--       - `BOT_SENALES` y `BOT_COPY_TRADING`, por importe FIJO por unidad:
--         4 / 2,50 / 1,50 y 8 / 5 / 3 USD;
--       - los dos GRATUITOS —`MEMBRESIA_BECA` y `BOT_ALERTAS`— por FIJO,
--         2 / 1 / 0,50 USD, porque en un producto de precio cero solo
--         comisiona el importe fijo (`RN-CM-020`).
--   · NINGUNA CADENA PASA DEL PRECIO (`RN-CM-026`): 22 % en los porcentajes,
--     8 USD sobre 39 y 16 USD sobre 79 en los fijos.
--
-- ES REPETIBLE: si el producto y el rol ya tienen una tasa VIVA, se deja como
-- está —también si alguien la corrigió por la API—. Es lo mismo que
-- `uq_commission_rates_product_role` exige, y el `NOT EXISTS` lo respeta sin
-- llegar a chocar con él.
--
-- LOS PRODUCTOS Y LOS ROLES SE RESUELVEN POR CÓDIGO, no por identificador: un
-- producto que no exista en esta base simplemente no recibe tasas.
--
-- Para lanzarla A MANO contra el entorno local:
--
--   docker exec -i nexus-db psql -U nexus -d nexus -v ON_ERROR_STOP=1 -1 \
--     < src/main/resources/db/dev-seed/semilla-tasas-comision.sql
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


WITH entradas (producto, rol, tipo, valor) AS (
  VALUES
      -- ---- Upgrades con precio: porcentaje ----------------------------------
      ('UPGRADE_BECA_VIP',       'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_BECA_VIP',       'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_BECA_VIP',       'MANAGER',  'PORCENTAJE',  5.00),
      ('UPGRADE_BECA_PLATINO',   'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_BECA_PLATINO',   'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_BECA_PLATINO',   'MANAGER',  'PORCENTAJE',  5.00),
      ('UPGRADE_BECA_ORO',       'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_BECA_ORO',       'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_BECA_ORO',       'MANAGER',  'PORCENTAJE',  5.00),
      ('UPGRADE_VIP_PLATINO',    'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_VIP_PLATINO',    'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_VIP_PLATINO',    'MANAGER',  'PORCENTAJE',  5.00),
      ('UPGRADE_VIP_ORO',        'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_VIP_ORO',        'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_VIP_ORO',        'MANAGER',  'PORCENTAJE',  5.00),
      ('UPGRADE_PLATINO_ORO',    'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_PLATINO_ORO',    'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_PLATINO_ORO',    'MANAGER',  'PORCENTAJE',  5.00),
      ('RENOVAR_VIP',            'AGENTE',   'PORCENTAJE', 10.00),
      ('RENOVAR_VIP',            'DIRECTOR', 'PORCENTAJE',  7.00),
      ('RENOVAR_VIP',            'MANAGER',  'PORCENTAJE',  5.00),
      ('RENOVAR_PLATINO',        'AGENTE',   'PORCENTAJE', 10.00),
      ('RENOVAR_PLATINO',        'DIRECTOR', 'PORCENTAJE',  7.00),
      ('RENOVAR_PLATINO',        'MANAGER',  'PORCENTAJE',  5.00),
      ('RENOVAR_ORO',            'AGENTE',   'PORCENTAJE', 10.00),
      ('RENOVAR_ORO',            'DIRECTOR', 'PORCENTAJE',  7.00),
      ('RENOVAR_ORO',            'MANAGER',  'PORCENTAJE',  5.00),
      -- El inactivo también: se prueba que activarlo no exige tocar sus tasas.
      ('UPGRADE_BECA_VIP_ANUAL', 'AGENTE',   'PORCENTAJE', 10.00),
      ('UPGRADE_BECA_VIP_ANUAL', 'DIRECTOR', 'PORCENTAJE',  7.00),
      ('UPGRADE_BECA_VIP_ANUAL', 'MANAGER',  'PORCENTAJE',  5.00),

      -- ---- Bots: el anual por porcentaje, los otros dos por fijo -----------
      ('BOT_PRO_ANUAL',          'AGENTE',   'PORCENTAJE', 10.00),
      ('BOT_PRO_ANUAL',          'DIRECTOR', 'PORCENTAJE',  7.00),
      ('BOT_PRO_ANUAL',          'MANAGER',  'PORCENTAJE',  5.00),
      ('BOT_SENALES',            'AGENTE',   'FIJO',        4.00),
      ('BOT_SENALES',            'DIRECTOR', 'FIJO',        2.50),
      ('BOT_SENALES',            'MANAGER',  'FIJO',        1.50),
      ('BOT_COPY_TRADING',       'AGENTE',   'FIJO',        8.00),
      ('BOT_COPY_TRADING',       'DIRECTOR', 'FIJO',        5.00),
      ('BOT_COPY_TRADING',       'MANAGER',  'FIJO',        3.00),

      -- ---- Gratuitos: solo fijo (`RN-CM-020`) -------------------------------
      ('MEMBRESIA_BECA',         'AGENTE',   'FIJO',        2.00),
      ('MEMBRESIA_BECA',         'DIRECTOR', 'FIJO',        1.00),
      ('MEMBRESIA_BECA',         'MANAGER',  'FIJO',        0.50),
      ('BOT_ALERTAS',            'AGENTE',   'FIJO',        2.00),
      ('BOT_ALERTAS',            'DIRECTOR', 'FIJO',        1.00),
      ('BOT_ALERTAS',            'MANAGER',  'FIJO',        0.50)
)
INSERT INTO commission_rates (id, product_id, role_id, rate_type, percentage, fixed_amount)
SELECT pg_temp.uuid_v7(),
       p.id,
       r.id,
       e.tipo,
       CASE WHEN e.tipo = 'PORCENTAJE' THEN e.valor END,
       -- El fijo se escribe en centésimas desde V65 (ADR-006); el porcentaje, no.
       CASE WHEN e.tipo = 'FIJO' THEN round(e.valor * 100) END
  FROM entradas e
  JOIN products p ON p.code = e.producto AND p.deleted_at IS NULL
  JOIN roles r    ON r.code = e.rol
 WHERE NOT EXISTS (SELECT 1 FROM commission_rates c
                    WHERE c.product_id = p.id AND c.role_id = r.id
                      AND c.deleted_at IS NULL);


-- -----------------------------------------------------------------------------
-- Guarda: un NOTICE, como la de los productos, si algún producto vivo de la
-- semilla quedó sin sus tres tasas. Sin tumbar el arranque.
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    incompletos integer;
BEGIN
    SELECT count(*) INTO incompletos
      FROM products p
     WHERE p.deleted_at IS NULL
       AND p.code IN ('UPGRADE_BECA_VIP', 'UPGRADE_BECA_PLATINO', 'UPGRADE_BECA_ORO',
                      'UPGRADE_VIP_PLATINO', 'UPGRADE_VIP_ORO', 'UPGRADE_PLATINO_ORO',
                      'MEMBRESIA_BECA', 'RENOVAR_VIP', 'RENOVAR_PLATINO', 'RENOVAR_ORO',
                      'UPGRADE_BECA_VIP_ANUAL', 'BOT_SENALES', 'BOT_COPY_TRADING',
                      'BOT_ALERTAS', 'BOT_PRO_ANUAL')
       AND (SELECT count(*) FROM commission_rates c
              JOIN roles r ON r.id = c.role_id
             WHERE c.product_id = p.id AND c.deleted_at IS NULL
               AND r.code IN ('AGENTE', 'DIRECTOR', 'MANAGER')) < 3;

    IF incompletos <> 0 THEN
        RAISE NOTICE
            'semilla-tasas-comision: % productos de la semilla sin sus tres tasas de rol; falta algún rol (AGENTE, DIRECTOR, MANAGER).',
            incompletos;
    END IF;
END $$;
