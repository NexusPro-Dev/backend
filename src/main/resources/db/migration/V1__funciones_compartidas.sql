-- =============================================================================
-- V1 — Extensiones y funciones que todo el esquema comparte.
--
-- ESQUEMA CONSOLIDADO EL 15-09-2026. Las noventa y cuatro migraciones que
-- construyeron este esquema entre el 20-08-2026 y el 15-09-2026 se reescriben
-- en nueve, con cada tabla en su estado final, por decisión del responsable
-- del proyecto: el sistema está en desarrollo, ninguna base tiene datos que
-- conservar, y una migración por cada paso del camino ya no le dice nada a
-- quien levanta una base nueva. La historia de cómo se llegó a cada decisión
-- sigue en `docs/` (specs, `modelo-datos.md`, controles de cambios), y la tabla
-- «qué migración vieja creó qué» está en `modelo-datos.md`. Los identificadores
-- literales de las semillas NO cambian.
--
-- Reparto: V1 funciones · V2 auditoría · V3 catálogos de SP · V4 seguridad de
-- SP · V5 productos (PM) · V6 comisiones (CM) · V7 movimientos (MV) · V8
-- semilla de permisos y roles · V9 semillas de catálogos y superadministrador.
-- =============================================================================

-- `unaccent` y `pg_trgm` sostienen las búsquedas por fragmento sin distinguir
-- acentos ni mayúsculas; `btree_gist` permite las restricciones EXCLUDE que
-- combinan igualdad con rangos (vigencias de tasas y membresías).
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- `unaccent()` no es IMMUTABLE porque depende de un diccionario, y un índice
-- exige una función inmutable. Este envoltorio fija el diccionario y declara la
-- inmutabilidad: es lo que hace indexables `f_unaccent(lower(...))` en toda
-- columna con unicidad o búsqueda insensible a acentos.
CREATE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

COMMENT ON FUNCTION f_unaccent(text) IS
    'Envoltorio IMMUTABLE de unaccent, indexable. RF-SP-010. '
    'Tocar el diccionario unaccent obliga a REINDEX de sus consumidores.';
