-- =============================================================================
-- RF-PM-002 · T-01 — Estructuras de acceso del listado del catálogo.
--
-- NO cambia columnas ni restricciones: la tabla, sus únicos y sus CHECK los
-- crea `V39__create_products.sql`. Aquí solo se añade por dónde entra el
-- listado.
--
-- POR QUÉ GIN DE TRIGRAMAS Y NO UN B-TREE. La búsqueda es por fragmento
-- —`LIKE '%termino%'`— y un B-tree ordena por prefijo: una coincidencia que
-- puede empezar en cualquier posición no acota el rango a recorrer. `V32` y
-- `V17` dejaron escrito el mismo argumento para roles y países.
--
-- EL ÚNICO `uq_products_name` NO SIRVE PARA ESTO aunque vaya sobre la misma
-- expresión: es un índice de IGUALDAD, y además es PARCIAL —solo los vivos—,
-- de modo que no cubre la búsqueda con `includeDeleted`.
--
-- EL ÍNDICE DE BÚSQUEDA NO ES PARCIAL, a propósito y al revés que el único:
-- excluir los retirados lo haría marginalmente más pequeño y dejaría sin
-- cobertura justo la consulta que sí los pide (`CA-PM-018`). La distinción la
-- hace el predicado.
--
-- `f_unaccent` existe desde `V1` y está declarada IMMUTABLE precisamente para
-- poder indexarse. Si `V1` no llegó a aplicarse, esta migración falla al crear
-- el índice — que es el momento correcto de enterarse.
-- =============================================================================

CREATE INDEX ix_products_busqueda ON products USING gin (
    f_unaccent(lower(name)) gin_trgm_ops
);

-- EL ORDEN POR OMISIÓN, Y EL QUE MÁS SE PIDE. Va con el desempate DENTRO del
-- índice y no solo en la consulta: sin `id` aquí, el motor puede usar el índice
-- para ordenar por fecha y tener que ordenar en memoria el empate — que es
-- justo donde la paginación se vuelve inestable (`CA-PM-076`).
--
-- El sentido importa: declararlo DESC deja que la página por omisión se lea del
-- índice hacia adelante. PostgreSQL también puede recorrerlo al revés para el
-- orden ascendente, de modo que un solo índice sirve a los dos sentidos.
CREATE INDEX ix_products_listado ON products (created_at DESC, id DESC);

-- NO SE INDEXAN `type` NI `status`, y queda escrito para que nadie lo añada
-- creyendo que falta: son dominios de dos valores. Un índice sobre una columna
-- con dos valores distintos no acota nada que un recorrido no acote igual, y el
-- planificador lo ignorará mientras paga su mantenimiento en cada escritura.
