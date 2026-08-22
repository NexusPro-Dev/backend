-- RF-SP-010 · T-02 — Catálogo de permisos.
--
-- Campos tomados de requirements/sp.md §10.1. La tabla es pequeña, inmutable
-- por API (RN-SP-004) y prerrequisito de todo el modelo de autorización: se
-- crea antes que roles porque role_permissions apunta aquí.

CREATE TABLE permissions (
    id          uuid         NOT NULL,
    code        varchar(100) NOT NULL,
    resource    varchar(50)  NOT NULL,
    action      varchar(50)  NOT NULL,
    name        varchar(100) NOT NULL,
    description text         NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_permissions PRIMARY KEY (id),

    -- Restricción TOTAL, no parcial: el catálogo no tiene borrado lógico, de
    -- modo que no existe estado en el que un código deba poder repetirse.
    CONSTRAINT uq_permissions_code UNIQUE (code),

    -- Formato <recurso>:<acción> en minúsculas (security.md §4.4). Admite el
    -- guion medio porque el catálogo aprobado lo usa: audit:read-changes,
    -- users:reset-password, users:assign-supervisor.
    CONSTRAINT ck_permissions_code_format
        CHECK (code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$'),

    -- La restricción más valiosa de la tabla, y no cuesta nada: code es la
    -- concatenación de las otras dos columnas (requirements/sp.md §10.1). Sin
    -- ella, una migración futura podría dejar code = 'roles:read' junto a
    -- resource = 'role', y el filtro por recurso devolvería un catálogo
    -- incoherente SIN QUE NADA FALLARA.
    CONSTRAINT ck_permissions_code_matches
        CHECK (code = resource || ':' || action),

    -- Mismo límite y mismo motivo que en roles: la columna es text y el
    -- catálogo se devuelve entero, sin paginar, de modo que sin cota el
    -- tamaño de la respuesta sería impredecible.
    CONSTRAINT ck_permissions_description_length
        CHECK (description IS NULL OR length(description) <= 500)
);

-- Lo que esta migración deliberadamente NO hace:
--
--   * No cierra el dominio de `action` ni el de `resource`. security.md §4.4
--     admite de forma explícita acciones específicas del dominio, y el propio
--     catálogo ya las tiene. Un CHECK cerrado obligaría a alterar el esquema
--     cada vez que un módulo estrena una acción o un recurso, que es la
--     fricción que RN-SP-004 quiere evitar.
--
--   * No declara `deleted_at`. Un permiso no se elimina: retirarlo exigiría
--     retirar antes sus filas de role_permissions, y la clave foránea con
--     ON DELETE RESTRICT de RF-SP-001 está puesta para que quien lo intente
--     se encuentre con las asociaciones vigentes.
--
--   * No crea índice de búsqueda. Son decenas de filas y se devuelven
--     enteras: el recorrido secuencial es más rápido que consultar un índice.

COMMENT ON TABLE permissions IS
    'Catálogo de permisos del sistema. Inmutable por API (RN-SP-004): '
    'solo se modifica mediante migración Flyway. RF-SP-010.';
COMMENT ON COLUMN permissions.code IS
    'Concatenación resource:action. Columna propia para poder referenciarla '
    'directamente; ck_permissions_code_matches garantiza que no diverja.';
