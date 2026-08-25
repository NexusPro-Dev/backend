-- =============================================================================
-- RF-SP-024 · T-02 — Roles que porta cada persona.
--
-- LA CREA EL ALTA Y NO `RF-SP-030`, corregido al aprobarse este plan: el alta ya
-- escribe asignaciones, de modo que la tabla tiene que existir aquí.
-- =============================================================================

CREATE TABLE user_roles (
    user_id    uuid        NOT NULL,
    role_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    -- La unicidad del par es TODA la información que la fila contiene, de modo
    -- que no lleva clave sustituta. Excepción declarada al Art. V.11, igual que
    -- en `role_permissions`.
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),

    -- Un usuario no se elimina físicamente; si alguien lo intentara, esto lo
    -- impide.
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- `RN-SEG-008`: no se elimina un rol que alguien porta. `RF-SP-009` lo
    -- comprueba antes y da un mensaje; esta restricción es la red debajo.
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

COMMENT ON TABLE user_roles IS
    'Asignación persona-rol. Sin updated_at: una asignación no se modifica, se crea y se borra.';

-- NO lleva `updated_at`, y no es un olvido del Art. V.7: no hay ninguna columna
-- que pudiera cambiar, de modo que una marca de última modificación sería
-- siempre igual a la de creación.
--
-- NO se crea `ix_user_roles_role_id`: lo declara `RF-SP-030`, que es quien
-- estrena el filtro por rol.
