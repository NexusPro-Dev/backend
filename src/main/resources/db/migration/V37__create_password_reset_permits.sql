-- =============================================================================
-- RF-SP-040 · T-01 — Permisos temporales de recuperación de contraseña.
--
-- NO ES UNA TABLA DE NEGOCIO: no lleva `updated_at` ni `deleted_at`. Es un
-- registro de permisos de un solo uso, y las dos formas de dejar de servir
-- —consumido y sustituido— son marcas propias.
--
-- Del permiso solo se guarda su HASH, nunca su valor: quien lea la tabla no
-- puede tomar la cuenta de nadie. Mismo criterio que `refresh_tokens`.
--
-- `plan.md` la numeraba `V29`, número que quedó tomado por
-- `V29__create_user_query_indexes.sql`: se reservó antes de que se aplicaran
-- `V13` a `V36`. Es la misma corrección que ya se hizo con `V32` y `V33`.
-- =============================================================================

CREATE TABLE password_reset_permits (
    id            uuid         PRIMARY KEY,
    user_id       uuid         NOT NULL,

    -- Único acceso a la fila: el permiso se localiza por su hash.
    permit_hash   varchar(255) NOT NULL,

    expires_at    timestamptz  NOT NULL,

    -- DOS COLUMNAS Y NO UN ESTADO, y la diferencia es lo que la auditoría
    -- necesita: `consumed_at` dice que alguien COMPLETÓ el flujo, y
    -- `superseded_at` que PIDIÓ OTRO. Colapsarlas en una sola columna haría
    -- indistinguibles dos cosas que se investigan de forma distinta.
    consumed_at   timestamptz  NULL,
    superseded_at timestamptz  NULL,

    -- Para que una ráfaga de solicitudes sea investigable por origen.
    requested_ip  inet         NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_password_reset_permits_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    -- Un permiso no puede haberse usado Y sustituido: son excluyentes.
    CONSTRAINT ck_password_reset_permits_final
        CHECK (consumed_at IS NULL OR superseded_at IS NULL),

    CONSTRAINT ck_password_reset_permits_periodo
        CHECK (expires_at > created_at)
);

COMMENT ON TABLE password_reset_permits IS
    'Permisos de un solo uso para recuperar la contraseña. Solo el hash; el valor no se guarda nunca.';
COMMENT ON COLUMN password_reset_permits.superseded_at IS
    'Se sustituyó porque la persona pidió otro. Distinto de consumed_at, que dice que completó el flujo.';

CREATE UNIQUE INDEX uq_password_reset_permits_hash
    ON password_reset_permits (permit_hash);

-- UN SOLO PERMISO VIVO POR PERSONA, declarado en el esquema y no solo en el
-- caso de uso.
--
-- `FA-002` exige que emitir uno invalide el anterior. Escrito únicamente en el
-- servicio, dos solicitudes concurrentes dejarían dos permisos vivos y con
-- ellos DOS VÍAS DE ENTRADA ABIERTAS a la misma cuenta. El índice lo hace
-- imposible: la segunda transacción choca en lugar de duplicar la puerta.
CREATE UNIQUE INDEX uq_password_reset_permits_vigente
    ON password_reset_permits (user_id)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
