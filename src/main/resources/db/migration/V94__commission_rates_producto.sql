-- =============================================================================
-- RF-CM-001 · RN-CM-021 (15-09-2026)
-- LA TASA DE ROL NACE CON SU PRODUCTO, Y SOLO RIGE SOBRE EL.
--
-- Por decision del responsable del proyecto (`requirements/cm.md` §5.4): «las
-- comisiones generales llevaran el id del producto: solo se podran crear desde
-- el producto y solo para ese producto». `commission_rates` deja de ser un
-- catalogo por rol y pasa a ser CONFIGURACION DE PRODUCTOS: cada fila dice que
-- paga ESTE producto a ESTE rol. La asociacion de rol —`product_commission_rates`,
-- de V49— deja de tener sentido y se borra. La personalizada y su asociacion
-- (`user_commission_rates`, `user_commission_rate_products`) NO SE TOCAN.
--
-- ES LA SEGUNDA MIGRACION DEL PROYECTO QUE BORRA DATOS A PROPOSITO, y por lo
-- mismo que V49: ninguna tasa de rol anterior tenia producto, y clonarlas por
-- cada asociacion habria sido una copia plausible decidida por una migracion.
-- Se vacia para que la perdida sea VISIBLE, y administracion las registra de
-- nuevo, producto a producto, sabiendo lo que hace.
--
-- EL ORDEN IMPORTA: se borran primero las asociaciones (referencian a las
-- tasas), despues las tasas (la columna NOT NULL no se puede añadir sobre
-- filas que no la tienen), y solo entonces cambia el esquema.
-- =============================================================================

-- 1. Vaciar, y que se vea.
DELETE FROM product_commission_rates;
DELETE FROM commission_rates;

-- 2. La asociacion de rol se va, con la clave foranea compuesta que la ataba a
-- la tasa y el UNIQUE (id, role_id) que existia SOLO para que esa clave
-- compuesta pudiera apuntar ahi.
DROP TABLE product_commission_rates;

ALTER TABLE commission_rates DROP CONSTRAINT uq_commission_rates_id_role;

-- 3. El producto, obligatorio e inmutable (RN-CM-021). SIN ON DELETE: el
-- producto no se borra fisicamente nunca (RN-PM-010).
ALTER TABLE commission_rates ADD COLUMN product_id uuid NOT NULL;

ALTER TABLE commission_rates
    ADD CONSTRAINT fk_commission_rates_product
    FOREIGN KEY (product_id) REFERENCES products (id);

-- 4. Un porcentaje por rol y producto (RN-CM-013), ahora en la propia tabla y
-- PARCIAL sobre las vivas: una tasa retirada no estorba a la que la sustituye.
-- Por parcial no admite DEFERRABLE: dos altas simultaneas del mismo rol sobre
-- el mismo producto muerden en el segundo INSERT, y el adaptador lo traduce a
-- 409 por el nombre de esta restriccion.
CREATE UNIQUE INDEX uq_commission_rates_product_role
    ON commission_rates (product_id, role_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE commission_rates IS
    'Que paga CADA PRODUCTO a cada rol vendedor (RN-CM-021, 15-09-2026): la '
    'tasa nace con su producto y rige solo sobre el, desde que existe. Fue el '
    'catalogo por rol hasta V94, que la vacio. Retiro logico con motivo.';

COMMENT ON COLUMN commission_rates.product_id IS
    'El producto que paga esta tasa (RN-CM-021). Obligatorio e inmutable: '
    'cambiar de producto es retirar la tasa y registrar otra. Un solo rol por '
    'producto entre las vivas (uq_commission_rates_product_role).';
