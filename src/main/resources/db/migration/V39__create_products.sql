-- =============================================================================
-- RF-PM-001 · T-01 — El catálogo de productos.
--
-- La tabla que funda el módulo `PM`. Dos tipos que no se mezclan: el UPGRADE
-- DE MEMBRESIA, que da derecho a pasar al nivel que declara, y el SERVICIO del
-- sistema, que da derecho a una prestación y no toca el nivel de nadie.
--
-- EL PRODUCTO NACE INACTIVO (RN-PM-012), y no es prudencia: si naciera activo,
-- "un solo upgrade activo por destino" (RN-PM-004) habría que comprobarlo en el
-- alta Y en la activación, y la copia que se quedara atrás no fallaría,
-- ADMITIRIA. Naciendo inactivo, esa comprobación vive solo en RF-PM-005.
--
-- LAS DOS CLAVES FORANEAS CRUZAN A `SP`, y no contradicen a D-25. La frontera
-- que modules.md §7 defiende es la del CODIGO: `PM` no lee esas tablas, y todo
-- dato que necesita entra por las interfaces que `SP` publica. La clave foránea
-- es integridad declarada en el motor (Art. V.6) y hace lo que el puerto no
-- puede: impedir que una fila apunte a algo borrado POR DEBAJO de la
-- aplicación. Si llegara a saltar, sería un 500 — y eso significaría que el
-- puerto y la base dejaron de estar de acuerdo: un defecto, no una validación.
-- =============================================================================

CREATE TABLE products (
    id                    uuid          PRIMARY KEY,

    -- Referencia estable desde la que una factura dirá qué se vendió
    -- (RN-PM-013). No se libera nunca, al revés que el nombre.
    code                  varchar(50)   NOT NULL,

    type                  varchar(30)   NOT NULL,
    name                  varchar(150)  NOT NULL,
    description           text          NULL,

    -- Obligatoria en el upgrade, PROHIBIDA en el servicio (RN-PM-002).
    target_membership_id  uuid          NULL,

    -- numeric(14,4) y no numeric(12,2): la escala no puede fijarse en dos
    -- porque `currencies.decimal_places` no siempre vale dos, y ese campo
    -- existe justamente para no asumirlo. La escala EFECTIVA de cada producto
    -- la decide su moneda, y esa comprobación vive en el dominio porque un
    -- CHECK no puede consultar otra tabla.
    price                 numeric(14,4) NOT NULL,
    currency_id           uuid          NOT NULL,

    -- Cuántos días dura lo que el producto otorga, contados desde la compra
    -- (RN-PM-015). NULA significa que lo adquirido NO CADUCA.
    validity_days         integer       NULL,

    status                varchar(20)   NOT NULL DEFAULT 'INACTIVO',

    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),

    -- Retiro lógico. El MOTIVO no está aquí: viaja al registro de eliminación
    -- con la instantánea de lo retirado (Art. V.7 y V.13).
    deleted_at            timestamptz   NULL,

    CONSTRAINT ck_products_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),

    CONSTRAINT ck_products_type
        CHECK (type IN ('UPGRADE_MEMBRESIA', 'SERVICIO')),

    CONSTRAINT ck_products_status
        CHECK (status IN ('ACTIVO', 'INACTIVO')),

    -- RN-PM-002, EN LOS DOS SENTIDOS.
    --
    -- NO PUEDE EVALUAR A NULL, y por eso las dos ramas son predicados IS NULL /
    -- IS NOT NULL: devuelven siempre verdadero o falso. La precaución no es
    -- teórica — `ck_deletion_reason` se escribió con un OR cuyo lado nulo daba
    -- NULL, y un CHECK que devuelve NULL ACEPTA LA FILA: la restricción existía
    -- y no restringía nada (requirements.md v0.31.0).
    CONSTRAINT ck_products_type_target
        CHECK (
            (type = 'UPGRADE_MEMBRESIA' AND target_membership_id IS NOT NULL)
            OR
            (type = 'SERVICIO' AND target_membership_id IS NULL)
        ),

    CONSTRAINT ck_products_price_positive
        CHECK (price > 0),

    -- La rama IS NULL va EXPLICITA aunque `validity_days > 0` sola también
    -- admitiría el nulo —por lo dicho arriba: un CHECK que evalúa a NULL acepta
    -- la fila—. Se escribe para que ese permiso sea DELIBERADO y no accidental,
    -- y para que el día que la vigencia se vuelva obligatoria baste con quitar
    -- esta rama (RN-PM-015).
    CONSTRAINT ck_products_validity_positive
        CHECK (validity_days IS NULL OR validity_days > 0),

    CONSTRAINT ck_products_description_length
        CHECK (description IS NULL OR length(description) <= 1000),

    CONSTRAINT fk_products_target_membership
        FOREIGN KEY (target_membership_id) REFERENCES memberships (id),

    CONSTRAINT fk_products_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id)
);

COMMENT ON TABLE products IS
    'Catalogo de venta del modulo PM: upgrades de membresia y servicios del sistema.';
COMMENT ON COLUMN products.code IS
    'Referencia estable e inmutable. NO se libera al retirar el producto (RN-PM-013).';
COMMENT ON COLUMN products.validity_days IS
    'Dias que dura lo adquirido, desde la compra. Nulo: no caduca (RN-PM-015).';
COMMENT ON COLUMN products.price IS
    'Escala 4 para admitir monedas de mas de dos decimales. La efectiva la fija la moneda.';

-- RN-PM-013 — RESTRICCION TOTAL, no parcial: al revés que el nombre, el código
-- no se libera al retirar un producto. El día que una factura diga
-- `UPGRADE_ORO` tiene que resolver a UN SOLO producto para siempre.
ALTER TABLE products
    ADD CONSTRAINT uq_products_code UNIQUE (code);

-- RN-PM-005 — Indice unico FUNCIONAL y PARCIAL: una restricción de tabla no
-- admite ni expresión ni condición. Sobre la forma normalizada, para que
-- `Plan Oro` y `plan oro` no convivan; parcial, para que el nombre SI se libere
-- al retirar. `f_unaccent` existe desde V1 y está declarada IMMUTABLE
-- precisamente para poder indexarse.
CREATE UNIQUE INDEX uq_products_name
    ON products (f_unaccent(lower(name)))
    WHERE deleted_at IS NULL;

-- RN-PM-004 — Un solo upgrade ACTIVO por destino. Se declara aquí, con la
-- tabla, aunque solo RF-PM-005 pueda violarla: el esquema es de quien crea la
-- tabla.
--
-- ES UN INDICE PARCIAL Y POR TANTO NO ADMITE DEFERRABLE, que es propiedad de
-- una RESTRICCION y no de un índice: morderá en la sentencia que lo viole y no
-- en el COMMIT. El adaptador lo traduce ahí, con flush explícito.
CREATE UNIQUE INDEX uq_products_upgrade_target
    ON products (target_membership_id)
    WHERE type = 'UPGRADE_MEMBRESIA' AND status = 'ACTIVO' AND deleted_at IS NULL;
