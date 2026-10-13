-- =============================================================================
-- V102 — Las clases en vivo que se repiten (RF-AC-055 · T-01; requirements/ac.md
-- v0.25.0 §5.2.16 y §8.10, RN-AC-031; 13-10-2026).
--
-- Una clase puede repetirse cada día, cada semana —el día de la semana de su
-- primera fecha— o cada mes —el día del mes de su primera fecha—, a la misma
-- hora, hasta repeat_until incluido. Es UNA clase con UNA reunión recurrente de
-- Zoom: starts_at y ends_at siguen siendo los de la primera fecha.
--
-- series_ends_at es el fin de la ÚLTIMA fecha: con él se calcula si terminó, y
-- el listado y la vitrina filtran por él. Sin repetición es igual a ends_at.
-- =============================================================================

ALTER TABLE live_sessions
    ADD COLUMN recurrence     varchar(10) NULL,
    ADD COLUMN repeat_until   date        NULL,
    ADD COLUMN series_ends_at timestamptz NULL;

UPDATE live_sessions SET series_ends_at = ends_at;

ALTER TABLE live_sessions
    ALTER COLUMN series_ends_at SET NOT NULL,
    ADD CONSTRAINT ck_live_sessions_recurrence
        CHECK (recurrence IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    ADD CONSTRAINT ck_live_sessions_repeat_until
        CHECK ((recurrence IS NULL) = (repeat_until IS NULL)),
    ADD CONSTRAINT ck_live_sessions_series_ends_at
        CHECK ((recurrence IS NULL AND series_ends_at = ends_at)
               OR (recurrence IS NOT NULL AND series_ends_at > ends_at));

CREATE INDEX ix_live_sessions_series_ends_at ON live_sessions (series_ends_at);

COMMENT ON COLUMN live_sessions.recurrence IS
    'RN-AC-031: DAILY, WEEKLY o MONTHLY; nula, una sola fecha. Una reunion recurrente de Zoom con hora fija.';
COMMENT ON COLUMN live_sessions.repeat_until IS
    'RN-AC-031: el ultimo dia de la serie, incluido, en America/Bogota. De 2 a 60 fechas.';
COMMENT ON COLUMN live_sessions.series_ends_at IS
    'RN-AC-031: el fin de la ultima fecha; igual a ends_at sin repeticion. Terminada se calcula con el.';
