-- ---------------------------------------------------------------------------
-- V64 — La comisión por venta directa pasa a la tasa de rol (RN-CM-050;
-- requirements/cm.md v0.30.0 §5.11, requirements/pm.md v0.50.0).
--
-- Decisión del responsable del proyecto, 05-10-2026: «en el mismo registro de
-- general, para los vendedores que no sean el último eslabón, poder configurar
-- la comisión por venta directa». Deja de ser una por producto y pasa a ser
-- una por rol, en la misma fila de commission_rates, y opcional.
--
-- Una migración para los dos módulos, como V55: la columna nace en CM y muere
-- en PM por la misma decisión (specs/cm/001-registrar-tasa-comision-rol/plan.md
-- §13).
-- ---------------------------------------------------------------------------

ALTER TABLE commission_rates
    ADD COLUMN direct_rate_type    varchar(20)   NULL,
    ADD COLUMN direct_percentage   numeric(5,2)  NULL,
    ADD COLUMN direct_fixed_amount numeric(14,2) NULL;

-- Lo de cada producto pasa a sus tasas de rol VIVAS cuyo rol no es el último
-- eslabón: el rol VENDEDOR del que cuelga otro rol VENDEDOR. Es la consulta de
-- LastLinkRoles.ids(), negada y escrita en SQL porque aquí no hay aplicación.
-- Se copia también la directa de cero: es la que pagaba hasta hoy la venta
-- propia de un superior, y migrar no puede cambiar lo que se paga.
UPDATE commission_rates cr
   SET direct_rate_type = p.direct_commission_type,
       direct_percentage = p.direct_commission_percentage,
       direct_fixed_amount = ROUND(p.direct_commission_fixed_amount, 2)
  FROM products p, roles r
 WHERE p.id = cr.product_id
   AND r.id = cr.role_id
   AND cr.deleted_at IS NULL
   AND p.direct_commission_type IS NOT NULL
   AND r.role_type = 'VENDEDOR'
   AND EXISTS (
         SELECT 1
           FROM roles hijo
          WHERE hijo.parent_role_id = r.id
            AND hijo.role_type = 'VENDEDOR'
            AND hijo.deleted_at IS NULL);

-- La directa que no tenía fila donde ponerse se pierde: no se inventan tasas
-- de rol (requirements/cm.md §5.11). Se deja dicho cuáles, para configurarlas
-- a mano. Solo las que pagaban algo: la de cero no se echa en falta.
DO $$
DECLARE
    fila record;
BEGIN
    FOR fila IN
        SELECT p.code
          FROM products p
         WHERE p.direct_commission_type IS NOT NULL
           AND COALESCE(p.direct_commission_percentage, p.direct_commission_fixed_amount) > 0
           AND NOT EXISTS (
                 SELECT 1
                   FROM commission_rates cr
                  WHERE cr.product_id = p.id
                    AND cr.direct_rate_type IS NOT NULL)
         ORDER BY p.code
    LOOP
        RAISE NOTICE 'V64: el producto % tenía comisión por venta directa y ninguna tasa de rol donde ponerla; se configura a mano', fila.code;
    END LOOP;
END $$;

ALTER TABLE commission_rates
    -- Las tres nulas —sin directa—, o el tipo con SOLO su campo (RN-CM-016).
    -- Que un rol que es el último eslabón no la lleve lo impide el caso de uso:
    -- depende de la jerarquía de roles, y un CHECK no consulta otra tabla.
    ADD CONSTRAINT ck_commission_rates_direct_forma CHECK (
        (direct_rate_type IS NULL
            AND direct_percentage IS NULL
            AND direct_fixed_amount IS NULL)
        OR (direct_rate_type = 'PORCENTAJE'
            AND direct_percentage IS NOT NULL
            AND direct_fixed_amount IS NULL)
        OR (direct_rate_type = 'FIJO'
            AND direct_percentage IS NULL
            AND direct_fixed_amount IS NOT NULL)),
    ADD CONSTRAINT ck_commission_rates_direct_rangos CHECK (
        (direct_percentage IS NULL OR direct_percentage BETWEEN 0 AND 100)
        AND (direct_fixed_amount IS NULL OR direct_fixed_amount >= 0));

COMMENT ON COLUMN commission_rates.direct_rate_type IS
    'PORCENTAJE o FIJO: lo que cobra este rol en su venta propia de este producto, si no es el último eslabón (RN-CM-050, RN-CM-045). Nula: sin directa, cobra la tasa de rol.';
COMMENT ON COLUMN commission_rates.direct_percentage IS
    'De 0 a 100. Presente solo si direct_rate_type = PORCENTAJE (RN-CM-050).';
COMMENT ON COLUMN commission_rates.direct_fixed_amount IS
    'Por unidad, en la moneda del producto y no por encima del precio. Presente solo si direct_rate_type = FIJO (RN-CM-050).';
COMMENT ON COLUMN commissions.rate_id IS
    'La tasa o el escalón exacto. Con source = DIRECTA, la tasa de rol que la declara desde V64; antes, el producto (RN-CM-045).';

ALTER TABLE products
    DROP CONSTRAINT ck_products_direct_commission_forma,
    DROP CONSTRAINT ck_products_direct_commission_rangos,
    DROP COLUMN direct_commission_type,
    DROP COLUMN direct_commission_percentage,
    DROP COLUMN direct_commission_fixed_amount;
