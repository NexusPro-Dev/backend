-- =============================================================================
-- V85 — Lo que avisa cada broker, tal como llegó (RF-SP-078 · T-01; RN-SP-066;
-- requirements/sp.md v1.107.0 §10.25, modelo-datos.md v0.106.0; 08-10-2026).
--
-- Se reciben los avisos de IQOPTION, EXNOVA y EXOPTION sin saber todavía qué
-- mandan: se guardan enteros y NADA los interpreta. Por eso la tabla no lleva
-- updated_at (la fila no cambia), ni columnas de proceso (llegarán con
-- RF-SP-054, que es quien procesará), ni único (no se sabe qué identifica a un
-- aviso, y dos iguales son dos filas). Ningún permiso: la ruta es pública y la
-- autentica un secreto por broker que vive en el entorno, no aquí.
-- =============================================================================

CREATE TABLE broker_notifications (
    id            uuid          PRIMARY KEY,
    broker_id     uuid          NOT NULL,
    method        varchar(10)   NOT NULL,
    query_params  jsonb         NOT NULL DEFAULT '{}',
    headers       jsonb         NOT NULL DEFAULT '{}',
    body          text          NULL,
    content_type  varchar(200)  NULL,
    ip_address    varchar(45)   NULL,
    received_at   timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT ck_broker_notifications_method CHECK (method IN ('GET', 'POST')),
    -- Sin ON DELETE: un broker no se borra (RN-SP-039).
    CONSTRAINT fk_broker_notifications_broker
        FOREIGN KEY (broker_id) REFERENCES brokers (id)
);

CREATE INDEX ix_broker_notifications_broker ON broker_notifications (broker_id, received_at DESC);

COMMENT ON TABLE broker_notifications IS
    'RN-SP-066: lo que avisa cada broker, tal como llegó y sin interpretar. La fila no cambia nunca.';
COMMENT ON COLUMN broker_notifications.query_params IS
    'Los parámetros de la dirección, nombre -> lista de valores, en el orden de llegada. SIN token.';
COMMENT ON COLUMN broker_notifications.headers IS
    'Las cabeceras, nombre en minúsculas -> lista de valores. Sin authorization, proxy-authorization ni cookie.';
COMMENT ON COLUMN broker_notifications.body IS
    'El cuerpo tal cual, como texto (hasta 64 KiB). NULL si vino vacío. Un formulario no se desarma.';
