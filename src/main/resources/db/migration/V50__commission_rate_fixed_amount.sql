-- =============================================================================
-- RF-CM-001, RF-CM-003, RF-CM-006 — VUELVE EL VALOR DIRECTO
-- (requirements/cm.md v0.7.0, decision del responsable del proyecto).
--
-- Una comision puede declararse de DOS FORMAS y solo una a la vez:
--
--   PORCENTAJE   «gana el 10 % de la venta»    acotada de 0 a 100
--   FIJO         «gana 10.000 por venta»       SIN TOPE POR ARRIBA
--
-- NO SE SUMAN (`RN-CM-016`). No existe «5 % mas 10.000»: el tipo manda y el
-- campo de la otra forma va vacio.
--
-- UNA SOLA MIGRACION PARA LAS DOS TABLAS, y es deliberado. `commission_rates`
-- y `user_commission_rates` reciben exactamente los mismos tres cambios;
-- separarlas dejaria en el historial un estado en el que UNA de las dos piezas
-- admite el valor fijo y la otra no — un estado que nadie querria desplegar y
-- que sin embargo existiria.
--
-- `rate_type` EXISTE AUNQUE PAREZCA DEDUCIBLE de que columna este llena, y esa
-- es la decision de fondo. Sin el, «una forma y solo una» seria una propiedad
-- emergente de dos nulos, y un CHECK que la vigilara NO PODRIA DECIR CUAL de
-- las dos formas quiso declarar quien inserto una fila con las dos vacias. Con
-- la columna, `RN-CM-016` se comprueba contra algo que el negocio declaro.
--
-- LO QUE ESTA MIGRACION NO HACE, Y NADIE MAS HARA:
--
--   1. NO acota `fixed_amount` por arriba (`RN-CM-018`). No puede: esta tabla
--      no conoce el precio del producto, y la personalizada ni siquiera sabe
--      sobre cuales rige. Una tasa de 10.000 fijos sobre un producto de 8.000
--      paga mas de lo que se cobro, CON UN SOLO NIVEL — donde antes hacian
--      falta tres. Lo hereda la liquidacion, que no existe.
--
--   2. NO guarda la moneda (`RN-CM-017`). El importe toma la del PRODUCTO QUE
--      SE VENDE, de modo que LA MISMA FILA PAGA COSAS DISTINTAS en productos
--      de monedas distintas — y en una personalizada, que no se asocia a nada,
--      sobre TODO EL CATALOGO. Se descarto darle moneda propia a la tasa
--      porque la personalizada NO TIENE PRODUCTO con el que comprobar que
--      coincide.
--
-- A DIFERENCIA DE `V49`, AQUI NO SE BORRA NADA. El relleno es exacto y no una
-- suposicion: hasta hoy la unica forma que existia era el porcentaje, de modo
-- que toda fila anterior a esta migracion es de tipo PORCENTAJE por definicion.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. `commission_rates` — el catalogo por rol.
-- -----------------------------------------------------------------------------

ALTER TABLE commission_rates
    ADD COLUMN rate_type    varchar(20)   NOT NULL DEFAULT 'PORCENTAJE',
    ADD COLUMN fixed_amount numeric(14,4) NULL;

-- LA LINEA MAS IMPORTANTE DE ESTA MIGRACION, y la que un despliegue apresurado
-- se dejaria. El DEFAULT existe SOLO para rellenar las filas anteriores; si se
-- quedara, una insercion que OMITA la forma obtendria PORCENTAJE en silencio —
-- que es exactamente lo que `RF-CM-001` 6.1 decide que no debe pasar: la
-- forma se DECLARA, y una peticion que no la declare se rechaza, no se
-- completa. La cubre una prueba de esquema, porque ninguna prueba que pase por
-- la API se enteraria: todas envian la forma.
ALTER TABLE commission_rates
    ALTER COLUMN rate_type DROP DEFAULT;

-- El porcentaje deja de ser obligatorio: solo lo lleva la forma PORCENTAJE.
ALTER TABLE commission_rates
    ALTER COLUMN percentage DROP NOT NULL;

-- REHACER `ck_commission_rates_percentage` NO ANADE NINGUNA COMPROBACION, y por
-- eso hay que hacerlo. La de `V44` dice `percentage >= 0 AND percentage <= 100`;
-- con la columna ya nula, ese CHECK EVALUA A NULL Y ACEPTA LA FILA en todas las
-- de tipo FIJO. Es lo que se quiere. Pero si no se reescribe explicitamente,
-- quien lea el esquema dentro de un ano NO PODRA SABER si eso se decidio o se
-- paso por alto. La rama nula va DELANTE, como en `V49`.
ALTER TABLE commission_rates
    DROP CONSTRAINT ck_commission_rates_percentage;

ALTER TABLE commission_rates
    ADD CONSTRAINT ck_commission_rates_percentage
        CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100));

ALTER TABLE commission_rates
    ADD CONSTRAINT ck_commission_rates_type
        CHECK (rate_type IN ('PORCENTAJE', 'FIJO'));

-- `RN-CM-016`. ES LA RESTRICCION NUEVA MAS FACIL DE ESCRIBIR A MEDIAS:
-- comprobar solo que UNO de los dos este presente admitiria una fila de tipo
-- FIJO con el porcentaje lleno. Se exige que el presente sea EL QUE
-- CORRESPONDE AL TIPO, y que el otro este vacio.
ALTER TABLE commission_rates
    ADD CONSTRAINT ck_commission_rates_forma
        CHECK (
            (rate_type = 'PORCENTAJE' AND percentage IS NOT NULL AND fixed_amount IS NULL)
            OR
            (rate_type = 'FIJO' AND fixed_amount IS NOT NULL AND percentage IS NULL)
        );

-- Solo acota POR ABAJO. Por arriba no lo acota nada y no puede — ver cabecera.
ALTER TABLE commission_rates
    ADD CONSTRAINT ck_commission_rates_fixed
        CHECK (fixed_amount IS NULL OR fixed_amount >= 0);

COMMENT ON COLUMN commission_rates.rate_type IS
    'PORCENTAJE o FIJO. Se DECLARA, no se deduce de que columna este llena: '
    'sin ella una fila con las dos vacias no permitiria saber cual de las dos '
    'formas se quiso declarar. `RN-CM-016`.';

COMMENT ON COLUMN commission_rates.fixed_amount IS
    'Importe fijo por venta. MISMA FORMA QUE `products.price` porque la escala '
    'real la decide la moneda (`currencies.decimal_places`, de 0 a 4). NO LLEVA '
    'MONEDA: toma la del producto que se venda (`RN-CM-017`). NO ESTA ACOTADO '
    'POR ARRIBA (`RN-CM-018`).';

-- -----------------------------------------------------------------------------
-- 2. `user_commission_rates` — la excepcion por persona.
--
-- Los mismos tres cambios y las mismas tres restricciones. La simetria es la
-- decision: en cuanto una de las dos tablas admitiera algo que la otra no
-- —un tope, un decimal mas, una moneda—, `RF-CM-005` tendria que devolver dos
-- cosas distintas segun de donde saliera la respuesta, y LA RESOLUCION DEJARIA
-- DE PODER HABLAR DE «LA COMISION EFECTIVA» EN SINGULAR.
--
-- Y AQUI EL IMPORTE FIJO PESA MAS QUE EN EL CATALOGO. Una tasa de rol se
-- interpreta en la moneda de los productos que alguien le asocio; esta NO SE
-- ASOCIA A NADA (`RN-CM-014`), de modo que 10.000 fijos son diez mil DE CADA
-- MONEDA QUE HAYA EN EL CATALOGO.
-- -----------------------------------------------------------------------------

ALTER TABLE user_commission_rates
    ADD COLUMN rate_type    varchar(20)   NOT NULL DEFAULT 'PORCENTAJE',
    ADD COLUMN fixed_amount numeric(14,4) NULL;

ALTER TABLE user_commission_rates
    ALTER COLUMN rate_type DROP DEFAULT;

ALTER TABLE user_commission_rates
    ALTER COLUMN percentage DROP NOT NULL;

ALTER TABLE user_commission_rates
    DROP CONSTRAINT ck_user_commission_rates_percentage;

ALTER TABLE user_commission_rates
    ADD CONSTRAINT ck_user_commission_rates_percentage
        CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100));

ALTER TABLE user_commission_rates
    ADD CONSTRAINT ck_user_commission_rates_type
        CHECK (rate_type IN ('PORCENTAJE', 'FIJO'));

ALTER TABLE user_commission_rates
    ADD CONSTRAINT ck_user_commission_rates_forma
        CHECK (
            (rate_type = 'PORCENTAJE' AND percentage IS NOT NULL AND fixed_amount IS NULL)
            OR
            (rate_type = 'FIJO' AND fixed_amount IS NOT NULL AND percentage IS NULL)
        );

ALTER TABLE user_commission_rates
    ADD CONSTRAINT ck_user_commission_rates_fixed
        CHECK (fixed_amount IS NULL OR fixed_amount >= 0);

COMMENT ON COLUMN user_commission_rates.rate_type IS
    'PORCENTAJE o FIJO, igual que en el catalogo por rol. `RN-CM-016`.';

COMMENT ON COLUMN user_commission_rates.fixed_amount IS
    'Importe fijo por venta, SIN MONEDA. Y aqui rige sobre TODO EL CATALOGO, '
    'porque esta tasa no se asocia a ningun producto (`RN-CM-014`): se '
    'interpreta en tantas monedas como haya. Consecuencia aceptada, no defecto '
    '(`cm.md` 1.1.1).';
