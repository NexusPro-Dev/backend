-- =============================================================================
-- V111 — El modelo de cada broker (RF-SP-052; requirements/sp.md v1.126.0;
-- 15-10-2026).
--
-- model: texto libre, decisión del responsable del proyecto del 15-10-2026.
-- Lo publica el catálogo junto al enlace de registro (url). NULL = todavía no
-- se cargó: no se inventa ningún valor.
--
-- SIN AUDITORÍA, como el resto del catálogo, que se puebla por migración
-- (RN-SP-039).
-- =============================================================================

ALTER TABLE brokers ADD COLUMN model varchar(120) NULL;

COMMENT ON COLUMN brokers.model IS
    'Modelo del broker, texto libre (RF-SP-052). NULL = no cargado. Lo publica el catálogo.';
