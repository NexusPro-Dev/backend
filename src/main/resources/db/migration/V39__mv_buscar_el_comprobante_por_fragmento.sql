-- =============================================================================
-- V39 — El comprobante se busca por FRAGMENTO (RN-MV-037, requirements/mv.md
-- §5, 24-09-2026).
--
-- El filtro `code` de las cuatro consultas de movimientos deja de exigir el
-- comprobante entero y pasa a CONTENER lo que se escriba, sin distinguir
-- mayúsculas. Esta migración no cambia ningún dato ni ninguna columna: añade el
-- único índice que hace que esa búsqueda no recorra la tabla entera.
--
-- POR QUÉ NO BASTA `uq_movements_code`. Aquel es un B-tree, y un B-tree solo
-- responde por el PRINCIPIO de la cadena: sirve para `VTA-A1%` y no para
-- `%A1B2%`, que es justo lo que se pidió — «por si solo me sé una parte», y la
-- parte que alguien recuerda suele ser el final. Sin este índice la consulta
-- pasa a recorrer `movements` entera en cada búsqueda, y el coste crece con el
-- libro de ventas.
--
-- MISMO PATRÓN QUE `ix_users_busqueda` (V4, RF-SP-025), y a propósito: allí se
-- resolvió la misma pregunta —encontrar por un trozo— con `pg_trgm`, y dos
-- soluciones distintas para el mismo problema obligarían a mantener dos.
-- `pg_trgm` la instala V1.
--
-- LA EXPRESIÓN DEL ÍNDICE ES LA DEL PREDICADO, y tiene que seguir siéndolo: el
-- índice está sobre `lower(code)`, y la consulta compara `lower(m.code)` contra
-- el término ya en minúsculas. Si alguien quita el `lower` de un lado, el índice
-- deja de usarse SIN QUE NADA FALLE — la respuesta sigue siendo correcta y solo
-- se vuelve lenta, que es la clase de regresión que no se ve hasta que duele.
--
-- SIN `f_unaccent`, al revés que en `users`. Un comprobante es `VTA-` y ocho
-- caracteres de `[A-Z0-9]`: no lleva acentos, y normalizar lo que no puede
-- llevarlos solo añade una función al índice y a cada consulta.
-- =============================================================================

CREATE INDEX ix_movements_codigo_busqueda
    ON movements USING gin (lower(code) gin_trgm_ops);

COMMENT ON INDEX ix_movements_codigo_busqueda IS
    'Busqueda por fragmento del comprobante (RN-MV-037): RF-MV-006, RF-MV-008, RF-MV-015 y RF-MV-017. La expresion es la del predicado; uq_movements_code no responde por un fragmento del medio.';
