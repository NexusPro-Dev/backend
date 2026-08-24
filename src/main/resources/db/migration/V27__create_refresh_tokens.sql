-- =============================================================================
-- RF-SP-034 · T-02 — Sesiones renovables.
--
-- NO ES UNA TABLA DE NEGOCIO: no lleva `deleted_at` ni `updated_at`. Una sesión
-- no se edita ni se borra lógicamente — se REVOCA, y `revoked_at` es su marca.
-- La purga de `security.md` §5.5 la elimina físicamente.
--
-- Del servidor solo se guarda el HASH del token, nunca su valor: quien lea la
-- tabla no puede usar lo que ve (`security.md` §5.2).
-- =============================================================================

CREATE TABLE refresh_tokens (
    id                uuid         PRIMARY KEY,
    user_id           uuid         NOT NULL,

    -- Único acceso a la fila. El token se localiza por su hash, nunca por su
    -- valor, y por eso el índice único es además la vía de consulta.
    token_hash        varchar(255) NOT NULL,

    -- `family_id` y `family_started_at` NO SON REDUNDANTES.
    --
    -- El primero agrupa la cadena de rotación para poder revocarla ENTERA
    -- cuando se detecta una reutilización.
    --
    -- El segundo mide la DURACIÓN MÁXIMA DE SESIÓN desde el inicio de sesión, no
    -- desde el último refresco, y es lo único que impide que una sesión rotada
    -- con disciplina no caduque nunca. Guardar solo el primero obligaría a
    -- recorrer la cadena hasta su origen en cada refresco.
    family_id         uuid         NOT NULL,
    family_started_at timestamptz  NOT NULL,

    expires_at        timestamptz  NOT NULL,
    revoked_at        timestamptz  NULL,
    revoked_reason    varchar(20)  NULL,

    -- Conserva el vínculo con el token que sustituyó al revocado (`CA-SP-303`).
    replaced_by_id    uuid         NULL,

    ip_address        inet         NULL,
    user_agent        text         NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE RESTRICT,

    -- EL MOTIVO ES OBLIGATORIO EN TODA FILA REVOCADA, y lo es en los dos
    -- sentidos: ni revocación sin motivo, ni motivo sin revocación.
    --
    -- Es el dato del que depende que `RF-SP-035` distinga un ROBO de un cierre
    -- de sesión. Sin esta restricción, una revocación sin motivo pasaría
    -- inadvertida hasta producir una alarma de robo falsa.
    CONSTRAINT ck_refresh_tokens_revocacion
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),

    -- Dominio cerrado. `ROTACION` es la revocación normal de un refresco y es la
    -- ÚNICA que, al reutilizarse, significa robo: las demás son revocaciones
    -- deliberadas cuyo token ya no debía usarse.
    CONSTRAINT ck_refresh_tokens_motivo CHECK (
        revoked_reason IS NULL OR revoked_reason IN (
            'ROTACION', 'CIERRE', 'ACCESO_RETIRADO',
            'CAMBIO_CONTRASENA', 'SESION_AGOTADA', 'REUTILIZACION'
        )
    ),

    CONSTRAINT ck_refresh_tokens_periodo CHECK (expires_at > created_at)
);

COMMENT ON TABLE refresh_tokens IS
    'Sesiones renovables. Solo el hash del token; el valor no se guarda nunca.';
COMMENT ON COLUMN refresh_tokens.revoked_reason IS
    'ROTACION es la única cuya reutilización significa robo. Las demás son revocaciones deliberadas.';

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);

-- Parcial: la revocación en cascada y el cierre de todas las sesiones solo
-- miran las vigentes, y son la inmensa mayoría de los accesos por usuario.
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- Total y no parcial: al detectar una reutilización hay que revocar la familia
-- entera, incluidas las filas ya revocadas por rotación.
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
