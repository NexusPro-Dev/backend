-- =============================================================================
-- RF-SP-011 a RF-SP-014 · T-01 — Índices de línea de tiempo de los cuatro
-- registros de auditoría.
--
-- NUMERADA V33 Y NO V9 A V12, que es como las nombran los cuatro `plan.md`.
-- Aquellos números se reservaron el 21-08-2026, antes de que se aplicaran `V13`
-- a `V32`. Insertar hoy una `V9` deja migraciones fuera de orden y Flyway aborta
-- el arranque en toda base ya migrada. Van además EN UN SOLO ARCHIVO porque son
-- el mismo índice cuatro veces sobre cuatro tablas hermanas: separarlas en
-- cuatro migraciones consecutivas no aporta reversibilidad —ninguna se aplica
-- sin las otras— y sí cuatro cabeceras que repetirían este mismo argumento.
--
-- POR QUÉ HACEN FALTA Y POR QUÉ NO ESTABAN. Los índices que `V4` declara
-- responden preguntas que EMPIEZAN POR UN FILTRO: la línea de tiempo de un
-- registro, todo lo que hizo una persona, el enlace con una petición, la
-- investigación por origen. Ninguno responde la pregunta del listado SIN
-- FILTROS, que es «los últimos veinte eventos de todo el sistema»: un B-tree
-- sobre `(entity, entity_id, occurred_at DESC)` no sirve para ordenar por
-- `occurred_at` a secas, porque sus dos primeras columnas mandan en el orden.
-- Sin estos índices, la primera página del listado por defecto ordena la tabla
-- entera para devolver veinte filas — y estas tablas crecen sin purga.
--
-- POR QUÉ INCLUYEN `id`. Dos eventos pueden compartir `occurred_at` —dos
-- escrituras en el mismo milisegundo son perfectamente posibles— y sin desempate
-- el orden de las empatadas queda a criterio del plan de ejecución, que puede
-- cambiar entre la página 1 y la 2: filas repetidas en una y ausentes en la
-- otra. Aquí el desempate tiene además una propiedad que en `roles` no se daba:
-- `id` es un UUID v7, cuyos bits más significativos son la marca temporal
-- (Art. V.11), de modo que ordenar por `id DESC` dentro del mismo instante
-- SIGUE SIENDO ORDEN CRONOLÓGICO. Al llevarlo en el índice, el desempate no
-- añade una operación de ordenamiento.
--
-- QUÉ ÍNDICES NO SE CREAN, Y POR QUÉ IMPORTA. Sería fácil justificar uno por
-- cada filtro: módulo, acción, tipo, severidad, resultado. No se hacen. Primero
-- por CARDINALIDAD: `action` tiene dos valores y `severity` tres; un índice que
-- parte la tabla en dos mitades no acota nada y el planificador lo descarta.
-- Segundo por COSTE DE ESCRITURA: cada índice de estas tablas se paga en CADA
-- OPERACIÓN DE NEGOCIO DEL SISTEMA, porque cada una emite su evento dentro de la
-- misma transacción (Art. V.14). Un índice que no se usa no es neutro: es
-- latencia añadida a toda alta, edición y borrado de NEXUS.
-- =============================================================================

-- `RF-SP-011`
CREATE INDEX ix_audit_change_log_occurred_at
    ON audit_change_log (occurred_at DESC, id DESC);

-- `RF-SP-012`
CREATE INDEX ix_audit_deletion_log_occurred_at
    ON audit_deletion_log (occurred_at DESC, id DESC);

-- `RF-SP-012` — búsqueda por texto sobre el motivo, insensible a acentos y
-- mayúsculas. PARCIAL a propósito: las eliminaciones de asociación no llevan
-- motivo (Art. V.13), de modo que indexarlas sería indexar el vacío. Es el
-- tercer consumidor de `f_unaccent` (`V1`); quien modifique el diccionario
-- `unaccent` debe reindexar también este índice.
CREATE INDEX ix_audit_deletion_log_reason_busqueda
    ON audit_deletion_log USING gin (f_unaccent(lower(reason)) gin_trgm_ops)
    WHERE reason IS NOT NULL;

-- `RF-SP-013`
CREATE INDEX ix_audit_error_log_occurred_at
    ON audit_error_log (occurred_at DESC, id DESC);

-- `RF-SP-013` — el código de error SÍ tiene cardinalidad: es la pregunta
-- «cuántas veces falló esto», y el rango de fechas la acompaña casi siempre.
CREATE INDEX ix_audit_error_log_error_code
    ON audit_error_log (error_code, occurred_at DESC);

-- `RF-SP-014`
CREATE INDEX ix_audit_security_log_occurred_at
    ON audit_security_log (occurred_at DESC, id DESC);
