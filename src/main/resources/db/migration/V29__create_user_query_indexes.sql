-- =============================================================================
-- RF-SP-025 · T-01 — Accesos del listado de personas.
--
-- NUMERACIÓN: el plan reservaba `V23`. Ese número ya no sirve —`V26` a `V28`
-- están aplicadas y Flyway rechaza un número inferior salvo con `outOfOrder`,
-- que no se habilita—, de modo que se toma el siguiente libre. Es la misma
-- decisión que `V28` declara, y la reserva por requerimiento queda muerta.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Búsqueda por fragmento (`CA-SP-344`)
--
-- GIN de trigramas y no B-tree: la búsqueda es por CONTENCIÓN —`%perez%`— y un
-- B-tree solo sirve prefijos. Sin este índice, cada búsqueda recorre `users`
-- entera, y `users` sí crece sin límite (`RF-SP-025` §10).
--
-- LAS EXPRESIONES SON LAS MISMAS que el predicado de la consulta, carácter por
-- carácter. Si divergieran, el índice existiría y el planificador no lo usaría
-- nunca: el defecto no se manifestaría como un error sino como una consulta
-- lenta que nadie relaciona con esta migración.
--
-- El nombre completo se indexa CONCATENADO porque así se busca: quien escribe
-- «juan perez» espera encontrarlo, y dos índices separados no sirven a un
-- predicado que compara la concatenación.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_users_busqueda ON users USING gin (
    f_unaccent(lower(username)) gin_trgm_ops,
    f_unaccent(lower(email)) gin_trgm_ops,
    f_unaccent(lower(first_name || ' ' || last_name)) gin_trgm_ops
);

COMMENT ON INDEX ix_users_busqueda IS
    'Búsqueda por fragmento del listado de personas (RF-SP-025). Las expresiones son las del predicado.';


-- -----------------------------------------------------------------------------
-- 2. Filtro por membresía
--
-- `pk_user_memberships` va sobre `user_id`, que es la dirección contraria: sirve
-- «qué membresía tiene esta persona» y no «quiénes tienen esta membresía», que
-- es lo que el filtro del listado pregunta.
--
-- Total y no parcial, al revés que el del equipo comercial: aquí la vigencia se
-- evalúa al consultar y no se retira la vencida, de modo que la fila no vigente
-- sigue siendo una fila que el filtro tiene que descartar — y descartarla desde
-- el índice es más barato que recorrer la tabla para hacerlo.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_user_memberships_membership_id
    ON user_memberships (membership_id);

COMMENT ON INDEX ix_user_memberships_membership_id IS
    'Portadores de una membresía. La clave primaria no sirve esta dirección (RF-SP-025 §4).';
