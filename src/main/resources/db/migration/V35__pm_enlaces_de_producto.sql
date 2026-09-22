-- =============================================================================
-- V35 — Los enlaces de un producto (PM): una tabla anexa con TIPO, en lugar de
-- una columna con un solo enlace.
--
-- Lo que este bloque decide y conviene no perder:
--   · La PAREJA (product_id, type) es la clave primaria, como en
--     `product_package_items`: la fila NO es una entidad, es el valor de un
--     hueco del producto. Sin `id` propio y SIN `deleted_at` — quitar un
--     enlace lo BORRA (RN-PM-048).
--   · Los dos tipos van en un CHECK y no en un catálogo administrable: cada
--     uno trae consigo DÓNDE SE PUBLICA, y eso es código (pm.md §5.2.14).
--   · `url` es NOT NULL, al revés que la columna que reemplaza: no hay enlace
--     sin enlace, y «no tener» pasa a ser NO TENER FILA.
--   · Con `external_id`, la url NO admite `?` ni `#`: el identificador se pega
--     como ÚLTIMO SEGMENTO DE RUTA, y detrás de una cadena de consulta daría
--     un enlace roto QUE RESPONDE 200 (RN-PM-049).
--   · El INSERT que migra `products.video_url` va ANTES del DROP y en el MISMO
--     archivo: Flyway lo aplica en una transacción, y separarlos dejaría el
--     mismo enlace en dos sitios sin decir cuál manda.
-- =============================================================================

CREATE TABLE product_links (
    product_id  uuid         NOT NULL,
    type        varchar(30)  NOT NULL,
    url         varchar(500) NOT NULL,
    external_id varchar(100) NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    -- RN-PM-048: la unicidad «uno por tipo» ES la clave. Sin DEFERRABLE: la
    -- colisión debe morder en el INSERT para que el repositorio la traduzca.
    CONSTRAINT pk_product_links PRIMARY KEY (product_id, type),
    CONSTRAINT ck_product_links_type
        CHECK (type IN ('VIDEO_PRESENTACION', 'CUPON_BOT')),
    -- Es `ck_products_video_url_format` mudada, SIN la rama IS NULL: aquí la
    -- columna es obligatoria y el CHECK no puede evaluar a NULL.
    CONSTRAINT ck_product_links_url_format
        CHECK (url ~ '^https?://[^[:space:]]+$'),
    CONSTRAINT ck_product_links_external_id_format
        CHECK (external_id IS NULL OR external_id ~ '^[^[:space:]]{1,100}$'),
    -- RN-PM-049. CRUZADA, como ck_products_type_target.
    CONSTRAINT ck_product_links_id_sin_consulta
        CHECK (external_id IS NULL OR url !~ '[?#]'),
    -- Sin ON DELETE: el producto no se borra físicamente nunca (RN-PM-010).
    CONSTRAINT fk_product_links_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);

COMMENT ON TABLE product_links IS
    'Los enlaces de un producto, UNO POR TIPO (RN-PM-048). La pareja (product_id, type) es la clave: la fila no es una entidad, es el valor de un hueco del producto. Se borra fisicamente; no hay deleted_at.';
COMMENT ON COLUMN product_links.type IS
    'VIDEO_PRESENTACION (el video que presenta el producto, RN-PM-032) o CUPON_BOT (donde registra su cuenta quien ya compro). El tipo decide DONDE SE PUBLICA: el video en las cuatro lecturas y el hotlink sin token; el cupon SOLO en RF-MV-014 y SOLO en la linea ENTREGADA (RN-PM-050).';
COMMENT ON COLUMN product_links.url IS
    'La DIRECCION, no el recurso. Se comprueba la forma —http(s), sin espacios— y NADA MAS: el sistema no sigue el enlace (pm.md §5.2.8). NOT NULL: no hay enlace sin enlace, y "no tener" es no tener fila.';
COMMENT ON COLUMN product_links.external_id IS
    'Un identificador de un sistema AJENO —quien aloja el bot o el video—: NEXUS lo guarda y lo devuelve SIN INTERPRETARLO. Se pega como ULTIMO SEGMENTO DE RUTA al publicar el enlace (RN-PM-049), y por eso, si esta informado, la url no admite ? ni #.';

-- ---------------------------------------------------------------------------
-- La mudanza de `products.video_url`. Va ANTES del DROP: ningún producto
-- pierde su video. Un producto sin video no obtiene fila, que es exactamente
-- lo que su nulo decía. `created_at` se queda en su DEFAULT —la fila nace
-- ahora—, y no se copia el del producto, que sería inventarle una antigüedad.
-- No hay CUPON_BOT que rellenar: el tipo nace vacío.
-- ---------------------------------------------------------------------------

INSERT INTO product_links (product_id, type, url)
SELECT id, 'VIDEO_PRESENTACION', video_url
FROM products
WHERE video_url IS NOT NULL;

ALTER TABLE products DROP CONSTRAINT ck_products_video_url_format;
ALTER TABLE products DROP COLUMN video_url;
