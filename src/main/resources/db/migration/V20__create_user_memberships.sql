-- =============================================================================
-- RF-SP-024 · T-03 — Membresía de cada consumidor.
--
-- `RN-SP-018` hace que el rol de consumidor y la membresía sean INSEPARABLES:
-- se conceden juntos y se sueltan juntos. El estado «consumidor sin nivel» no
-- existe, y por eso el alta escribe esta fila dentro de su misma transacción.
-- =============================================================================

CREATE TABLE user_memberships (
    user_id       uuid        NOT NULL,
    membership_id uuid        NOT NULL,
    started_at    timestamptz NOT NULL DEFAULT now(),
    ends_at       timestamptz NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    -- `RN-SP-014`: UNA MEMBRESÍA POR USUARIO, declarada en el esquema y no solo
    -- en el dominio (Art. V.6). Con `user_id` como clave primaria, «dos
    -- membresías a la vez» es imposible por construcción, y `RF-SP-032`
    -- sustituye con un UPDATE en lugar de insertar.
    CONSTRAINT pk_user_memberships PRIMARY KEY (user_id),

    CONSTRAINT fk_user_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- Obligación que el plan de `RF-SP-016` dejó declarada: una membresía no se
    -- elimina.
    CONSTRAINT fk_user_memberships_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id) ON DELETE RESTRICT,

    -- Una vigencia que termina antes de empezar no es un caso de negocio, es un
    -- dato corrupto.
    CONSTRAINT ck_user_memberships_periodo CHECK (ends_at IS NULL OR ends_at > started_at)
);

COMMENT ON TABLE user_memberships IS
    'Una fila por consumidor (RN-SP-014). La vigencia se evalúa al consultarla; nadie retira la vencida.';
COMMENT ON COLUMN user_memberships.ends_at IS
    'Nula = indefinida. El alta siempre la deja nula; acotarla es una operación aparte (RF-SP-032).';

-- LLEVA `updated_at` Y `user_roles` NO, y la diferencia es real: aquí sí hay
-- algo que se modifica, porque `RF-SP-032` cambia la membresía o su fecha de fin
-- sobre la misma fila.
--
-- NINGUNA VIGENCIA SE RETIRA SOLA. `RN-SP-014` es explícita: se evalúa al
-- consultarla y ningún proceso retira la fila vencida. El esquema no lleva nada
-- que sugiera lo contrario —ni columna de estado, ni marca de caducado— y ese
-- vacío es deliberado.
