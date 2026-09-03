-- =============================================================================
-- RF-CM-001 a RF-CM-008 — `CM` SE REHACE ENTERO (requirements/cm.md v0.4.0).
--
-- Donde `V44` puso UNA tabla que lo decia todo con la ausencia, aqui quedan
-- TRES que dicen cada una una cosa:
--
--   commission_rates          el CATALOGO por rol. Sin vigencia, sin producto
--                             y sin persona.
--   user_commission_rates     la EXCEPCION por persona. Con vigencia, una sola
--                             viva, y sin rol.
--   product_commission_rates  la ASOCIACION, que es LO UNICO que pone una tasa
--                             en vigor.
--
-- EL SIGNIFICADO DE LA AUSENCIA SE INVIERTE, y es lo que mas facil se lee mal
-- (`RN-CM-012`). En `V44`, una tarifa sin producto regia para TODO EL CATALOGO;
-- aqui, una tasa sin asociar NO PAGA NADA A NADIE. La tarifa por omision del
-- rol deja de existir. Una tasa creada y no asociada PARECE CONFIGURADA y no
-- rige — no falla, se descubre liquidando.
--
-- Y SE PIERDE EL HISTORIAL DE LAS TASAS DE ROL. Sin vigencia no hay dos filas
-- contando cada una su parte: hay una que ahora dice otra cosa. Corregir un
-- porcentaje REESCRIBE LO QUE RIGIO SIEMPRE. Se acepta a conciencia
-- (`cm.md` §8, v0.4.0), y con ello `RN-CM-008` deja de ser una condicion
-- prudente y pasa a ser LA UNICA DEFENSA DEL PASADO: quien liquide debe copiar
-- el porcentaje que aplico. Como esa liquidacion no existe todavia, HOY NO HAY
-- NINGUNA.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Las filas de `V44` NO SE PUEDEN TRADUCIR, y por eso se borran.
--
-- No es pereza ni prisa: NINGUNA de las cuatro formas del modelo anterior tiene
-- equivalente en el nuevo. Una tarifa de persona no es una tasa de rol —perdio
-- el rol y gano una tabla propia—; una de producto necesitaria una asociacion
-- que nadie declaro; y una por omision regia sobre todo el catalogo, que es
-- justo lo que `RN-CM-012` deja de permitir.
--
-- Conservarlas dejandolas caer a «tasa de rol» las convertiria en filas
-- PLAUSIBLES Y FALSAS: seguirian ahi, con su porcentaje, sin asociacion y sin
-- que nada dijera que significan otra cosa que el dia que se escribieron.
-- Se borran para que la perdida sea visible en vez de silenciosa.
--
-- El sistema no esta en produccion y esta tabla solo tiene datos de desarrollo
-- y de prueba.
-- -----------------------------------------------------------------------------
DELETE FROM commission_rates;


-- -----------------------------------------------------------------------------
-- 2. `commission_rates` se queda en el hueso: un rol y un porcentaje.
--
-- Se sueltan primero las restricciones que dependen de las columnas que se van.
-- `ex_commission_rates_sin_solape` desaparece de esta tabla y REAPARECE ABAJO
-- sobre `user_commission_rates`: es la misma regla —ningun dia cubierto dos
-- veces— aplicada a lo unico que conserva vigencia.
-- -----------------------------------------------------------------------------
ALTER TABLE commission_rates
    DROP CONSTRAINT ex_commission_rates_sin_solape,
    DROP CONSTRAINT ck_commission_rates_vigencia,
    DROP CONSTRAINT fk_commission_rates_product,
    DROP CONSTRAINT fk_commission_rates_user;

ALTER TABLE commission_rates
    DROP COLUMN product_id,
    DROP COLUMN user_id,
    DROP COLUMN valid_from,
    DROP COLUMN valid_to;

-- REDUNDANTE CON LA CLAVE PRIMARIA, Y ESA ES TODA SU FUNCION: PostgreSQL exige
-- que el destino de una clave foranea COMPUESTA sea una restriccion unica sobre
-- exactamente esas columnas. Sin esto, la de `product_commission_rates` —que es
-- lo que impide que el rol copiado diverja— no se puede declarar.
ALTER TABLE commission_rates
    ADD CONSTRAINT uq_commission_rates_id_role UNIQUE (id, role_id);

-- VARIAS TASAS POR ROL SON LEGITIMAS y no hay unicidad sobre `role_id`: el
-- catalogo puede ofrecer «AGENTE 10 %» y «AGENTE 15 %» para asociarlas a
-- productos distintos. Lo que no puede repetirse es un rol sobre el MISMO
-- producto, y eso lo cierra la clave primaria de la asociacion (`RN-CM-013`).
CREATE INDEX idx_commission_rates_role
    ON commission_rates (role_id)
 WHERE deleted_at IS NULL;

COMMENT ON TABLE commission_rates IS
    'Catalogo de tasas por rol. NO rige hasta asociarse a un producto (RN-CM-012).';
COMMENT ON COLUMN commission_rates.percentage IS
    'De 0 a 100. El cero es «no comisiona», y no es lo mismo que no tener tasa (RN-CM-007).';


-- -----------------------------------------------------------------------------
-- 3. `user_commission_rates` — la excepcion por persona.
--
-- SIN `role_id`, por decision del responsable del proyecto: la tasa es de la
-- persona y punto. Lo que eso cuesta esta escrito en `cm.md` §5.3 — una
-- excepcion SOBREVIVE a que su titular deje de vender, y no falla: se queda
-- callada hasta que alguien la mira.
--
-- ES LA UNICA TABLA DEL MODULO CON VIGENCIA, y por tanto la unica que conserva
-- historial: sus filas cerradas dicen que gano esa persona y hasta cuando.
-- -----------------------------------------------------------------------------
CREATE TABLE user_commission_rates (
    id          uuid          PRIMARY KEY,

    user_id     uuid          NOT NULL,

    percentage  numeric(5,2)  NOT NULL,

    -- LA VIGENCIA SE MIDE EN `date` Y NO EN `timestamptz`, que es la excepcion
    -- justificada al criterio del proyecto: una comision cambia «a partir del
    -- dia 1», no a partir de las 00:00:00 de una zona horaria concreta, y esa
    -- decision no la tiene que tomar quien declara una tasa.
    valid_from  date          NOT NULL,

    -- NULO significa «indefinidamente», no «se desconoce».
    valid_to    date          NULL,

    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),

    -- Retiro logico. Una tasa VENCIDA no es lo mismo que una RETIRADA: la
    -- primera dejo de regir y sigue explicando lo que se pago; la segunda no
    -- debio existir.
    deleted_at  timestamptz   NULL,

    CONSTRAINT ck_user_commission_rates_percentage
        CHECK (percentage >= 0 AND percentage <= 100),

    -- `RN-CM-009`. La rama `IS NULL` va DELANTE y explicita: un CHECK que
    -- evalua a NULL ACEPTA la fila, que es el defecto de `ck_deletion_reason`
    -- (`requirements.md` v0.31.0). Sin ella, toda tasa indefinida pasaria sin
    -- comprobarse.
    CONSTRAINT ck_user_commission_rates_vigencia
        CHECK (valid_to IS NULL OR valid_to >= valid_from),

    CONSTRAINT fk_user_commission_rates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);


-- =============================================================================
-- `RN-CM-006` — NINGUN DIA CUBIERTO DOS VECES POR LA MISMA PERSONA.
--
-- Es la unica regla del modulo que sigue en el motor, y no es incoherencia: es
-- LA UNICA QUE DOS PETICIONES SIMULTANEAS PUEDEN BURLAR. Comprobarla con un
-- SELECT previo seguido de un INSERT es una carrera — exactamente el defecto
-- que `RN-SP-018` tuvo y que se corrigio el 26-08-2026.
--
-- NO ES UNA UNICIDAD: lo que no debe repetirse no es un valor, es un INTERVALO.
-- Un UNIQUE no puede expresarlo.
--
-- DOS DETALLES QUE PARECEN COSMETICOS Y NO LO SON:
--
--   1. EL RANGO LLEVA LOS DOS EXTREMOS INCLUIDOS, con corchetes. Con el
--      semiabierto que PostgreSQL usa por omision, dos tasas consecutivas que
--      comparten el dia de corte NO CHOCARIAN y ese dia quedaria cubierto dos
--      veces. Un `valid_to` nulo produce un rango sin limite superior, que es
--      exactamente «rige indefinidamente».
--
--   2. LA RESTRICCION ES PARCIAL SOBRE LAS VIVAS. Sin el `WHERE`, una tasa
--      retirada seguiria bloqueando sus dias y retirar dejaria el periodo
--      inutilizable PARA SIEMPRE — y nada mas fallaria.
--
-- EL `COALESCE` DE `V44` YA NO HACE FALTA: alli normalizaba el producto y la
-- persona nulos, y aqui `user_id` es NOT NULL. La regla se simplifico con el
-- modelo.
-- =============================================================================
ALTER TABLE user_commission_rates
    ADD CONSTRAINT uq_user_commission_rates_vigente
    EXCLUDE USING gist (
        user_id WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (deleted_at IS NULL);

COMMENT ON TABLE user_commission_rates IS
    'Excepcion por persona. Gana siempre sobre la del rol y NO mira el producto (RN-CM-004).';
COMMENT ON COLUMN user_commission_rates.valid_to IS
    'Nulo: rige indefinidamente. Vencida no es lo mismo que retirada.';


-- -----------------------------------------------------------------------------
-- 4. `product_commission_rates` — lo unico que pone una tasa en vigor.
--
-- SIN `deleted_at`, a proposito: una asociacion no es un hecho del pasado que
-- haya que conservar, es una configuracion vigente. Lo que hay que conservar
-- —con que porcentaje se pago— es obligacion de la liquidacion (`RN-CM-008`).
-- Desasociar deja registro de eliminacion FISICA con motivo (Art. V.13), que es
-- donde queda la huella.
-- -----------------------------------------------------------------------------
CREATE TABLE product_commission_rates (

    product_id          uuid         NOT NULL,

    -- COPIADO de la tasa, y no es la desnormalizacion que parece: existe para
    -- que `RN-CM-013` PUEDA DECLARARSE EN EL ESQUEMA. Sin el, la unicidad
    -- «un porcentaje por rol y producto» tendria que unir dos tablas, y ningun
    -- indice lo hace.
    role_id             uuid         NOT NULL,

    commission_rate_id  uuid         NOT NULL,

    created_at          timestamptz  NOT NULL DEFAULT now(),

    -- `RN-CM-013`. LA CLAVE PRIMARIA ES LA REGLA, no una regla que alguien
    -- comprueba: dos tasas del mismo rol sobre el mismo producto harian
    -- INDETERMINADA la resolucion, y la eleccion quedaria a criterio del plan
    -- de ejecucion.
    CONSTRAINT pk_product_commission_rates
        PRIMARY KEY (product_id, role_id),

    CONSTRAINT fk_product_commission_rates_product
        FOREIGN KEY (product_id) REFERENCES products (id),

    -- COMPUESTA A PROPOSITO. Apuntando solo a `commission_rates(id)`, el
    -- `role_id` copiado podria decir una cosa y la tasa otra, y esa
    -- contradiccion no la detectaria nada: la resolucion buscaria por un rol y
    -- pagaria el porcentaje de otro. Asi es IMPOSIBLE, no improbable.
    CONSTRAINT fk_product_commission_rates_rate
        FOREIGN KEY (commission_rate_id, role_id)
        REFERENCES commission_rates (id, role_id)
);

-- Para responder «sobre que productos rige esta tasa» sin recorrer la tabla.
-- La clave primaria ya ordena por producto, que es la otra direccion.
CREATE INDEX idx_product_commission_rates_rate
    ON product_commission_rates (commission_rate_id);

COMMENT ON TABLE product_commission_rates IS
    'La asociacion. Sin ella una tasa de rol no paga nada a nadie (RN-CM-012).';
COMMENT ON COLUMN product_commission_rates.role_id IS
    'Copiado de la tasa. La FK compuesta impide que diverja (cm.md 7.3).';
