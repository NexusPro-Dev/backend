-- ---------------------------------------------------------------------------
-- V12 — la cabecera del movimiento lleva UN SUJETO y el vendedor baja a la
-- línea (RN-MV-026, RN-MV-003 enmendada, 16-09-2026).
--
-- Decisión del responsable del proyecto. `movements` es el libro de TODOS los
-- hechos económicos (mv.md §4.2: ventas hoy; depósitos, puntos, comisiones y
-- retiros después), y `client_id` nombraba un papel que solo la venta tiene:
-- quien cobra una comisión no es cliente de nadie. `user_id` es el SUJETO — a
-- nombre de quién ocurre el hecho —, y en una venta es quien compra.
--
-- El vendedor se va a `movement_details.seller_id` porque la comisión se
-- devenga POR LÍNEA (decisión del 02-09-2026) y porque cada línea puede tener
-- el suyo. Admite nulo SOLO por los tipos de movimiento que no venden nada:
-- en una venta siempre está, y lo sostiene el caso de uso, porque un CHECK no
-- consulta `movement_types`.
--
-- Tercera migración posterior a la consolidación (V1..V9), y la primera que
-- QUITA columnas: enmienda V7 en lugar de reescribirla, porque una migración
-- aplicada no se toca. EL ORDEN IMPORTA: renombrar, añadir la columna a la
-- línea, COPIAR, y solo entonces borrar la de la cabecera.
-- ---------------------------------------------------------------------------

-- 1. La cabecera: `client_id` pasa a llamarse `user_id`. Misma columna, mismas
--    filas; la FK y el índice cambian de nombre para que nadie lea «cliente».
ALTER TABLE movements RENAME COLUMN client_id TO user_id;
ALTER TABLE movements RENAME CONSTRAINT fk_movements_client TO fk_movements_user;
ALTER INDEX ix_movements_client RENAME TO ix_movements_user;

COMMENT ON COLUMN movements.user_id IS
    'RN-MV-026: el SUJETO del movimiento — a nombre de quien ocurre. En una venta, quien compra. Nunca quien lo registro desde oficina: eso va a auditoria.';
COMMENT ON INDEX ix_movements_user IS
    'RF-MV-008: la mitad «lo que compre» de los movimientos propios, ya ordenada.';

-- 2. La línea gana su vendedor.
ALTER TABLE movement_details
    ADD COLUMN seller_id uuid NULL,
    ADD CONSTRAINT fk_movement_details_seller
        FOREIGN KEY (seller_id) REFERENCES users (id) ON DELETE RESTRICT;

COMMENT ON COLUMN movement_details.seller_id IS
    'RN-MV-003: quien le vendio ESTA linea, congelado; a el se le creara la comision. NULL solo en los tipos de movimiento que no venden nada — en una VENTA siempre esta (lo sostiene el caso de uso).';

-- 3. Lo ya vendido conserva su atribución, y NINGUNA línea de venta queda sin
--    vendedor: la venta que estaba sin él —posible entre el 04-09 y el 16-09,
--    y solo de quien no colgaba de nadie— recibe al comprador, que es lo que
--    RN-MV-003 dice hoy de esa persona. No se inventa nada: es la única
--    atribución que la regla admite para ese caso.
UPDATE movement_details d
   SET seller_id = COALESCE(m.seller_id, m.user_id)
  FROM movements m
 WHERE m.id = d.movement_id;

-- 4. La cabecera pierde al vendedor, y con él su índice.
DROP INDEX IF EXISTS ix_movements_seller;
ALTER TABLE movements DROP CONSTRAINT fk_movements_seller;
ALTER TABLE movements DROP COLUMN seller_id;

-- 5. La mitad «lo que vendí» de RF-MV-008, ahora sobre la línea. Parcial por
--    lo mismo que el que sustituye: las filas sin vendedor nunca están en esa
--    respuesta. La segunda columna evita volver a la tabla para casar el
--    movimiento en el EXISTS.
CREATE INDEX ix_movement_details_seller
    ON movement_details (seller_id, movement_id) WHERE seller_id IS NOT NULL;
COMMENT ON INDEX ix_movement_details_seller IS
    'RF-MV-008: la mitad «lo que vendi». Parcial: una linea sin vendedor nunca esta en esa respuesta.';
