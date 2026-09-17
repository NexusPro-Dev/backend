-- ---------------------------------------------------------------------------
-- V10 — la tasa personalizada NACE con su producto (RN-CM-021, 16-09-2026).
--
-- Decisión del responsable del proyecto: «una sola comisión personalizada
-- por usuario y producto», conservando la vigencia. Primera migración
-- posterior a la consolidación (V1..V9): un número no se reutiliza y una
-- migración aplicada no se toca, de modo que va aquí y no reescribiendo V6.
--
-- Deshace V85 y recupera V84 con una diferencia: el EXCLUDE que V84
-- conservaba cubría (persona, rango); este cubre (persona, PRODUCTO, rango).
-- ---------------------------------------------------------------------------

-- Se vacía antes de añadir la columna, por lo mismo que V94 con las de rol:
-- ninguna personalizada existente tiene UN producto honesto que ponerle —las
-- asociadas tenían varios—, y el NOT NULL sin valor por defecto lo exige.
DELETE FROM user_commission_rate_products;
DELETE FROM user_commission_rates;

DROP TABLE user_commission_rate_products;

ALTER TABLE user_commission_rates
    ADD COLUMN product_id uuid NOT NULL,
    ADD CONSTRAINT fk_user_commission_rates_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    -- RN-CM-006 vuelve al motor: ningún día cubierto por dos tasas vivas de la
    -- misma persona sobre el mismo producto. Es un EXCLUDE y no un UNIQUE
    -- porque lo que no debe repetirse es un intervalo. `btree_gist` está en V1.
    ADD CONSTRAINT uq_user_commission_rates_vigente
        EXCLUDE USING gist (
            user_id    WITH =,
            product_id WITH =,
            daterange(valid_from, valid_to, '[]') WITH &&
        ) WHERE (deleted_at IS NULL);

CREATE INDEX ix_user_commission_rates_producto ON user_commission_rates (product_id);

COMMENT ON TABLE user_commission_rates IS
    'Excepcion por persona SOBRE UN PRODUCTO, con vigencia (RN-CM-021, 16-09-2026). RN-CM-006 vive aqui: uq_user_commission_rates_vigente.';
COMMENT ON COLUMN user_commission_rates.product_id IS
    'El producto sobre el que rige. Nace con la tasa y no se corrige (RN-CM-021).';
COMMENT ON COLUMN user_commission_rates.fixed_amount IS
    'Importe fijo por venta, en la moneda de SU producto (RN-CM-017): se valida contra sus decimales.';
