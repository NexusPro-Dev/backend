-- =============================================================================
-- RF-PM-001 · RN-PM-023, RN-PM-024, RN-PM-006
-- UN PRODUCTO LLEVA DOS PRECIOS, Y SOLO UNO DE ELLOS ES DINERO.
--
-- `price` es EL QUE SE COBRA: lo copia `movement_details.unit_price` y sobre el
-- calcula `RN-CM-019`. `public_price` es LO QUE SE ANUNCIA, es OPCIONAL, y
-- NINGUNA OTRA TABLA LO LEE (`requirements/pm.md` §5.2.4).
--
-- LA MISMA FORMA Y LA MISMA MONEDA. `numeric(14,4)` porque es el mismo dinero
-- en la misma moneda: no hay una segunda `currency_id`, porque un importe en
-- otra moneda no seria un rotulo sino un segundo precio de verdad, con su tasa
-- y su vigencia — y eso lo resuelve `RF-SP-047`, no esta tabla.
--
-- LO QUE NO COMPARTEN ES LA OBLIGATORIEDAD, Y AHI ESTA TODA LA DECISION: EL
-- NULO SIGNIFICA «ESTE PRODUCTO NO DECLARA PRECIO PUBLICO» —y entonces se
-- anuncia con `price`—, NO «VALE CERO». Los dos estados existen y son
-- distintos, y por eso la columna admite nulo en lugar de llevar `DEFAULT 0`.
--
-- ESTA MIGRACION SE PLANIFICO COMO `V65` Y ACABO EN `V67`, el mismo dia: las
-- tasas de cambio (`RF-SP-047`) se llevaron `V65` y `V66` antes de que esta
-- tuviera una linea de SQL escrita. Es la cuarta vez que le pasa a este
-- proyecto y `modelo-datos.md` v0.23.0 ya lo tenia escrito: UNA MIGRACION
-- RESERVADA NO ESTA RESERVADA, y Flyway deja fuera SIN ERROR Y SIN AVISO una
-- migracion con numero por debajo del ultimo aplicado.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. La columna, opcional.
--
-- SIN RELLENO, Y ESA ES LA DIFERENCIA CON `V53` Y `V59`. Aquellas tuvieron que
-- DECIDIR un valor para las filas existentes —`FREE` como origen, `TIENDA` y
-- `MANUAL`— porque las columnas quedaban obligatorias. Aqui no hace falta
-- ninguna decision: EL NULO YA SIGNIFICA LO CORRECTO para todo producto ya
-- registrado —«se anuncia con el precio del sistema»—, que es exactamente lo
-- que hacian ayer.
--
-- `updated_at` NO se toca, por lo mismo que en `V38`, `V43` y `V59`: esa marca
-- dice cuando cambio el producto como hecho de negocio, y anadir una columna
-- nueva no lo es.
-- -----------------------------------------------------------------------------

ALTER TABLE products ADD COLUMN public_price numeric(14, 4) NULL;

-- -----------------------------------------------------------------------------
-- 2. `RN-PM-006` se relaja: de «mayor que cero» a «no negativo».
--
-- LO QUE TUMBO LA MITAD QUE SE VA NO ES EL PRECIO PUBLICO, ES LA RENOVACION: un
-- upgrade `FREE → FREE` es un producto legitimo que vale CERO, y prohibirlo
-- obligaba a inventarle un centimo (`requirements/pm.md` §5.2.3 y §5.2.4).
--
-- LA RESTRICCION CAMBIA DE NOMBRE CON EL UMBRAL, Y ES DELIBERADO. Dejarle
-- `ck_products_price_positive` haria que quien lo leyera creyera que el cero
-- sigue prohibido — que es EXACTAMENTE lo que `ProductCommissionCapGuard` de
-- `CM` creia, y lo decia por escrito en su Javadoc citando esta restriccion
-- por su nombre.
-- -----------------------------------------------------------------------------

ALTER TABLE products DROP CONSTRAINT ck_products_price_positive;

ALTER TABLE products
    ADD CONSTRAINT ck_products_price_no_negativo
        CHECK (price >= 0);

-- -----------------------------------------------------------------------------
-- 3. El mismo umbral para el precio publico, con su rama `IS NULL` EXPLICITA.
--
-- `public_price >= 0` sola tambien admitiria el nulo —un CHECK que evalua a
-- NULL ACEPTA la fila—, y se escribe entera por lo mismo que en
-- `ck_products_validity_positive` y `ck_products_icon_solo_upgrade`: para que
-- ese permiso sea DELIBERADO y no accidental. El dia que el precio publico se
-- vuelva obligatorio, basta con quitar esa rama.
--
-- ESTE PROYECTO YA PAGO UNA VEZ POR NO ESCRIBIRLA: `ck_deletion_reason` se
-- escribio con un OR cuyo lado nulo evaluaba a NULL, y la restriccion existia
-- sin restringir nada.
-- -----------------------------------------------------------------------------

ALTER TABLE products
    ADD CONSTRAINT ck_products_public_price_no_negativo
        CHECK (public_price IS NULL OR public_price >= 0);

-- -----------------------------------------------------------------------------
-- 4. Lo que este esquema NO puede declarar, y hay que dejar dicho.
--
-- QUE UN IMPORTE NO SE COBRE no cabe en ninguna restriccion. Es la TERCERA
-- regla de esta tabla que el motor no sostiene, junto a `RN-PM-007` —los
-- decimales dependen de otra tabla— y `RN-PM-017` —el nivel de dos filas de
-- `memberships`—.
--
-- Lo unico que sostiene `RN-PM-023` es DONDE NO APARECE: `movement_details`
-- copia `price`, y el puerto que `PM` publica para vender —
-- `ProductCatalog.saleViewOf`— no lleva el otro. ANADIRLO AHI BASTARIA PARA QUE
-- EMPEZARA A COBRARSE SIN QUE NADA FALLARA.
--
-- Y `RN-PM-024` —que el precio del sistema no salga de administracion— tampoco
-- es de esquema: es forma de la respuesta. La sostienen `OfferItem` y la
-- respuesta del hotlink al no tener donde poner el segundo importe.
-- -----------------------------------------------------------------------------

COMMENT ON COLUMN products.price IS
    'El precio que SE COBRA: lo copia movement_details.unit_price y sobre el '
    'calcula RN-CM-019. Desde V67 admite CERO (RN-PM-006), porque una '
    'renovacion de una membresia gratuita vale eso.';

COMMENT ON COLUMN products.public_price IS
    'El precio con el que el producto SE ANUNCIA a quien no administra el '
    'catalogo (RN-PM-023). NO SE COBRA y ningun calculo lo lee. NULL significa '
    '"no declara precio publico" —se anuncia con price—, NO "vale cero".';
