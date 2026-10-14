-- =============================================================================
-- V110 — La señal se ve el día en que se crea (RF-AC-067 a RF-AC-072 · T-10;
-- requirements/ac.md v0.32.0 §5.2.23, RN-AC-035 enmendada; 14-10-2026).
--
--   * visible_until deja de ser «la próxima vez que llega signal_time» y pasa a
--     ser la medianoche de Bogotá que cierra el día de created_at, aunque la
--     hora de la señal ya haya pasado.
--   * Se recalcula en todas, retiradas o no: un solo significado en la columna.
--     ck_signals_visible_until (visible_until > created_at) sigue valiendo: la
--     medianoche siguiente siempre es posterior.
--
-- Sin permisos: el catálogo sigue en 250.
-- =============================================================================

UPDATE signals
   SET visible_until =
       (date_trunc('day', created_at AT TIME ZONE 'America/Bogota') + interval '1 day')
           AT TIME ZONE 'America/Bogota';
