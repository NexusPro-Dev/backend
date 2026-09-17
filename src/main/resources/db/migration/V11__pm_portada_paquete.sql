-- ---------------------------------------------------------------------------
-- V11 — el paquete lleva PORTADA (RN-PM-045, 16-09-2026).
--
-- Decisión del responsable del proyecto: la misma imagen que la del producto,
-- guardada en la MISMA tabla y servida por la MISMA ruta pública, y SIN icono
-- ni color al lado — sin portada, el frontend pinta el icono de promoción y el
-- color por omisión del sistema, los mismos para todos los paquetes. Por eso
-- no hay aquí ninguna regla como RN-PM-034 que sostener.
--
-- Segunda migración posterior a la consolidación (V1..V9): añade la columna en
-- lugar de reescribir V5, porque una migración aplicada no se toca.
-- ---------------------------------------------------------------------------

ALTER TABLE product_packages
    ADD COLUMN cover_image_id uuid NULL,
    -- Sin ON DELETE, como fk_products_cover_image: la fila de la imagen se
    -- borra DESPUÉS de que la columna deje de señalarla, en la misma
    -- transacción, y nunca al revés.
    ADD CONSTRAINT fk_product_packages_cover_image
        FOREIGN KEY (cover_image_id) REFERENCES product_images (id),
    -- Una imagen es portada de UN paquete como máximo. Que tampoco sea a la vez
    -- la de un producto no cabe en un UNIQUE —son dos tablas— y no hace falta:
    -- una fila de product_images solo nace por una subida, que la señala desde
    -- una entidad y solo una (requirements/pm.md §5.2.12).
    ADD CONSTRAINT uq_product_packages_cover_image UNIQUE (cover_image_id);

COMMENT ON COLUMN product_packages.cover_image_id IS
    'La portada del paquete (RN-PM-045): la fila de product_images cuyos bytes se sirven sin token en /api/v1/product-images/{id}, la misma ruta que la del producto. NULL = no tiene, y entonces el frontend pinta el icono de promocion y el color por omision: el paquete NO declara icono ni color. Cada subida estrena identificador y la reemplazada se borra.';

COMMENT ON TABLE product_images IS
    'Los bytes de las portadas del catalogo: la de un producto (RN-PM-033) y, desde V11, la de un paquete (RN-PM-045). NO es una entidad: es el valor de products.cover_image_id o de product_packages.cover_image_id, y la tabla no sabe de cual viene cada fila — quien la senala lo dice. Una fila no se modifica; reemplazar la portada es otra fila y la anterior se borra.';
