-- =============================================================================
-- `ix_user_brokers_busqueda` — el filtro por texto del listado de administración.
--
-- `RF-SP-057` · `T-01`. El actor que revisa un caso concreto llega con **el
-- número de cuenta que le dio el broker**, y ese es el campo que faltaba por
-- indexar: `username`, `email` y el nombre completo ya los cubre
-- `ix_users_busqueda` (`V29`).
--
-- SE NUMERÓ COMPROBANDO EL MÁXIMO APLICADO, que era `V81`.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- LAS EXPRESIONES SON LAS DEL PREDICADO, y por eso se escriben juntas.
--
-- Es la lección que `V29` dejó escrita, y se repite porque EL DEFECTO NO SE
-- MANIFIESTA COMO UN ERROR: si la expresión del índice y la de la consulta
-- divergen en un solo carácter, el índice existe, el planificador NO LO USA
-- NUNCA, y lo que se ve es una consulta lenta que nadie relaciona con esta
-- migración.
--
-- GIN DE TRIGRAMAS y no B-tree: la búsqueda es POR CONTENCIÓN —`%7012%`—, y un
-- B-tree solo sirve al prefijo. Es la misma elección de `users`, `roles`,
-- `countries` y `products`.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_user_brokers_busqueda ON user_brokers USING gin (
    f_unaccent(lower(external_id)) gin_trgm_ops
);

COMMENT ON INDEX ix_user_brokers_busqueda IS
    'Búsqueda por fragmento del número de cuenta (RF-SP-057). Las expresiones son las del predicado.';
