-- =============================================================================
-- V109 — La dirección de la señal (RF-AC-067 a RF-AC-072 · T-07;
-- requirements/ac.md v0.31.0 §5.2.22, RN-AC-035 enmendada; 14-10-2026).
--
--   * signals gana direction: UP si sube, DOWN si baja.
--   * Las señales vivas que existían no tienen dirección, y el responsable del
--     proyecto eligió retirarlas: deleted_at = now(). Inventarles una sería un
--     dato falso; retirarlas sin borrarlas conserva su auditoría.
--   * ck_signals_direction: UP o DOWN, o vacía SOLO en una señal retirada. Con
--     CASE y no con OR: direction IN (...) con direction nula da NULL, y un
--     CHECK que da NULL deja pasar la fila.
--
-- Sin permisos: el catálogo sigue en 250.
-- =============================================================================

ALTER TABLE signals ADD COLUMN direction varchar(4) NULL;

UPDATE signals SET deleted_at = now() WHERE deleted_at IS NULL;

ALTER TABLE signals ADD CONSTRAINT ck_signals_direction
    CHECK (CASE WHEN direction IS NULL THEN deleted_at IS NOT NULL
                ELSE direction IN ('UP', 'DOWN') END);

COMMENT ON COLUMN signals.direction IS
    'UP si sube, DOWN si baja (RN-AC-035). Vacia solo en las senales que V109 retiro por no tenerla.';
