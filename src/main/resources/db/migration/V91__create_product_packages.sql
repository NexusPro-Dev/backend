-- =============================================================================
-- RF-PM-017 · T-01 — Los paquetes de productos (`requirements/pm.md` §10.6).
--
-- DOS TABLAS EN UNA MIGRACIÓN, a propósito: las dos definen el paquete, y una
-- migración por tabla dejaría en el historial un estado en el que el paquete
-- existe y no puede contener nada. La segunda la estrena `RF-PM-023`.
--
-- EL PAQUETE NO TIENE PRECIO (`RN-PM-036`). Su precio es la suma de sus
-- productos con su descuento, SE CALCULA EN CADA LECTURA y no se guarda en
-- ninguna columna: si mañana alguien añade `price` «para no sumar cada vez»,
-- la copia que se quede atrás no fallará — mentirá. Es la misma decisión que
-- el promedio de las reseñas (`V87`).
--
-- SÍ TIENE MONEDA, obligatoria e inmutable (`RN-PM-035`): un paquete recién
-- creado no tiene productos y aun así sabe en qué se expresa. Solo se le
-- asocian productos en esa misma moneda, y eso no cabe en un CHECK —compara
-- con otra tabla—: vive en el caso de uso de `RF-PM-023`.
--
-- HEREDA LA FORMA DEL PRODUCTO (`RN-PM-041`): código único INCLUSO frente a
-- los retirados, nombre único entre los vivos sin acentos ni mayúsculas, nace
-- `INACTIVO`, alcance obligatorio, retiro lógico con motivo. Las restricciones
-- son las de `V39`, con otro nombre.
--
-- NINGUNA CLAVE FORÁNEA LLEVA `ON DELETE`: ni el paquete, ni el producto, ni
-- la moneda se borran físicamente. La fila de asociación SÍ se borra
-- físicamente (`RF-PM-025`, Art. V.13), pero desde el caso de uso y con su
-- registro de eliminación — no en cascada.
-- =============================================================================

CREATE TABLE product_packages (
    id           uuid         PRIMARY KEY,
    code         varchar(50)  NOT NULL,
    name         varchar(150) NOT NULL,

    -- Nula al nacer es un estado normal: el paquete se ARMA inactivo. Lo que
    -- no se admite es publicarlo sin ella (`RN-PM-040`), y eso lo mira
    -- `RF-PM-021`, no el esquema.
    description  text         NULL,

    currency_id  uuid         NOT NULL,

    -- CON valor por omisión, al revés que `products.status`, porque aquí no
    -- hay decisión que dejar a la vista: `RN-PM-041` dice que nace INACTIVO y
    -- el dominio no recibe el estado.
    status       varchar(20)  NOT NULL DEFAULT 'INACTIVO',

    -- SIN valor por omisión, como en `products` y por lo mismo: un paquete
    -- guardado con el alcance supuesto se vería igual que uno declarado.
    -- EL MISMO DOMINIO QUE `products.scope` DESDE EL 15-09-2026 (`RN-PM-019`
    -- reescrita, V92): cuatro valores explícitos y no una escala acumulativa.
    -- TIENDA solo tienda, HOTLINK solo hotlinks, AMBOS las dos vistas, y
    -- NINGUNO —existe y se activa, pero no se ofrece en ninguna; solo
    -- administración lo ve—. El alcance ES DEL PAQUETE: el de sus productos
    -- no filtra dentro de él.
    scope        varchar(20)  NOT NULL,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    deleted_at   timestamptz  NULL,

    -- RN-PM-041 por RN-PM-013: el código NO SE LIBERA JAMÁS. Restricción total
    -- y no índice parcial, por eso.
    CONSTRAINT uq_product_packages_code
        UNIQUE (code),

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

-- -----------------------------------------------------------------------------
-- RN-PM-041 por RN-PM-005: el nombre es único entre los VIVOS, sin distinguir
-- acentos ni mayúsculas. Parcial, y por parcial no admite `DEFERRABLE`: la
-- carrera entre dos altas simultáneas muerde en el segundo INSERT y el
-- repositorio la traduce ahí al mismo `409` que la comprobación previa
-- (hallazgo de `RF-SP-019`).
-- -----------------------------------------------------------------------------

CREATE UNIQUE INDEX uq_product_packages_name
    ON product_packages (f_unaccent(lower(name)))
    WHERE deleted_at IS NULL;


-- =============================================================================
-- La asociación paquete-producto con su descuento (`RN-PM-037`, `RN-PM-038`).
--
-- LA CLAVE ES LA PAREJA: un producto entra UNA vez por paquete y no hay
-- cantidad — «dos veces el mismo bot» sería otro producto. El mismo producto
-- puede estar en varios paquetes con descuentos distintos: el descuento es
-- DEL PAQUETE, no del producto.
--
-- SIN `id` PROPIO, como `role_permissions`: la fila no se referencia desde
-- ningún sitio y se borra físicamente al desasociar (`RF-PM-025`).
-- =============================================================================

CREATE TABLE product_package_items (
    package_id      uuid          NOT NULL,
    product_id      uuid          NOT NULL,

    -- PORCENTAJE de 0 a 100, o FIJO de 0 al precio del producto (RN-PM-037).
    -- La misma escala que `products.price` para que el fijo quepa entero.
    discount_type   varchar(20)   NOT NULL,
    discount_value  numeric(14,4) NOT NULL,

    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT pk_product_package_items
        PRIMARY KEY (package_id, product_id),

    CONSTRAINT fk_product_package_items_package
        FOREIGN KEY (package_id) REFERENCES product_packages (id),

    CONSTRAINT fk_product_package_items_product
        FOREIGN KEY (product_id) REFERENCES products (id),

    CONSTRAINT ck_product_package_items_type
        CHECK (discount_type IN ('PORCENTAJE', 'FIJO')),

    -- RN-PM-037: el cero se admite —un producto que entra sin rebaja— y el
    -- negativo no, en las dos formas.
    CONSTRAINT ck_product_package_items_value
        CHECK (discount_value >= 0),

    -- EL ÚNICO TECHO QUE CABE AQUÍ. El del fijo es el precio del producto, que
    -- está en otra tabla: vive en el caso de uso de `RF-PM-023` y `RF-PM-024`,
    -- y se comprueba contra el precio DE HOY.
    CONSTRAINT ck_product_package_items_percentage
        CHECK (discount_type <> 'PORCENTAJE' OR discount_value <= 100)
);

COMMENT ON TABLE product_package_items IS
    'Producto dentro de un paquete con su descuento (RF-PM-023). La pareja es la clave (RN-PM-038); se borra físicamente al desasociar (RF-PM-025).';

-- -----------------------------------------------------------------------------
-- No implementa ninguna regla: la clave primaria ya sostiene «los productos
-- de un paquete». Este sostiene la pregunta inversa —«¿en qué paquetes está
-- este producto?»—, que es la que hará quien retire o desactive un producto y
-- quiera saber qué paquetes deja de ofrecer.
-- -----------------------------------------------------------------------------

CREATE INDEX ix_product_package_items_product
    ON product_package_items (product_id);
