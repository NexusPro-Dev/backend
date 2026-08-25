-- =============================================================================
-- RF-SP-021 · T-01 — Índice de búsqueda del catálogo de países.
--
-- Esta migración NO cambia columnas ni restricciones: añade la estructura de
-- acceso que la búsqueda exige. La tabla, sus dos índices únicos y sus dos CHECK
-- los crea `V16__create_countries.sql`.
--
-- Las extensiones y `f_unaccent` las crea `V1__create_shared_functions.sql`, y
-- allí vive la justificación de por qué `unaccent` no es indexable directamente
-- y hay que envolverla. Este es su quinto consumidor.
--
-- POR QUÉ GIN DE TRIGRAMAS Y NO UN B-TREE, en una línea: un `LIKE '%termino%'`
-- no puede usar un B-tree en ningún caso, porque el B-tree ordena por prefijo y
-- una coincidencia que puede empezar en cualquier posición no acota el rango. El
-- argumento completo está en `RF-SP-002` §2 y aquí solo se hereda; lo mismo vale
-- para el índice multicolumna, que el planificador combina con BitmapOr sobre
-- las dos ramas del OR.
--
-- POR QUÉ AQUÍ SÍ Y EN `permissions` Y `memberships` NO. Aquellos rechazaron el
-- índice con un argumento correcto: unas pocas filas se recorren más rápido de
-- lo que cuesta consultar un índice. La diferencia no es el tamaño de hoy sino
-- QUIÉN DECIDE EL TAMAÑO DE MAÑANA: `permissions` solo crece por migración y
-- `memberships` solo cuando el negocio define un nivel, mientras que `countries`
-- crece POR API Y A DEMANDA, y nada impone su techo.
--
-- Lo que conviene dejar escrito para que no se lea como contradicción: con pocas
-- decenas de filas el planificador probablemente prefiera el recorrido
-- secuencial, y ESO NO ES UN DEFECTO DEL ÍNDICE. Existe para el caso en que el
-- catálogo se pueble de verdad, y su coste de escritura es irrelevante porque
-- las escrituras son altas manuales.
-- =============================================================================

CREATE INDEX ix_countries_busqueda ON countries USING gin (
    f_unaccent(lower(code)) gin_trgm_ops,
    f_unaccent(lower(name)) gin_trgm_ops
);
