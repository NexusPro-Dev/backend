-- =============================================================================
-- RF-SP-001 · T-02 — Tabla `roles`.
--
-- Campos de `requirements/sp.md` §10.2, restricciones de §10.8.
--
-- `RN-SEG-003`, `RN-SEG-006` y `RN-SEG-010` NO son expresables como restricción
-- declarativa y se verifican en la capa de dominio.
-- =============================================================================

CREATE TABLE roles (
    id             uuid         PRIMARY KEY,
    code           varchar(50)  NOT NULL,
    name           varchar(100) NOT NULL,
    description    text         NULL,
    role_type      varchar(20)  NOT NULL,
    parent_role_id uuid         NULL,
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVO',
    is_system      boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL,

    -- `RN-SEG-008`: la base de datos impide dejar hijos huérfanos aunque la
    -- aplicación falle. El borrado del rol es lógico (`RF-SP-009`), de modo que
    -- no hay cascada que declarar.
    CONSTRAINT fk_roles_parent
        FOREIGN KEY (parent_role_id) REFERENCES roles (id) ON DELETE RESTRICT,

    CONSTRAINT ck_roles_status CHECK (status IN ('ACTIVO', 'INACTIVO')),

    CONSTRAINT ck_roles_type
        CHECK (role_type IN ('FUNCIONARIO', 'VENDEDOR', 'CONSUMIDOR')),

    -- `VAL-008`. En el esquema y no solo en Java: la garantía vale también para
    -- las migraciones de poblado y para cualquier punto de entrada futuro; en
    -- Java la validación solo cubre la API.
    CONSTRAINT ck_roles_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),

    -- `VAL-007`. La columna es `text`: sin CHECK, cualquier otro punto de
    -- entrada la deja sin acotar y el listado de `RF-SP-002` devolvería
    -- respuestas de tamaño impredecible con hasta cien filas por página.
    CONSTRAINT ck_roles_description_length
        CHECK (description IS NULL OR length(description) <= 500),

    -- Ciclo de longitud uno. No lo ejercita este requerimiento —el rol aún no
    -- existe— pero sí `RF-SP-008`, y cuesta una línea declararlo al crear la
    -- tabla en lugar de alterarla después.
    CONSTRAINT ck_roles_parent_not_self
        CHECK (parent_role_id IS NULL OR parent_role_id <> id)
);

COMMENT ON TABLE roles IS
    'Roles del sistema. parent_role_id acota privilegios (RN-SEG-003); no concede herencia.';
COMMENT ON COLUMN roles.parent_role_id IS
    'Nulo únicamente en el rol raíz (RN-SEG-007, RN-SP-002). Expresa además el orden comercial (RN-SP-011).';
COMMENT ON COLUMN roles.is_system IS
    'Solo lo pone en true la migración de poblado. Un rol creado por la API nunca es de sistema.';

-- `RN-SEG-001`. Índices PARCIALES y no restricciones únicas corrientes: una
-- restricción corriente bloquearía para siempre el código de un rol eliminado y
-- haría imposible `CA-SP-006`. La alternativa de renombrar el código al
-- eliminar (CONTABILIDAD_20260820) mantiene la restricción simple, pero corrompe
-- un dato de negocio para acomodar una limitación técnica.
--
-- Sobre `uq_roles_name` queda una consecuencia declarada: distingue mayúsculas,
-- de modo que `Contabilidad` y `contabilidad` pueden coexistir. El índice está
-- fijado por `requirements/sp.md` §10.8 y no se reabre aquí.
CREATE UNIQUE INDEX uq_roles_code ON roles (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_roles_name ON roles (name) WHERE deleted_at IS NULL;

-- `RN-SEG-007`. Garantiza COMO MÁXIMO un rol raíz; el «exactamente uno» lo
-- aporta V7. La API siempre exige padre (`VAL-004`), así que esto es defensa en
-- profundidad y no el camino normal.
CREATE UNIQUE INDEX uq_roles_single_root
    ON roles ((parent_role_id IS NULL))
    WHERE parent_role_id IS NULL AND deleted_at IS NULL;

-- PostgreSQL no indexa las columnas de clave foránea por su cuenta, y la
-- verificación de ON DELETE RESTRICT y los filtros de `RF-SP-002` la recorren.
CREATE INDEX ix_roles_parent_role_id ON roles (parent_role_id);
