-- =============================================================================
-- RF-CM-006 — La tasa personalizada declara SU producto (`RN-CM-004`,
-- `RN-CM-006` y `RN-CM-014`, enmendadas el 11-09-2026).
--
-- Hasta hoy una personalizada NO nombraba ningun producto: quien tenia una
-- ganaba lo mismo vendiera lo que vendiera, y la resolucion la elegia mirando
-- solo persona y fecha. Desde esta migracion nombra UNO, y su excepcion alcanza
-- a ese producto y a ninguno mas.
--
-- -----------------------------------------------------------------------------
-- UNA COLUMNA PROPIA Y NO UNA TABLA DE ASOCIACION, al reves que la tasa de rol.
-- La asimetria es deliberada: la de rol es CATALOGO REUTILIZABLE —una fila que
-- rige en muchos productos, y por eso tiene `product_commission_rates`—,
-- mientras que una personalizada ya es de UNA SOLA PERSONA y no hay nada que
-- reutilizar. Una tabla intermedia solo anadiria un salto.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. LA GUARDA, Y VA PRIMERA.
--
-- `NOT NULL` sobre una tabla con filas exige un valor, y AQUI NO HAY NINGUNO
-- QUE NO SEA MENTIRA: a que producto se referia una tasa que valia para todos
-- no se deduce de la fila, ni de la persona, ni de nada.
--
-- Las tres salidas alternativas son peores, y conviene que quede escrito por
-- que se descartan:
--
--   - Rellenar con un producto cualquiera produce FILAS PLAUSIBLES Y FALSAS.
--     Es exactamente lo que `V49` evito vaciando en lugar de traducir.
--   - Retirarlas en silencio deja a alguien SIN COBRAR SU EXCEPCION hasta la
--     siguiente liquidacion, que es cuando ya no se puede arreglar.
--   - Dejar la columna nulable conserva para siempre las DOS formas, y con
--     ellas una precedencia de tres niveles que el responsable descarto.
--
-- QUE ABORTE EL ARRANQUE NO ES UN EFECTO SECUNDARIO, ES EL PUNTO. Un despliegue
-- que no puede migrar sin inventar datos debe pararse y DECIR QUE HACER. Mismo
-- criterio que `V22` con la credencial del superadministrador y `V7` con el rol
-- raiz.
-- -----------------------------------------------------------------------------
DO $guarda$
DECLARE
    vivas int;
BEGIN
    SELECT count(*) INTO vivas
      FROM user_commission_rates
     WHERE deleted_at IS NULL;

    IF vivas > 0 THEN
        RAISE EXCEPTION
            'Hay % tasa(s) personalizada(s) viva(s) y ninguna declara producto. No se puede adivinar a cual pertenecian: retirelas o asigneles un producto a mano antes de aplicar V84.', vivas;
    END IF;
END
$guarda$;


-- -----------------------------------------------------------------------------
-- 2. La columna.
--
-- `NOT NULL` Y NO UN `CHECK`: la ausencia no significa nada aqui —no hay
-- «excepcion global» que expresar desde hoy— y una columna nulable con un
-- `CHECK` que exige valor es la misma restriccion escrita dos veces.
--
-- LA CLAVE FORANEA ES SIMPLE Y NO COMPUESTA, al reves que la de
-- `product_commission_rates`. Alli es compuesta porque `role_id` viaja COPIADO
-- de la tasa y podria divergir de ella; aqui no hay nada copiado que pueda
-- mentir: la fila declara su producto y punto.
-- -----------------------------------------------------------------------------
ALTER TABLE user_commission_rates
    ADD COLUMN product_id uuid NOT NULL;

ALTER TABLE user_commission_rates
    ADD CONSTRAINT fk_user_commission_rates_product
        FOREIGN KEY (product_id) REFERENCES products (id);


-- -----------------------------------------------------------------------------
-- 3. `RN-CM-006` se rehace CON el producto dentro.
--
-- SE REHACE Y NO SE ANADE OTRO. Con dos restricciones —la vieja por persona y
-- una nueva por persona y producto— LA VIEJA SEGUIRIA PROHIBIENDO lo que esta
-- enmienda quiere permitir, y el motor rechazaria citando una regla que ya no
-- existe. Rehacerlo deja UNA SOLA definicion de «solapada».
--
-- LA REGLA SE AFLOJA A PROPOSITO: la misma persona puede tener ahora varias
-- tasas vivas a la vez mientras hablen de productos distintos. Lo que sigue
-- prohibido —y sigue en el motor porque es lo unico que dos peticiones
-- simultaneas pueden burlar— es que dos cubran el mismo dia SOBRE EL MISMO
-- PRODUCTO: con eso, `RF-CM-005` dejaria de ser determinista.
--
-- SIGUE SIENDO PARCIAL SOBRE LAS VIVAS. Sin el `WHERE`, una tasa retirada
-- seguiria bloqueando sus dias y retirar dejaria el periodo inutilizable PARA
-- SIEMPRE — y nada mas fallaria.
-- -----------------------------------------------------------------------------
ALTER TABLE user_commission_rates
    DROP CONSTRAINT uq_user_commission_rates_vigente;

ALTER TABLE user_commission_rates
    ADD CONSTRAINT uq_user_commission_rates_vigente
    EXCLUDE USING gist (
        user_id    WITH =,
        product_id WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (deleted_at IS NULL);


-- NO se crea indice propio sobre `product_id`: el `EXCLUDE` deja uno GiST que
-- ya lleva la columna, y la resolucion busca por persona, producto y fecha a la
-- vez.

COMMENT ON TABLE user_commission_rates IS
    'Excepcion por persona Y PRODUCTO. Gana sobre la del rol en ESE producto, y solo en ese (RN-CM-004, 11-09-2026).';
COMMENT ON COLUMN user_commission_rates.product_id IS
    'Sobre que producto rige la excepcion. Obligatorio: una personalizada sin producto ya no se puede expresar (RN-CM-014).';
