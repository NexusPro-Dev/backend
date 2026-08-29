-- =============================================================================
-- RF-CM-001 · T-01 — Las tarifas de comisión.
--
-- La tabla que funda el módulo `CM`. Una tarifa dice CUÁNTO GANA UN ROL
-- VENDEDOR por vender, y se declara en cuatro grados de precisión donde LA
-- AUSENCIA ES LA QUE DA EL ALCANCE: sin persona rige para todos los del rol,
-- sin producto rige para todo el catálogo. No hay ninguna columna que diga
-- «para todos», porque podría contradecir a las otras dos y esa contradicción
-- no la detecta nada.
--
-- TODA TARIFA RIGE DURANTE UN PERIODO, y por eso esta tabla es EL HISTORIAL de
-- lo que se pagó y no la foto de lo que se paga hoy. Corregir una tarifa
-- reescribe lo que ella dice que rigió; cambiar la comisión a partir de una
-- fecha es cerrar la vigente y registrar otra.
--
-- LAS TRES CLAVES FORÁNEAS CRUZAN A `SP` Y A `PM`, y no contradicen a D-25: la
-- frontera que `modules.md` §7 defiende es la del CÓDIGO —`CM` no lee esas
-- tablas ni importa sus repositorios—, mientras que una clave foránea es
-- integridad declarada en el motor (Art. V.6). Es el mismo criterio con el que
-- `V39` declaró las de `products` hacia `SP`.
-- =============================================================================

-- `btree_gist` aporta las clases de operador que permiten combinar `=` sobre
-- `uuid` con `&&` sobre un rango DENTRO DEL MISMO índice GiST. Sin ella, la
-- restricción de abajo no se puede crear. `V1` ya declara `unaccent` y
-- `pg_trgm`, de modo que no es un precedente nuevo.
CREATE EXTENSION IF NOT EXISTS btree_gist;


CREATE TABLE commission_rates (
    id          uuid          PRIMARY KEY,

    -- Obligatorio incluso en una excepción de persona, y NO es redundante: la
    -- tarifa dice «esta persona, EN ESTE ROL, cobra esto». Sin el rol, una
    -- excepción sobreviviría a que la persona dejara de ser vendedora y
    -- seguiría aplicándose.
    role_id     uuid          NOT NULL,

    -- NULO significa «todo el catálogo». No es un dato que falte.
    product_id  uuid          NULL,

    -- NULO significa «todos los del rol». Tampoco es un dato que falte.
    user_id     uuid          NULL,

    -- numeric(5,2) admite hasta 999.99 por precisión; el CHECK lo acota a
    -- [0, 100]. NO se usa un entero de puntos básicos —la otra forma habitual—
    -- porque el dato que el negocio declara y lee es un porcentaje, y
    -- convertirlo en las dos direcciones es una fuente de errores de escala que
    -- ninguna prueba de camino feliz detecta.
    percentage  numeric(5,2)  NOT NULL,

    -- LA VIGENCIA SE MIDE EN `date` Y NO EN `timestamptz`, que es la excepción
    -- justificada al criterio del proyecto: una comisión cambia «a partir del
    -- día 1», no a partir de las 00:00:00 de una zona horaria concreta, y esa
    -- decisión no la tiene que tomar quien declara una tarifa.
    valid_from  date          NOT NULL,

    -- NULO significa «indefinidamente», no «se desconoce». Es el estado normal
    -- de la tarifa que rige hoy.
    valid_to    date          NULL,

    created_at  timestamptz   NOT NULL DEFAULT now(),
    updated_at  timestamptz   NOT NULL DEFAULT now(),

    -- Retiro lógico (`architecture.md` §6.4). El MOTIVO no está aquí: viaja al
    -- registro de eliminación con la instantánea de lo retirado (Art. V.7 y
    -- V.13). Una tarifa VENCIDA no es lo mismo que una RETIRADA: la primera
    -- dejó de regir y sigue explicando lo que se pagó; la segunda no debió
    -- existir.
    deleted_at  timestamptz   NULL,

    -- `RN-CM-007`. EL CERO SE ADMITE: significa «esto no comisiona», y es la
    -- única forma de exceptuar un producto a un rol que sí tiene tarifa por
    -- omisión. No es lo mismo que no tener tarifa, y quien resuelve las
    -- distingue.
    CONSTRAINT ck_commission_rates_percentage
        CHECK (percentage >= 0 AND percentage <= 100),

    -- `RN-CM-009`. La rama `IS NULL` va DELANTE y explícita: un CHECK que
    -- evalúa a NULL ACEPTA la fila, que es el defecto de `ck_deletion_reason`
    -- (`requirements.md` v0.31.0). Aquí sin ella toda tarifa indefinida pasaría
    -- sin comprobarse.
    CONSTRAINT ck_commission_rates_vigencia
        CHECK (valid_to IS NULL OR valid_to >= valid_from),

    CONSTRAINT fk_commission_rates_role
        FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_commission_rates_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_commission_rates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);


-- =============================================================================
-- `RN-CM-006` — NINGÚN DÍA CUBIERTO DOS VECES.
--
-- Es la restricción más importante de la tabla y la más fácil de escribir mal.
-- No es una unicidad: LO QUE NO DEBE REPETIRSE NO ES UN VALOR, ES UN INTERVALO.
-- Un UNIQUE no puede expresarlo, y por eso esto es un EXCLUDE.
--
-- TIENE QUE ESTAR EN EL MOTOR. Comprobarlo con un SELECT previo seguido de un
-- INSERT es una carrera: dos peticiones simultáneas leen que no hay solape y
-- las dos insertan. Es exactamente el defecto que `RN-SP-018` tuvo y que se
-- corrigió el 26-08-2026.
--
-- TRES DETALLES QUE PARECEN COSMÉTICOS Y NO LO SON:
--
--   1. EL `COALESCE` NO ES UN TRUCO, ES LA ÚNICA SALIDA. En PostgreSQL dos NULL
--      no son iguales, ni en un UNIQUE ni en un EXCLUDE. Sin él, dos tarifas
--      por omisión idénticas del mismo rol —las dos con producto y persona
--      nulos— NO CHOCARÍAN, y esta regla sería una regla escrita que el motor
--      no sostiene. El centinela es el UUID nulo, y es seguro porque los
--      identificadores se generan como UUID v7 (Art. V.11), que nunca produce
--      ceros: no puede colisionar con uno real.
--
--   2. EL RANGO LLEVA LOS DOS EXTREMOS INCLUIDOS, `'[]'`. Con el semiabierto
--      que PostgreSQL usa por omisión, dos tarifas consecutivas que comparten
--      el día de corte NO CHOCARÍAN y ese día quedaría cubierto dos veces. Un
--      `valid_to` nulo produce un rango sin límite superior, que es exactamente
--      «rige indefinidamente».
--
--   3. LA RESTRICCIÓN ES PARCIAL SOBRE LAS VIVAS. Sin el `WHERE`, una tarifa
--      retirada seguiría bloqueando sus días y retirar dejaría el periodo
--      inutilizable PARA SIEMPRE — y nada más fallaría.
-- =============================================================================
ALTER TABLE commission_rates
    ADD CONSTRAINT ex_commission_rates_sin_solape
    EXCLUDE USING gist (
        role_id WITH =,
        COALESCE(product_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        COALESCE(user_id,    '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (deleted_at IS NULL);


COMMENT ON TABLE commission_rates IS
    'Tarifas de comision del modulo CM. Historial: una combinacion admite varias consecutivas.';
COMMENT ON COLUMN commission_rates.product_id IS
    'Nulo: la tarifa rige para todo el catalogo. No es un dato que falte (RN-CM-004).';
COMMENT ON COLUMN commission_rates.user_id IS
    'Nulo: la tarifa rige para todos los del rol. No es un dato que falte (RN-CM-004).';
COMMENT ON COLUMN commission_rates.valid_to IS
    'Nulo: rige indefinidamente. Vencida no es lo mismo que retirada.';
COMMENT ON COLUMN commission_rates.percentage IS
    'De 0 a 100. El cero es «no comisiona», y no es lo mismo que no tener tarifa (RN-CM-007).';
