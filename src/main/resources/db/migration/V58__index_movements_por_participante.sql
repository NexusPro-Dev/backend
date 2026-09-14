-- =============================================================================
-- RF-MV-008 · T-01 — Los dos accesos por participante.
--
-- `RF-MV-008` pregunta «los movimientos en los que YO participo», y eso es
-- `client_id = ? OR seller_id = ?`.
--
-- NINGUN INDICE SIRVE A UN `OR` SOBRE DOS COLUMNAS DISTINTAS. Uno compuesto
-- sobre `(client_id, seller_id)` no responde a la segunda mitad: PostgreSQL
-- solo puede usar la primera columna como prefijo. Con DOS indices, en cambio,
-- el planificador resuelve la condicion como un `BitmapOr` de dos recorridos.
--
-- POR QUE IMPORTA Y NO ES PREMATURO: `movements` crece SIN LIMITE —una fila por
-- venta del sistema— y el sintoma de no tenerlos NO SERIA UN FALLO SINO
-- LENTITUD CRECIENTE, que es la clase de defecto que nadie descubre hasta que
-- duele. Es el mismo motivo por el que `RF-SP-025` declaro su indice de
-- trigramas sobre `users`.
--
-- LA SEGUNDA COLUMNA ES EL ORDEN, no un capricho: la consulta devuelve del mas
-- reciente al mas antiguo, y sin ella cada pagina obliga a ordenar en memoria
-- todo lo que el indice devuelve.
-- =============================================================================


-- «Lo que compre». Total: TODA venta tiene cliente (`client_id` es NOT NULL).
CREATE INDEX ix_movements_client
    ON movements (client_id, occurred_at DESC);


-- «Lo que vendi». PARCIAL, y es la unica decision de esta migracion: una venta
-- SIN vendedor —el caso normal de quien no cuelga de nadie— nunca forma parte
-- de esta respuesta, de modo que no tiene por que ocupar sitio en el indice ni
-- crecer dentro de el indefinidamente. Mismo criterio con el que
-- `ix_user_supervisors_supervisor_vigente` es parcial desde `RF-SP-028`.
CREATE INDEX ix_movements_seller
    ON movements (seller_id, occurred_at DESC)
 WHERE seller_id IS NOT NULL;


COMMENT ON INDEX ix_movements_client IS
    'RF-MV-008: la mitad «lo que compre» de los movimientos propios, ya ordenada.';
COMMENT ON INDEX ix_movements_seller IS
    'RF-MV-008: la mitad «lo que vendi». Parcial: una venta sin vendedor nunca esta en esa respuesta.';
