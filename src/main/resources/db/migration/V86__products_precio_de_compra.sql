-- =============================================================================
-- RF-PM-001 · RN-PM-023, RN-PM-024 (12-09-2026)
-- EL PRECIO PUBLICO SE CONVIERTE EN PRECIO DE COMPRA: LA MISMA COLUMNA, OTRO
-- SIGNIFICADO, Y POR ESO OTRO ALCANCE DE VISIBILIDAD.
--
-- `V67` anadio `public_price` como LO QUE SE ANUNCIA a quien no administra.
-- Por decision del responsable del proyecto ese segundo importe pasa a ser LO
-- QUE NEXUS PAGA POR EL PRODUCTO cuando tiene que comprarlo —el costo—, y ahi
-- se guarda lo que costo (`requirements/pm.md` §5.2.6).
--
-- `price` NO CAMBIA: sigue siendo lo unico que la venta copia y sobre lo que
-- comisiona `CM`. El cliente sigue pagando `price`.
--
-- LO QUE CAMBIA DE VERDAD NO ESTA EN ESTE ARCHIVO. Cuando el numero era un
-- rotulo, publicarlo en la oferta y en el hotlink —sin token— era una decision
-- de forma; ahora que es el costo, publicarlo ENSENA EL MARGEN. `RN-PM-024`
-- vuelve a ser critica: el precio de compra NO SALE DE ADMINISTRACION, y lo
-- sostienen `OfferItem` y la respuesta del hotlink al NO TENER el campo y sus
-- consultas al NO SELECCIONAR la columna. Eso es codigo, no esquema.
--
-- `V67` NO SE TOCA. Una migracion aplicada no se edita (ver la cabecera de
-- `V85`): el renombrado va en la suya, y el historial conserva que la columna
-- nacio con otro nombre y otro significado.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Se VACIA la columna ANTES de renombrarla.
--
-- Lo que hubiera en `public_price` era un precio anunciado, NO UN COSTO.
-- Dejarlo con el nombre nuevo seria mentir con datos: un informe de margenes
-- leeria como «lo que pago NEXUS» un numero que nadie declaro con ese
-- significado. La columna cambia de significado VACIA DEL ANTERIOR.
--
-- Es la unica sentencia de esta migracion que no es un renombrado, y es la
-- que la diferencia de `V67`: aquella no relleno nada porque el nulo ya
-- significaba lo correcto; esta vacia porque lo que habia significaba OTRA
-- cosa. `updated_at` NO se toca: el producto no cambio como hecho de negocio,
-- cambio lo que el sistema entiende por su segunda columna de dinero.
-- -----------------------------------------------------------------------------

UPDATE products SET public_price = NULL WHERE public_price IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. El renombrado de la columna. Tipo, escala y opcionalidad se conservan:
-- la forma era correcta —el mismo dinero en la misma moneda— y sigue siendolo.
-- EL NULO SIGUE SIGNIFICANDO ALGO Y NO ES CERO: ahora «NO SE CONOCE» —el
-- producto no se ha comprado todavia, o no aplica—, mientras que cero dice que
-- no costo nada. Por eso la columna sigue sin `DEFAULT 0`.
-- -----------------------------------------------------------------------------

ALTER TABLE products RENAME COLUMN public_price TO purchase_price;

-- -----------------------------------------------------------------------------
-- 3. El `CHECK` se renombra con la columna, por lo mismo que
-- `ck_products_price_positive` se renombro con su umbral en `V67`: un nombre
-- que dice «publico» sobre un costo MIENTE, y quien lo lea en un error del
-- motor buscara una columna que ya no existe. La condicion no cambia:
-- `purchase_price IS NULL OR purchase_price >= 0`, con la rama `IS NULL`
-- delante y explicita.
-- -----------------------------------------------------------------------------

ALTER TABLE products
    RENAME CONSTRAINT ck_products_public_price_no_negativo
    TO ck_products_purchase_price_no_negativo;

-- -----------------------------------------------------------------------------
-- 4. El comentario decia «se anuncia»; ahora dice que es y que significa su
-- nulo. Y dice DONDE NO SE VE, que es lo que un lector del esquema no puede
-- deducir de la columna.
-- -----------------------------------------------------------------------------

COMMENT ON COLUMN products.purchase_price IS
    'Lo que NEXUS PAGA por el producto cuando tiene que comprarlo; ahi se '
    'guarda lo que costo (RN-PM-023). NO SE COBRA, ningun calculo lo lee y NO '
    'SALE DE ADMINISTRACION: la oferta y el hotlink no lo seleccionan '
    '(RN-PM-024). NULL significa "no se conoce", NO "costo cero". Se llamo '
    'public_price —lo que se anunciaba— desde V67 hasta V86.';
