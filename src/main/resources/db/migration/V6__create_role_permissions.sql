-- =============================================================================
-- RF-SP-001 · T-03 — Asociación rol–permiso.
--
-- Campos de `requirements/sp.md` §10.3.
-- =============================================================================

CREATE TABLE role_permissions (
    role_id       uuid        NOT NULL,
    permission_id uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),

    -- Clave primaria COMPUESTA, excepción declarada al Art. V.11: la unicidad
    -- del par es la restricción que importa y una clave sustituta añadiría una
    -- columna sin significado.
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),

    -- El borrado del rol es lógico (`RF-SP-009`), de modo que no hay cascada
    -- que declarar; una eliminación física accidental debe fallar.
    CONSTRAINT fk_role_permissions_roles
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT,

    -- El catálogo solo se modifica por migración (`RN-SP-004`), y quien lo
    -- modifique debe encontrarse con las asociaciones vigentes.
    CONSTRAINT fk_role_permissions_permissions
        FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE RESTRICT
);

COMMENT ON TABLE role_permissions IS
    'Permisos que declara cada rol. Sin updated_at ni deleted_at: no se edita y su retiro es físico (RN-SP-005).';

-- La clave primaria compuesta solo sirve consultas que empiezan por `role_id`.
-- La pregunta inversa —«qué roles declaran este permiso»— la necesita
-- `RN-SEG-005` en `RF-SP-006`.
CREATE INDEX ix_role_permissions_permission_id ON role_permissions (permission_id);
