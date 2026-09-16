-- ---------------------------------------------------------------------------
-- V14 — el descuento es de la LÍNEA, el movimiento recuerda su paquete, y la
-- línea congela el nombre y la descripción de lo que se vendió
-- (RN-MV-027, RN-MV-002 y RN-MV-013 enmendadas, 16-09-2026).
--
-- Decisión del responsable del proyecto, que cierra la pregunta que mv.md §7.1
-- dejó abierta el 02-09-2026: el descuento NO es de cabecera. Cada rebaja de
-- una línea es una fila de `movement_detail_discounts` —como se pactó (`type`,
-- `value`) y como se cobró (`discount_value`, en dinero y por unidad)—, y la
-- línea congela la suma: line_discount = quantity × Σ discount_value, y
-- line_amount = quantity × unit_price − line_discount. La cabecera pasa a ser
-- suma de las líneas en sus tres cifras; ck_movements_payable no cambia.
--
-- `movements.package_id` es una REFERENCIA y no una copia (RN-MV-002): el
-- paquete no se borra, se retira; lo que sí cambia de él —el descuento de cada
-- producto— queda congelado en las líneas. Va en la CABECERA porque una venta
-- lleva UN paquete y nada más (RN-MV-028), y con el producto de cada línea
-- forma la pareja que identifica su asociación en product_package_items
-- (RN-PM-038) — que no tiene identificador propio y cuya fila se borra al
-- desasociar (RN-PM-042), de modo que una clave foránea hacia ella prohibiría
-- desasociar lo ya vendido. Nulo en toda venta que no sea de un paquete.
--
-- Y la línea congela el NOMBRE y la DESCRIPCIÓN del producto, que es
-- RN-MV-002 aplicada sin excepción: RF-PM-004 los corrige, de modo que leerlos
-- del catálogo al mostrar una venta de hace un año reescribiría lo que alguien
-- compró. EL CÓDIGO NO SE COPIA y se sigue leyendo de products: RN-PM-013 lo
-- declara inmutable, y lo inmutable se referencia.
--
-- HOY NADIE ESCRIBE DESCUENTOS: las tres entradas registran la línea con
-- line_discount = 0 y sin rebajas. El DEFAULT 0 es lo que deja válidas las
-- filas ya vendidas sin reescribirlas. Cuarta migración posterior a la
-- consolidación (V1..V9); enmienda V7 y V12 en lugar de reescribirlas.
-- ---------------------------------------------------------------------------

-- 1. La cabecera recuerda el paquete que se compró.
ALTER TABLE movements
    ADD COLUMN package_id uuid NULL,
    ADD CONSTRAINT fk_movements_package
        FOREIGN KEY (package_id) REFERENCES product_packages (id) ON DELETE RESTRICT;

COMMENT ON COLUMN movements.package_id IS
    'RN-MV-028: el paquete que se compro; NULL en toda venta que no sea de un paquete. Con el product_id de cada linea forma la pareja de product_package_items (RN-PM-038).';

-- 2. La línea gana su descuento y congela lo que puede cambiar del producto.
ALTER TABLE movement_details
    ADD COLUMN product_name        varchar(150)  NULL,
    ADD COLUMN product_description text          NULL,
    ADD COLUMN line_discount       numeric(14,2) NOT NULL DEFAULT 0,
    -- Ningún descuento deja la línea por debajo de cero, y ninguno es negativo:
    -- un descuento negativo es un recargo disfrazado.
    ADD CONSTRAINT ck_movement_details_discount
        CHECK (line_discount >= 0 AND line_discount <= quantity * unit_price),
    -- La igualdad de la cabecera, bajada a la línea. Las filas que ya existen
    -- la cumplen con el cero.
    ADD CONSTRAINT ck_movement_details_amount
        CHECK (line_amount = quantity * unit_price - line_discount);

-- Lo ya vendido se rellena con el nombre y la descripcion de HOY, que es lo
-- unico que se sabe de aquellas lineas: no se congelaron entonces, y este es el
-- valor mas cercano al de aquel dia. Despues el nombre queda OBLIGATORIO, con
-- el mismo orden que V13 uso con la vigencia del paquete: anadir, rellenar,
-- exigir. La descripcion sigue admitiendo nulo porque products.description
-- tambien lo admite: ahi el nulo significa «este producto no la declara».
UPDATE movement_details d
   SET product_name = p.name,
       product_description = p.description
  FROM products p
 WHERE p.id = d.product_id;

ALTER TABLE movement_details
    ALTER COLUMN product_name SET NOT NULL;

COMMENT ON COLUMN movement_details.product_name IS
    'COPIA del nombre del producto en el momento de la venta (RN-MV-002): RF-PM-004 lo corrige, y una venta pasada no se reescribe.';
COMMENT ON COLUMN movement_details.product_description IS
    'COPIA de la descripcion en el momento de la venta (RN-MV-002). NULL tambien cuando el producto no la tenia: products.description admite nulo.';
COMMENT ON COLUMN movement_details.line_discount IS
    'RN-MV-027: quantity x la suma en dinero de las rebajas de movement_detail_discounts. CONGELADO. Hoy siempre cero.';
COMMENT ON COLUMN movement_details.line_amount IS
    'quantity x unit_price - line_discount. Se guarda aunque se derive: es el numero que se imprimio (RN-MV-013).';

-- 3. Las rebajas de cada línea: como se pactaron y como se cobraron.
CREATE TABLE movement_detail_discounts (
    id                 uuid          PRIMARY KEY,
    movement_detail_id uuid          NOT NULL,
    type               varchar(20)   NOT NULL,
    value              numeric(14,4) NOT NULL,
    discount_value     numeric(14,2) NOT NULL,
    created_at         timestamptz   NOT NULL DEFAULT now(),

    -- El vocabulario de RN-PM-037, y el mismo par de CHECK de
    -- product_package_items: es la declaración de donde vendrá.
    CONSTRAINT ck_movement_detail_discounts_type
        CHECK (type IN ('PORCENTAJE', 'FIJO')),
    CONSTRAINT ck_movement_detail_discounts_value
        CHECK (value >= 0 AND (type <> 'PORCENTAJE' OR value <= 100)),
    CONSTRAINT ck_movement_detail_discounts_money
        CHECK (discount_value >= 0),
    -- Cascada con el mismo significado que la de la línea con su venta: nada
    -- borra ventas, y el esquema no admite rebajas huérfanas.
    CONSTRAINT fk_movement_detail_discounts_detail
        FOREIGN KEY (movement_detail_id) REFERENCES movement_details (id) ON DELETE CASCADE
);

COMMENT ON TABLE movement_detail_discounts IS
    'RN-MV-027: cada rebaja de una linea, como se pacto (type, value) y como se cobro (discount_value, en dinero y por unidad). Sin updated_at ni deleted_at: una rebaja aplicada no se edita ni se retira.';
COMMENT ON COLUMN movement_detail_discounts.value IS
    'Lo declarado: 10 (por ciento) o 5.0000 (fijo). Escala de product_package_items.discount_value.';
COMMENT ON COLUMN movement_detail_discounts.discount_value IS
    'Lo que valio en dinero POR UNIDAD el dia de la venta: el fijo tal cual; el porcentaje sobre unit_price, redondeado a la moneda a la mitad hacia arriba. CONGELADO.';

CREATE INDEX idx_movement_detail_discounts_detail ON movement_detail_discounts (movement_detail_id);
