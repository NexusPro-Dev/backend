-- =============================================================================
-- Issue #23 — `request_log`: qué se le pidió al sistema y qué respondió.
--
-- POR QUÉ ESTA TABLA NO EXISTÍA Y HACÍA FALTA. El Art. XV.2 la exige desde el
-- primer día, y SIETE PUNTOS DEL CÓDIGO ya contaban con ella para decidir NO
-- auditar: el manejador global no registra los `404`, los `400` de formato ni
-- las peticiones mal dirigidas «porque `request_log` ya lo cubre», y el
-- `X-Correlation-Id` que se devuelve en cada respuesta se documenta como la
-- forma de localizar la petición «en `request_log`». Sin la tabla, todo eso era
-- una promesa: un barrido de rutas, un `404` o un `400` no dejaban rastro en
-- ninguna parte, y el identificador de correlación apuntaba a un sitio vacío.
--
-- QUÉ RESPONDE, Y EN QUÉ SE DISTINGUE DE LA AUDITORÍA (Art. XV.3). Esta tabla
-- responde «qué se le pidió al sistema y qué respondió»; los cuatro registros de
-- auditoría responden «qué cambió en el negocio, quién lo cambió y por qué».
-- Se correlacionan por `correlation_id`, y se separan porque se consultan por
-- motivos distintos, se retienen por plazos distintos (Art. XV.8) y crecen a
-- ritmos muy distintos.
--
-- LO QUE NO SE GUARDA, Y ES TAN DECISIÓN COMO LO QUE SÍ:
--   * El CUERPO de la petición. Ahí viajan contraseñas, y el Art. VI.5 prohíbe
--     que un registro las contenga. Ningún saneador es de fiar sobre un cuerpo
--     arbitrario: la única forma segura de no registrar un secreto es no
--     registrar el cuerpo.
--   * Las CABECERAS, y `Authorization` en particular, por lo mismo.
--   * El `query_string` SÍ se guarda —el Art. XV.2 pide «parámetros»— y por eso
--     ningún endpoint del sistema admite secretos por ahí: el motivo de una
--     eliminación viaja en el cuerpo justamente para no acabar en este registro
--     ni en el de un proxy.
--
-- POR QUÉ `status` ES NULABLE. Si el contenedor aborta la petición antes de
-- producir respuesta —una conexión cortada a media escritura—, no hay código que
-- registrar. Un cero fingido diría que el sistema respondió cero, que no existe;
-- el nulo dice lo que ocurrió: no hubo respuesta.
--
-- CRECIMIENTO. Es la tabla que más crece del sistema —una fila por petición— y
-- NO tiene purga todavía: la retención concreta es la decisión **D-10**, con su
-- propio issue. Los índices se dejan al mínimo por el mismo motivo que en los
-- registros de auditoría: cada uno se paga en cada petición.
-- =============================================================================

CREATE TABLE request_log (
    id             uuid        PRIMARY KEY,
    occurred_at    timestamptz NOT NULL,
    correlation_id uuid        NOT NULL,

    -- Nulo significa ANÓNIMO, no «se perdió el dato» (Art. XV.2): las rutas
    -- públicas —inicio de sesión, refresco, cierre— se llaman sin actor.
    actor_id       uuid        NULL,

    method         varchar(10)  NOT NULL,
    path           varchar(2048) NOT NULL,
    query_string   text         NULL,
    status         smallint     NULL,
    duration_ms    integer      NOT NULL,
    ip_address     inet         NULL,
    user_agent     text         NULL,

    CONSTRAINT ck_request_log_status
        CHECK (status IS NULL OR (status BETWEEN 100 AND 599)),

    -- Una duración negativa solo puede venir de un reloj que retrocedió o de un
    -- cálculo mal hecho; en ambos casos es un defecto y no un dato.
    CONSTRAINT ck_request_log_duration CHECK (duration_ms >= 0)
);

COMMENT ON TABLE request_log IS
    'Qué se le pidió al sistema y qué respondió (Art. XV.2). Append-only. Sin cuerpo ni cabeceras.';
COMMENT ON COLUMN request_log.actor_id IS
    'Nulo = anónimo. Sin clave foránea a users: la fila debe sobrevivir a la eliminación de la persona.';
COMMENT ON COLUMN request_log.status IS
    'Nulo cuando la petición se abortó sin respuesta. Un cero fingido diría que el sistema respondió cero.';
COMMENT ON COLUMN request_log.duration_ms IS
    'Lo que permite medir los umbrales p95 del Art. XV.9, que hasta ahora eran inverificables.';

-- La línea de tiempo: «las últimas peticiones», que es la consulta por defecto.
-- Con `id` como desempate por el mismo motivo que en los registros de auditoría:
-- dos peticiones pueden compartir instante, y al ser UUID v7 el desempate sigue
-- siendo cronológico.
CREATE INDEX ix_request_log_occurred_at ON request_log (occurred_at DESC, id DESC);

-- El enlace con los cuatro registros de auditoría (Art. XV.3). Es la consulta
-- que responde «qué más pasó en la petición que produjo este evento».
CREATE INDEX ix_request_log_correlation_id ON request_log (correlation_id);

-- «Todo lo que pidió esta persona», que es la investigación más frecuente.
CREATE INDEX ix_request_log_actor ON request_log (actor_id, occurred_at DESC);
