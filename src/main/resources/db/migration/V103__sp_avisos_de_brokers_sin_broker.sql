-- =============================================================================
-- V103 — Los avisos de un broker que no se reconoce se guardan sin broker
-- (RF-SP-078 · T-20; requirements/sp.md v1.123.0, RN-SP-076; plan.md §16;
-- 13-10-2026).
--
-- broker_id NULL: el aviso trajo el secreto correcto y un advertiser que no es
-- el de ningún broker activo, o ninguno. Se guarda igual y no se interpreta;
-- al arrancar, si su advertiser ya es de un broker, se le asocia y se
-- interpreta (BrokerNotificationClassifier). La clave foránea sigue.
--
-- advertiser: el que trajo el aviso, recortado, de 1 a 100 caracteres; NULL si
-- faltaba, venía repetido o era más largo. Lo llevan todos, también los
-- reconocidos. Se rellena desde query_params, como V98 con event_id: todos los
-- avisos reales hasta hoy llegan por GET.
--
-- ix_broker_notifications_sin_broker: la pregunta del arranque, «qué avisos
-- sin broker son ya de alguien», lo más viejo primero.
--
-- SIN PERMISOS: el catálogo no se mueve.
-- =============================================================================

ALTER TABLE broker_notifications
    ALTER COLUMN broker_id DROP NOT NULL,
    ADD COLUMN advertiser varchar(100) NULL;

COMMENT ON COLUMN broker_notifications.broker_id IS
    'RN-SP-076: NULL = el advertiser del aviso no era el de ningún broker activo. Se pone una sola vez, al reconocerlo.';
COMMENT ON COLUMN broker_notifications.advertiser IS
    'RN-SP-076: el advertiser que trajo el aviso, recortado. NULL si faltaba, venía repetido o pasaba de 100.';

UPDATE broker_notifications
   SET advertiser = btrim(query_params -> 'advertiser' ->> 0)
 WHERE jsonb_typeof(query_params -> 'advertiser') = 'array'
   AND jsonb_array_length(query_params -> 'advertiser') = 1
   AND length(btrim(query_params -> 'advertiser' ->> 0)) BETWEEN 1 AND 100;

CREATE INDEX ix_broker_notifications_sin_broker
    ON broker_notifications (lower(advertiser), received_at, id)
 WHERE broker_id IS NULL;
