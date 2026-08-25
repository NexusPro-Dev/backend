-- =============================================================================
-- RF-SP-024 · T-04 — Estructura comercial: quién está a cargo de quién.
--
-- Es una relación PERSONA → PERSONA, distinta de la jerarquía de roles: aquella
-- acota permisos, esta dirá a quién se atribuye cada resultado. `RN-SP-020` las
-- ata —el superior debe portar el rol padre inmediato del subordinado— y esa
-- atadura es lo que le regala a esta cadena la aciclicidad de la de roles, sin
-- necesitar una regla anti-ciclos propia.
--
-- REGISTRAR LA ESTRUCTURA NO CONCEDE ALCANCE DE DATOS. Que el sistema sepa que
-- Ana tiene a Luis a cargo no le da a Ana visibilidad sobre los datos de Luis:
-- eso lo decidirá D-22, que sigue abierta.
-- =============================================================================

CREATE TABLE user_supervisors (
    id            uuid        PRIMARY KEY,
    user_id       uuid        NOT NULL,
    supervisor_id uuid        NOT NULL,
    started_at    timestamptz NOT NULL DEFAULT now(),
    ended_at      timestamptz NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_supervisors_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- `RN-SP-022`: ningún equipo se queda sin superior.
    CONSTRAINT fk_user_supervisors_supervisor
        FOREIGN KEY (supervisor_id) REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT ck_user_supervisors_no_self CHECK (user_id <> supervisor_id),

    CONSTRAINT ck_user_supervisors_periodo CHECK (ended_at IS NULL OR ended_at > started_at)
);

COMMENT ON TABLE user_supervisors IS
    'Estructura comercial con historial. La fila cerrada se conserva: dice a quién se atribuía cada resultado.';

-- `RN-SP-021`: UN SUPERIOR VIGENTE POR VENDEDOR. Es la ÚNICA unicidad parcial
-- de este plan, y por el motivo CONTRARIO al de `roles`: allí es parcial para
-- que el borrado libere el nombre; aquí lo es para que el historial no compita
-- con la asignación vigente.
--
-- El historial no se borra: determina a quién se atribuía cada resultado en cada
-- momento, y las comisiones lo necesitarán.
CREATE UNIQUE INDEX uq_user_supervisors_vigente
    ON user_supervisors (user_id) WHERE ended_at IS NULL;

-- NO se crea `ix_user_supervisors_supervisor_id`: la consulta por superior
-- —«quién está a cargo de esta persona»— es de `RF-SP-042`, y `RN-SP-022` la usa
-- desde `RF-SP-028`, `RF-SP-029` y `RF-SP-031`. El primero que la necesite la
-- declara.
