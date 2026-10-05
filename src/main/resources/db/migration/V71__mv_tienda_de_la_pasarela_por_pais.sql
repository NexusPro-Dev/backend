-- =============================================================================
-- V71 — La tienda de la pasarela local, en la conversión de cada país
-- (RN-MV-063; requirements/mv.md §4.9; 05-10-2026).
--
-- PayRetailers da UNA TIENDA POR PAÍS: un shop_id con su clave secreta. Por
-- decisión del responsable del proyecto viven en la conversión del país, para
-- que un ADMIN los fije por la API sin tocar el entorno, y con la misma historia
-- hacia delante que los precios: fijar inserta una fila nueva, que hereda la
-- tienda de la anterior si no se manda otra.
--
-- La clave secreta se guarda CIFRADA (AES-GCM, con la llave maestra
-- PAYRETAILERS_ENCRYPTION_KEY del entorno): nunca en claro, y la API no la
-- devuelve. Sin tienda, el país no cobra por la pasarela local.
-- =============================================================================

ALTER TABLE country_conversion_rates
    ADD COLUMN shop_id         varchar(64),
    ADD COLUMN shop_secret_key text;

ALTER TABLE country_conversion_rates
    ADD CONSTRAINT ck_country_conversion_rates_tienda CHECK (
        (shop_id IS NULL AND shop_secret_key IS NULL)
        OR (shop_id IS NOT NULL AND btrim(shop_id) <> '' AND shop_secret_key IS NOT NULL)
    );

COMMENT ON COLUMN country_conversion_rates.shop_id IS
    'La tienda de PayRetailers del país (RN-MV-063): con ella se abre y se consulta el cobro.';
COMMENT ON COLUMN country_conversion_rates.shop_secret_key IS
    'La clave secreta de esa tienda, CIFRADA con AES-GCM (v1:<base64>). Nunca en claro.';
