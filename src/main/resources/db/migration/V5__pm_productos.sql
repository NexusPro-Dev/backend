-- =============================================================================
-- V5 — Productos y mercadeo (PM): el catálogo, sus portadas, sus reseñas y
-- los paquetes.
--
-- Lo que este bloque decide y conviene no perder:
--   · Un producto es un UPGRADE de membresía (sale de una y lleva a otra, y
--     puede ser la misma: entonces es una renovación y vende tiempo) o un BOT.
--     La condición cruzada está en `ck_products_type_target`.
--   · El CÓDIGO no se libera jamás —unicidad total—; el NOMBRE sí, entre los
--     vivos —índice parcial, que por parcial no admite DEFERRABLE y muerde en
--     el INSERT: el repositorio traduce la carrera—.
--   · `price` es lo que SE COBRA y admite cero; `purchase_price` es lo que
--     NEXUS PAGA, no se cobra y no sale de administración (RN-PM-023/024).
--   · El alcance (`scope`) son CUATRO valores explícitos (RN-PM-019).
--   · El promedio de las reseñas y el precio del paquete NO se guardan: se
--     calculan en cada lectura (RN-PM-031, RN-PM-036). Una columna que se
--     quedara atrás no fallaría, mentiría.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Los bytes de la portada (RN-PM-033). NO es una entidad: es el valor de
-- `products.cover_image_id`. Una fila no se modifica: reemplazar la portada es
-- otra fila, y la anterior se borra. Va antes que `products` por la FK.
-- ---------------------------------------------------------------------------

CREATE TABLE product_images (
    id           uuid        PRIMARY KEY,
    content_type varchar(30) NOT NULL,
    content      bytea       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_product_images_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_product_images_size
        CHECK (octet_length(content) >= 1 AND octet_length(content) <= 5242880)
);

COMMENT ON TABLE product_images IS
    'Los bytes de la portada de un producto (RN-PM-033). NO es una entidad: es el valor de products.cover_image_id. Una fila no se modifica; reemplazar la portada es otra fila y la anterior se borra.';
COMMENT ON COLUMN product_images.content_type IS
    'El tipo REAL, detectado en los primeros bytes al subir — no la cabecera de la peticion. Es lo que se devuelve al servir la imagen.';
COMMENT ON COLUMN product_images.content IS
    'Los bytes TAL CUAL se subieron: sin recorte, redimension ni conversion. De 1 byte a 5 MB.';

-- ---------------------------------------------------------------------------
-- El catálogo de venta.
-- ---------------------------------------------------------------------------

CREATE TABLE products (
    id                   uuid          PRIMARY KEY,
    code                 varchar(50)   NOT NULL,
    type                 varchar(30)   NOT NULL,
    name                 varchar(150)  NOT NULL,
    description          text          NULL,
    icon                 varchar(50)   NULL,
    video_url            varchar(500)  NULL,
    cover_image_id       uuid          NULL,
    source_membership_id uuid          NULL,
    target_membership_id uuid          NULL,
    price                numeric(14,4) NOT NULL,
    purchase_price       numeric(14,4) NULL,
    currency_id          uuid          NOT NULL,
    validity_days        integer       NULL,
    scope                varchar(20)   NOT NULL,
    implementation       varchar(20)   NOT NULL,
    status               varchar(20)   NOT NULL DEFAULT 'INACTIVO',
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    deleted_at           timestamptz   NULL,

    -- RN-PM-013: el código no se libera al retirar. Restricción total.
    CONSTRAINT uq_products_code UNIQUE (code),
    -- Una portada pertenece a UN producto.
    CONSTRAINT uq_products_cover_image UNIQUE (cover_image_id),
    CONSTRAINT ck_products_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_products_type
        CHECK (type IN ('UPGRADE_MEMBRESIA', 'BOT')),
    CONSTRAINT ck_products_status
        CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_products_scope
        CHECK (scope IN ('TIENDA', 'HOTLINK', 'AMBOS', 'NINGUNO')),
    CONSTRAINT ck_products_implementation
        CHECK (implementation IN ('AUTOMATICA', 'MANUAL')),
    -- RN-PM-002: el upgrade exige LAS DOS membresías y el bot las tiene prohibidas.
    CONSTRAINT ck_products_type_target
        CHECK ((type = 'UPGRADE_MEMBRESIA' AND target_membership_id IS NOT NULL AND source_membership_id IS NOT NULL)
            OR (type = 'BOT' AND target_membership_id IS NULL AND source_membership_id IS NULL)),
    CONSTRAINT ck_products_description_length
        CHECK (description IS NULL OR length(description) <= 1000),
    CONSTRAINT ck_products_icon_format
        CHECK (icon IS NULL OR icon ~ '^[a-z][a-z0-9-]*$'),
    -- RN-PM-016: el icono solo en el upgrade.
    CONSTRAINT ck_products_icon_solo_upgrade
        CHECK (icon IS NULL OR type = 'UPGRADE_MEMBRESIA'),
    CONSTRAINT ck_products_video_url_format
        CHECK (video_url IS NULL OR video_url ~ '^https?://[^[:space:]]+$'),
    -- RN-PM-006: el cero se admite —una renovación gratuita vale eso—.
    CONSTRAINT ck_products_price_no_negativo
        CHECK (price >= 0),
    CONSTRAINT ck_products_purchase_price_no_negativo
        CHECK (purchase_price IS NULL OR purchase_price >= 0),
    CONSTRAINT ck_products_validity_positive
        CHECK (validity_days IS NULL OR validity_days > 0),
    CONSTRAINT fk_products_source_membership
        FOREIGN KEY (source_membership_id) REFERENCES memberships (id),
    CONSTRAINT fk_products_target_membership
        FOREIGN KEY (target_membership_id) REFERENCES memberships (id),
    CONSTRAINT fk_products_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id),
    CONSTRAINT fk_products_cover_image
        FOREIGN KEY (cover_image_id) REFERENCES product_images (id)
);

COMMENT ON TABLE products IS
    'Catalogo de venta del modulo PM: upgrades de membresia y bots del sistema.';
COMMENT ON COLUMN products.code IS
    'Referencia estable e inmutable. NO se libera al retirar el producto (RN-PM-013).';
COMMENT ON COLUMN products.price IS
    'El precio que SE COBRA: lo copia movement_details.unit_price y sobre el calcula RN-CM-019. Admite CERO (RN-PM-006), porque una renovacion de una membresia gratuita vale eso.';
COMMENT ON COLUMN products.purchase_price IS
    'Lo que NEXUS PAGA por el producto cuando tiene que comprarlo; ahi se guarda lo que costo (RN-PM-023). NO SE COBRA, ningun calculo lo lee y NO SALE DE ADMINISTRACION: la oferta y el hotlink no lo seleccionan (RN-PM-024). NULL significa "no se conoce", NO "costo cero".';
COMMENT ON COLUMN products.validity_days IS
    'Dias que dura lo adquirido, desde la compra. Nulo: no caduca (RN-PM-015).';
COMMENT ON COLUMN products.icon IS
    'Nombre del icono para el frontend, no una imagen. Solo en upgrade y opcional (RN-PM-016).';
COMMENT ON COLUMN products.video_url IS
    'La DIRECCION de un video que presenta el producto, no el video (RN-PM-032). Se comprueba la forma —http(s), sin espacios— y NADA MAS: el sistema no sigue el enlace. NULL significa "no tiene video". SE PUBLICA en las cuatro lecturas, hotlink sin token incluido: es material de venta, no un costo — al reves que purchase_price.';
COMMENT ON COLUMN products.cover_image_id IS
    'La portada (RN-PM-033): la fila de product_images cuyos bytes se sirven sin token en /api/v1/product-images/{id}. NULL = no tiene. En un upgrade, si es NULL el icono es obligatorio (RN-PM-034, en el dominio). Cada subida estrena identificador y la reemplazada se borra.';
COMMENT ON COLUMN products.source_membership_id IS
    'De que membresia sale el upgrade. Obligatoria en UPGRADE_MEMBRESIA y prohibida en BOT (`RN-PM-002`). NO tiene por que ser la inmediatamente inferior al destino: saltar niveles es legitimo (`RN-PM-018`). PUEDE SER LA MISMA que el destino: entonces el producto es una RENOVACION y lo que vende es tiempo (`RN-PM-017`).';
COMMENT ON COLUMN products.scope IS
    'En que vistas de venta se ofrece el producto (RN-PM-019): TIENDA (solo la oferta), HOTLINK (solo el canal de hotlinks), AMBOS, o NINGUNO (activo o no, no se ofrece en ninguna vista; solo administracion).';
COMMENT ON COLUMN products.implementation IS
    'Si lo comprado se aplica solo (AUTOMATICA) o espera a que un funcionario lo autorice (MANUAL). Gobierna que hace MV al confirmar (RN-PM-020, RN-MV-020).';

-- RN-PM-005: el nombre es único entre los VIVOS; parcial, y por parcial sin DEFERRABLE.
CREATE UNIQUE INDEX uq_products_name ON products (f_unaccent(lower(name))) WHERE deleted_at IS NULL;
-- RN-PM-004: un solo upgrade ACTIVO por pareja origen→destino. Se comprueba al
-- activar, que es el motivo de que el producto nazca inactivo.
CREATE UNIQUE INDEX uq_products_upgrade_target
    ON products (source_membership_id, target_membership_id)
    WHERE type = 'UPGRADE_MEMBRESIA' AND status = 'ACTIVO' AND deleted_at IS NULL;
CREATE INDEX ix_products_listado  ON products (created_at DESC, id DESC);
CREATE INDEX ix_products_busqueda ON products USING gin (f_unaccent(lower(name)) gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- Reseñas (RF-PM-009 a RF-PM-013). `user_id` es el AUTOR, no el actor (Art.
-- V.7), y solo él toca la fila (RN-PM-027). Una por persona y producto entre
-- las vivas; retirada la suya puede escribir otra. Se retira SIN motivo
-- declarado: es la excepción del contenido propio en el Art. V.13.
-- ---------------------------------------------------------------------------

CREATE TABLE product_comments (
    id         uuid        PRIMARY KEY,
    product_id uuid        NOT NULL,
    user_id    uuid        NOT NULL,
    rating     smallint    NOT NULL,
    comment    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz NULL,

    CONSTRAINT ck_product_comments_rating
        CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_product_comments_comment_length
        CHECK (char_length(btrim(comment)) BETWEEN 1 AND 1000),
    CONSTRAINT fk_product_comments_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_comments_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

-- RN-PM-026: una reseña viva por persona y producto.
CREATE UNIQUE INDEX uq_product_comments_autor ON product_comments (product_id, user_id) WHERE deleted_at IS NULL;
-- La lista pública en su orden, y el agregado de RN-PM-031, que corre sobre este predicado.
CREATE INDEX ix_product_comments_product
    ON product_comments (product_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Paquetes (RF-PM-017 a RF-PM-026). SIN precio: es la suma de sus productos
-- con su descuento, calculada en cada lectura (RN-PM-036). SÍ con moneda,
-- obligatoria e inmutable (RN-PM-035): un paquete vacío también sabe en qué se
-- expresa, y solo reúne productos en esa moneda. Hereda la forma del producto
-- (RN-PM-041): código que no se libera, nombre único entre vivos, nace
-- INACTIVO, mismo dominio de alcance.
-- ---------------------------------------------------------------------------

CREATE TABLE product_packages (
    id          uuid         PRIMARY KEY,
    code        varchar(50)  NOT NULL,
    name        varchar(150) NOT NULL,
    description text         NULL,
    currency_id uuid         NOT NULL,
    status      varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    scope       varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz  NULL,

    CONSTRAINT uq_product_packages_code UNIQUE (code),
    CONSTRAINT ck_product_packages_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_product_packages_status
        CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_product_packages_scope
        CHECK (scope IN ('TIENDA', 'HOTLINK', 'AMBOS', 'NINGUNO')),
    CONSTRAINT fk_product_packages_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id)
);

COMMENT ON TABLE product_packages IS
    'Paquete de productos (RF-PM-017). SIN precio propio: es la suma de sus productos con su descuento, calculada en cada lectura (RN-PM-036).';
COMMENT ON COLUMN product_packages.currency_id IS
    'Moneda del paquete, inmutable; solo reúne productos en esta moneda (RN-PM-035).';

CREATE UNIQUE INDEX uq_product_packages_name
    ON product_packages (f_unaccent(lower(name))) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- La asociación paquete-producto con su descuento (RN-PM-037, RN-PM-038). La
-- PAREJA es la clave: un producto entra una vez por paquete y no hay cantidad.
-- Sin id propio y se borra físicamente al desasociar (RF-PM-025). El único
-- techo que cabe aquí es el del porcentaje; el del fijo es el precio del
-- producto, que está en otra tabla y se comprueba en el caso de uso contra el
-- precio DE HOY.
-- ---------------------------------------------------------------------------

CREATE TABLE product_package_items (
    package_id     uuid          NOT NULL,
    product_id     uuid          NOT NULL,
    discount_type  varchar(20)   NOT NULL,
    discount_value numeric(14,4) NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_product_package_items PRIMARY KEY (package_id, product_id),
    CONSTRAINT ck_product_package_items_type
        CHECK (discount_type IN ('PORCENTAJE', 'FIJO')),
    CONSTRAINT ck_product_package_items_value
        CHECK (discount_value >= 0),
    CONSTRAINT ck_product_package_items_percentage
        CHECK (discount_type <> 'PORCENTAJE' OR discount_value <= 100),
    CONSTRAINT fk_product_package_items_package
        FOREIGN KEY (package_id) REFERENCES product_packages (id),
    CONSTRAINT fk_product_package_items_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);

COMMENT ON TABLE product_package_items IS
    'Producto dentro de un paquete con su descuento (RF-PM-023). La pareja es la clave (RN-PM-038); se borra físicamente al desasociar (RF-PM-025).';

CREATE INDEX ix_product_package_items_product ON product_package_items (product_id);
