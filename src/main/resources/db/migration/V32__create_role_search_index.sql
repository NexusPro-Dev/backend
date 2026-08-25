-- =============================================================================
-- RF-SP-002 · T-01 — Índice de búsqueda del catálogo de roles.
--
-- NUMERADA V32 Y NO V8, que es como la nombra `tasks.md`. Aquel número se
-- reservó el 21-08-2026, cuando `RF-SP-002` iba a implementarse antes que los
-- catálogos y que los usuarios; desde entonces se aplicaron `V13` a `V31`.
-- Insertar hoy una `V8` deja una migración fuera de orden: Flyway la detecta
-- como «resolved migration not applied» y ABORTA EL ARRANQUE en toda base ya
-- migrada —incluida la de cada quien y la de la suite—, salvo activando
-- `out-of-order`, que es exactamente la puerta que no conviene abrir para
-- ahorrarse un renombrado. El número es un orden de aplicación, no un
-- identificador del requerimiento; la trazabilidad la da esta cabecera.
--
-- Esta migración NO cambia columnas ni restricciones: añade la estructura de
-- acceso que la búsqueda exige. La tabla, sus índices únicos y sus CHECK los
-- crea `V5__create_roles.sql`, y el filtro por rol padre aprovecha el
-- `ix_roles_parent_role_id` que aquella ya declara.
--
-- Las extensiones y `f_unaccent` las crea `V1__create_shared_functions.sql`, y
-- allí vive la justificación de por qué `unaccent` no es indexable directamente
-- y hay que envolverla. Si `V1` no llegó a aplicarse, esta migración falla al
-- crear el índice — que es el momento correcto de enterarse.
--
-- POR QUÉ GIN DE TRIGRAMAS Y NO UN B-TREE. Un `LIKE '%termino%'` no puede usar
-- un B-tree en ningún caso: el B-tree ordena por prefijo y una coincidencia que
-- puede empezar en cualquier posición no acota el rango a recorrer. Y el prefijo
-- no basta aquí: `LIDER_ACADEMICO` obliga a que teclear «academico» encuentre el
-- rol, y ningún prefijo lo consigue. El argumento completo está en
-- `RF-SP-002` §2; `ix_countries_busqueda` (`V17`) lo hereda igual.
--
-- EL ÍNDICE NO ES PARCIAL: no lleva `WHERE deleted_at IS NULL`. Excluir los
-- eliminados lo haría marginalmente más pequeño y dejaría sin cobertura la
-- consulta que sí los pide (`CA-SP-011`). La distinción se deja al predicado.
-- =============================================================================

CREATE INDEX ix_roles_busqueda ON roles USING gin (
    f_unaccent(lower(code)) gin_trgm_ops,
    f_unaccent(lower(name)) gin_trgm_ops
);
