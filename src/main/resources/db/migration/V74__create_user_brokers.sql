-- =============================================================================
-- `user_brokers` — qué cuenta tiene cada persona en cada broker.
--
-- `RF-SP-052` · `T-02`. Nace SIN CÓDIGO QUE LA ESCRIBA, y es deliberado: quién
-- declara una cuenta (`RF-SP-053`) no está decidido. Lo que sí está decidido es
-- la forma, y la forma es lo que esta migración fija.
-- =============================================================================

CREATE TABLE user_brokers (
    id              uuid         PRIMARY KEY,

    user_id         uuid         NOT NULL,
    broker_id       uuid         NOT NULL,

    -- EL IDENTIFICADOR DE LA PERSONA EN EL BROKER: el número de cuenta. Es lo
    -- único que la persona conoce al declararla, y por eso es obligatorio.
    external_id     varchar(80)  NOT NULL,

    -- ADMITE NULO, Y SU NULO SIGNIFICA ALGO (`RN-SP-040`): «el broker todavía
    -- no lo ha confirmado». Lo rellena el webhook de `RF-SP-054`, no el alta.
    --
    -- Declararlo obligatorio obligaría a inventarse un valor al declarar la
    -- cuenta, y ese valor inventado SOBREVIVIRÍA a la confirmación — que es el
    -- defecto peor de los dos, porque no falla.
    broker_username varchar(120),

    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_user_brokers_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_brokers_broker
        FOREIGN KEY (broker_id) REFERENCES brokers (id)
);

-- =============================================================================
-- `RN-SP-038` — UNA CUENTA ES DE UNA SOLA PERSONA.
--
-- EL ÚNICO VA SOBRE `(broker_id, external_id)` Y NO SOBRE `(user_id,
-- broker_id)`, que es el que sale solo al escribir esta tabla. La diferencia es
-- el requerimiento entero:
--
--   `(user_id, broker_id)`      -> prohíbe varias cuentas de la misma persona
--                                  en el mismo broker (que es LO NORMAL en el
--                                  ramo) y permite que dos personas declaren la
--                                  misma cuenta (que es el fraude).
--
--   `(broker_id, external_id)`  -> al revés, que es lo que se quiere.
--
-- Y LO SOSTIENE EL ÍNDICE, NO UNA COMPROBACIÓN PREVIA: dos altas simultáneas de
-- la misma cuenta leen una tabla sin la fila, las dos creen que pueden, y solo
-- chocan aquí. Es la lección que `RF-SP-047 · T-12` dejó escrita el mismo día.
-- =============================================================================
ALTER TABLE user_brokers
    ADD CONSTRAINT uq_user_brokers_cuenta UNIQUE (broker_id, external_id);

-- Las cuentas de una persona, que es la lectura que `RF-SP-053` hará en cuanto
-- exista. Se declara ahora porque la clave foránea sola no la cubre: PostgreSQL
-- no indexa el lado que apunta.
CREATE INDEX ix_user_brokers_persona ON user_brokers (user_id);

COMMENT ON TABLE user_brokers IS
    'Cuenta de una persona en un broker. Sin `deleted_at`: desvincular no está decidido.';
COMMENT ON COLUMN user_brokers.broker_username IS
    'NULL = el broker aún no lo ha confirmado (RN-SP-040). Lo rellena RF-SP-054.';
