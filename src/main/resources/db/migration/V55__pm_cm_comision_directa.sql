-- ---------------------------------------------------------------------------
-- V55 — La comisión por venta directa (RN-PM-051, RN-CM-045;
-- requirements/pm.md v0.47.0 §5.2.16 y requirements/cm.md v0.24.0 §5.9).
--
-- Todo producto declara lo que cobra en su venta propia quien NO es el último
-- eslabón de la fuerza comercial —hoy un DIRECTOR o un MANAGER—, en lugar de
-- su tasa de rol. Vive en products y no en CM porque es obligatoria al
-- registrar el producto, y exigirla desde CM cerraría un ciclo PM -> CM.
--
-- Una migración para los dos módulos: las dos mitades nacen de la misma
-- decisión (specs/pm/001-registrar-producto/plan.md §12.1).
-- ---------------------------------------------------------------------------

ALTER TABLE products
    ADD COLUMN direct_commission_type         varchar(20)   NULL,
    ADD COLUMN direct_commission_percentage   numeric(5,2)  NULL,
    ADD COLUMN direct_commission_fixed_amount numeric(14,4) NULL;

-- Lo ya registrado nace en CERO, salvo los FTD —un upgrade de BECA a BECA—,
-- que no devengan por venta y no la llevan. Decisión del responsable del
-- proyecto: cero, y se configura a mano. Porcentaje cero, salvo en un producto
-- gratuito, que solo admite importe fijo (RN-PM-051, como RN-CM-020): ahí,
-- fijo cero. Es la definición de ProductCatalog.ftdProductIds(), escrita en
-- SQL porque aquí no hay aplicación.
UPDATE products p
   SET direct_commission_type = CASE WHEN p.price = 0 THEN 'FIJO' ELSE 'PORCENTAJE' END,
       direct_commission_percentage = CASE WHEN p.price = 0 THEN NULL ELSE 0 END,
       direct_commission_fixed_amount = CASE WHEN p.price = 0 THEN 0 ELSE NULL END
 WHERE NOT EXISTS (
         SELECT 1
           FROM memberships m
          WHERE m.id = p.source_membership_id
            AND p.type = 'UPGRADE_MEMBRESIA'
            AND p.target_membership_id = p.source_membership_id
            AND m.code = 'BECA');

ALTER TABLE products
    -- Las tres nulas —un FTD—, o el tipo con SOLO su campo (RN-CM-016). Que un
    -- producto que no es FTD las tenga nulas lo impide el caso de uso: saber si
    -- es FTD exige leer memberships, y un CHECK no consulta otra tabla.
    ADD CONSTRAINT ck_products_direct_commission_forma CHECK (
        (direct_commission_type IS NULL
            AND direct_commission_percentage IS NULL
            AND direct_commission_fixed_amount IS NULL)
        OR (direct_commission_type = 'PORCENTAJE'
            AND direct_commission_percentage IS NOT NULL
            AND direct_commission_fixed_amount IS NULL)
        OR (direct_commission_type = 'FIJO'
            AND direct_commission_percentage IS NULL
            AND direct_commission_fixed_amount IS NOT NULL)),
    -- El tope contra el precio no va aquí aunque sea de la misma fila: sobre
    -- precio cero no hay tope, y la regla se lee entera en el caso de uso.
    ADD CONSTRAINT ck_products_direct_commission_rangos CHECK (
        (direct_commission_percentage IS NULL
            OR direct_commission_percentage BETWEEN 0 AND 100)
        AND (direct_commission_fixed_amount IS NULL
            OR direct_commission_fixed_amount >= 0));

COMMENT ON COLUMN products.direct_commission_type IS
    'PORCENTAJE o FIJO: lo que cobra en su venta propia quien no es el último eslabón (RN-PM-051, RN-CM-045). Nula solo en un FTD.';
COMMENT ON COLUMN products.direct_commission_percentage IS
    'De 0 a 100. Presente solo si direct_commission_type = PORCENTAJE (RN-PM-051).';
COMMENT ON COLUMN products.direct_commission_fixed_amount IS
    'Por unidad, en la moneda del producto y no por encima del precio. Presente solo si direct_commission_type = FIJO (RN-PM-051).';

-- La comisión generada dice que salió de la directa, y solo la cobra quien
-- vendió (requirements/cm.md §7.4). rate_id apunta entonces al producto.
ALTER TABLE commissions
    DROP CONSTRAINT ck_commissions_source,
    ADD CONSTRAINT ck_commissions_source CHECK (source IN ('PERSONALIZADA', 'ROL', 'DIRECTA')),
    ADD CONSTRAINT ck_commissions_directa CHECK (
        source <> 'DIRECTA' OR (commission_kind = 'POR_VENTA' AND chain_level = 0));
