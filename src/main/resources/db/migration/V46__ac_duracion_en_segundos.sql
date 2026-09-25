-- =============================================================================
-- V46 — La duración de la lección pasa a segundos (RN-AC-017 reescrita,
-- ac.md §5.2.10, 25-09-2026).
--
-- Por decisión del responsable del proyecto: un video de 12 min 34 s dura 754,
-- no 12 ni 13, y las sumas del módulo y del curso cuadran exactas. Las sumas no
-- se guardan (se calculan en cada lectura), de modo que esta columna es lo
-- único que cambia en el esquema.
--
-- SE RENOMBRA Y SE CONVIERTE en la misma migración: renombrar sin multiplicar
-- dejaría las lecciones existentes durando sesenta veces menos, y en silencio.
-- `ck_lessons_duration` (> 0) sigue siendo cierta con cualquier múltiplo.
--
-- Sobre V24, que no se reescribe (modelo-datos.md §5.4).
-- =============================================================================

ALTER TABLE lessons RENAME COLUMN duration_minutes TO duration_seconds;

UPDATE lessons SET duration_seconds = duration_seconds * 60;
