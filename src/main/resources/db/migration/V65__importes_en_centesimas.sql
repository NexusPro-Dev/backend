-- =============================================================================
-- V65 — Los importes se guardan en centésimas (ADR-006).
--
-- architecture/ADR-006-importes-en-unidades-minimas.md · specs/mv/001-registrar-
-- venta/tasks.md T-41.
--
-- Todo importe en dinero pasa a bigint: 12,50 se guarda 1250. El dominio y la
-- API siguen en decimales; convierte MinorUnitsConverter en las entidades y
-- MinorUnits en el SQL nativo. NO se tocan los porcentajes, exchange_rates.price
-- ni points_rates.points_per_unit, que no son importes.
--
-- round() sobre numeric deshace el empate alejándose de cero, que es lo que hace
-- RoundingMode.HALF_UP: las columnas que admitían cuatro decimales se quedan con
-- dos (10,0050 → 1001), y las de dos se multiplican sin perder nada.
--
-- LO QUE NO SE RECALCULA: commission_batches.total_amount se redondea como las
-- demás, y no se vuelve a sumar de sus comisiones ya redondeadas. Un lote PAGADO
-- se abonó redondeando su total (RN-MV-044), y ese abono es historia: recalcular
-- podría separarlo del movimiento que lo pagó. La diferencia, si la hay, es de
-- céntimos en filas anteriores a esta migración.
--
-- Va detrás de V64 (la comisión directa por rol), que crea
-- commission_rates.direct_fixed_amount y retira products.direct_commission_*.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Los CHECK que comparan con una constante que no es cero. Se retiran ANTES:
--    el ALTER … TYPE los revalida, y con «<= 100» un 12,50 % (1250) ya no cabría.
--    Los que comparan con cero o son igualdades lineales siguen valiendo
--    multiplicados por cien y no se tocan.
-- ---------------------------------------------------------------------------

ALTER TABLE product_package_items DROP CONSTRAINT ck_product_package_items_percentage;
ALTER TABLE movement_detail_discounts DROP CONSTRAINT ck_movement_detail_discounts_value;
ALTER TABLE currencies DROP CONSTRAINT ck_currencies_decimal_places;

-- ---------------------------------------------------------------------------
-- 2. PM.
-- ---------------------------------------------------------------------------

ALTER TABLE products
    ALTER COLUMN price          TYPE bigint USING round(price * 100),
    ALTER COLUMN purchase_price TYPE bigint USING round(purchase_price * 100);

ALTER TABLE product_package_items
    ALTER COLUMN discount_value TYPE bigint USING round(discount_value * 100);

-- ---------------------------------------------------------------------------
-- 3. CM.
-- ---------------------------------------------------------------------------

ALTER TABLE commission_rates
    ALTER COLUMN fixed_amount        TYPE bigint USING round(fixed_amount * 100),
    ALTER COLUMN direct_fixed_amount TYPE bigint USING round(direct_fixed_amount * 100);

ALTER TABLE user_commission_rates
    ALTER COLUMN fixed_amount TYPE bigint USING round(fixed_amount * 100);

ALTER TABLE commissions
    ALTER COLUMN fixed_amount      TYPE bigint USING round(fixed_amount * 100),
    ALTER COLUMN unit_price        TYPE bigint USING round(unit_price * 100),
    ALTER COLUMN commission_amount TYPE bigint USING round(commission_amount * 100);

ALTER TABLE commission_batches
    ALTER COLUMN total_amount TYPE bigint USING round(total_amount * 100);

ALTER TABLE afftrack_rates
    ALTER COLUMN amount_per_ftd TYPE bigint USING round(amount_per_ftd * 100);

ALTER TABLE user_afftrack_rates
    ALTER COLUMN amount_per_ftd TYPE bigint USING round(amount_per_ftd * 100);

-- ---------------------------------------------------------------------------
-- 4. MV. Las columnas con DEFAULT 0 lo pierden y lo recuperan: el valor por
--    defecto se reescribe con el tipo nuevo en lugar de confiar en que el
--    motor convierta la expresión.
-- ---------------------------------------------------------------------------

ALTER TABLE movements
    ALTER COLUMN discount_amount DROP DEFAULT,
    ALTER COLUMN total_amount    TYPE bigint USING round(total_amount * 100),
    ALTER COLUMN discount_amount TYPE bigint USING round(discount_amount * 100),
    ALTER COLUMN payable_amount  TYPE bigint USING round(payable_amount * 100),
    ALTER COLUMN points_amount   TYPE bigint USING round(points_amount * 100),
    ALTER COLUMN discount_amount SET DEFAULT 0;

ALTER TABLE movement_details
    ALTER COLUMN line_discount DROP DEFAULT,
    ALTER COLUMN unit_price    TYPE bigint USING round(unit_price * 100),
    ALTER COLUMN line_discount TYPE bigint USING round(line_discount * 100),
    ALTER COLUMN line_amount   TYPE bigint USING round(line_amount * 100),
    ALTER COLUMN line_discount SET DEFAULT 0;

ALTER TABLE movement_detail_discounts
    ALTER COLUMN value          TYPE bigint USING round(value * 100),
    ALTER COLUMN discount_value TYPE bigint USING round(discount_value * 100);

ALTER TABLE payments
    ALTER COLUMN amount          TYPE bigint USING round(amount * 100),
    ALTER COLUMN refunded_amount TYPE bigint USING round(refunded_amount * 100);

ALTER TABLE accounts
    ALTER COLUMN balance DROP DEFAULT,
    ALTER COLUMN balance TYPE bigint USING round(balance * 100),
    ALTER COLUMN balance SET DEFAULT 0;

ALTER TABLE movement_entries
    ALTER COLUMN amount        TYPE bigint USING round(amount * 100),
    ALTER COLUMN balance_after TYPE bigint USING round(balance_after * 100);

-- ---------------------------------------------------------------------------
-- 5. Los tres CHECK, recreados en la unidad nueva.
-- ---------------------------------------------------------------------------

ALTER TABLE product_package_items
    ADD CONSTRAINT ck_product_package_items_percentage
        CHECK (discount_type <> 'PORCENTAJE' OR discount_value <= 10000);

ALTER TABLE movement_detail_discounts
    ADD CONSTRAINT ck_movement_detail_discounts_value
        CHECK (value >= 0 AND (type <> 'PORCENTAJE' OR value <= 10000));

-- Una moneda de tres o cuatro decimales no cabe en centésimas. Ninguna sembrada
-- pasaba de dos, y el catálogo no se edita por API (RN-SP-010).
ALTER TABLE currencies
    ADD CONSTRAINT ck_currencies_decimal_places
        CHECK (decimal_places BETWEEN 0 AND 2);

-- ---------------------------------------------------------------------------
-- 6. Lo que dice cada columna. Los tres comentarios que hablaban de la escala
--    anterior se reescriben; a todos los demás se les AÑADE la unidad, sin
--    perder lo que ya decían (sus reglas de negocio siguen vigentes).
-- ---------------------------------------------------------------------------

COMMENT ON COLUMN commission_rates.fixed_amount IS
    'Importe fijo por venta, en CENTÉSIMAS (ADR-006). NO LLEVA MONEDA: toma la del producto que se venda (RN-CM-017). NO ESTA ACOTADO POR ARRIBA (RN-CM-018).';
COMMENT ON COLUMN afftrack_rates.amount_per_ftd IS
    'Valor por FTD, en CENTÉSIMAS (ADR-006) de la moneda del producto: no la declara (RN-CM-017).';
COMMENT ON COLUMN movement_detail_discounts.value IS
    'Lo declarado, en CENTÉSIMAS (ADR-006): 1000 es un 10 % o un fijo de 10,00 según type. Misma unidad que product_package_items.discount_value.';

DO $$
DECLARE
    col record;
    previo text;
BEGIN
    FOR col IN
        SELECT * FROM (VALUES
            ('products', 'price'), ('products', 'purchase_price'),
            ('product_package_items', 'discount_value'),
            ('commission_rates', 'direct_fixed_amount'),
            ('user_commission_rates', 'fixed_amount'),
            ('commissions', 'fixed_amount'), ('commissions', 'unit_price'),
            ('commissions', 'commission_amount'),
            ('commission_batches', 'total_amount'),
            ('user_afftrack_rates', 'amount_per_ftd'),
            ('movements', 'total_amount'), ('movements', 'discount_amount'),
            ('movements', 'payable_amount'), ('movements', 'points_amount'),
            ('movement_details', 'unit_price'), ('movement_details', 'line_discount'),
            ('movement_details', 'line_amount'),
            ('movement_detail_discounts', 'discount_value'),
            ('payments', 'amount'), ('payments', 'refunded_amount'),
            ('accounts', 'balance'),
            ('movement_entries', 'amount'), ('movement_entries', 'balance_after')
        ) AS v(tabla, columna)
    LOOP
        SELECT col_description(c.oid, a.attnum) INTO previo
          FROM pg_class c
          JOIN pg_attribute a ON a.attrelid = c.oid
         WHERE c.relname = col.tabla AND a.attname = col.columna;
        EXECUTE format('COMMENT ON COLUMN %I.%I IS %L', col.tabla, col.columna,
                       coalesce(previo || ' ', '') || 'En CENTÉSIMAS (ADR-006).');
    END LOOP;
END $$;
