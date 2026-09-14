-- =============================================================================
-- RF-SP-047 · T-01 — A CUANTO SE CAMBIA UNA MONEDA POR OTRA, Y DESDE CUANDO.
--
-- La tabla que funda el submodulo de tasas de cambio. Se administra POR API, al
-- reves que el catalogo de monedas: `RN-SP-010` deja las monedas fuera porque
-- son un catalogo estable que nadie edita, y una tasa es lo contrario — cambia,
-- y cambia seguido. Meterla en una migracion obligaria a desplegar para
-- corregir un numero.
--
-- ESTA MIGRACION SE NUMERO `V62` AL ESCRIBIR EL PLAN. Entre la tripleta y la
-- construccion, `V64__usuario_con_pais.sql` se llevo el hueco. UNA RESERVA NO
-- ESTA RESERVADA, y no es cosmetico: Flyway aplica EN ORDEN, y una migracion
-- con numero por debajo del ultimo aplicado se queda fuera SIN ERROR Y SIN
-- AVISO. Con `V62`, esta tabla no existiria y nada lo diria hasta la primera
-- consulta. Es la cuarta vez que este proyecto lo paga; las tres anteriores
-- fueron las tablas de `MV`, que pidieron el 51, el 52 y el 53.
-- =============================================================================

CREATE TABLE exchange_rates (
    id                  uuid           PRIMARY KEY,

    source_currency_id  uuid           NOT NULL,
    target_currency_id  uuid           NOT NULL,

    -- numeric(18,8) Y NO numeric(14,4), QUE ES LA FORMA DE TODO IMPORTE DEL
    -- SISTEMA. Hay que romperla: UNA TASA NO ES UN IMPORTE. `products.price` y
    -- los cinco de `movements` llevan la escala que su MONEDA admite
    -- —`currencies.decimal_places` va de cero a cuatro—, pero una tasa NO ESTA
    -- EXPRESADA EN NINGUNA MONEDA: es un cociente entre dos.
    --
    -- Con cuatro decimales, `COP -> USD` —del orden de 0,00024— se guardaria
    -- como 0,0002, y una moneda mas devaluada se guardaria como CERO. Ocho
    -- decimales es lo que usan las tesorerias y las pasarelas; los diez digitos
    -- enteros cubren el extremo contrario.
    price               numeric(18,8)  NOT NULL,

    -- `date` y no `timestamptz`, igual que las dos tablas de tasas de comision.
    -- Una tasa rige POR DIAS, no por instantes: declararla con hora obligaria a
    -- decidir en que zona se corta el dia, que es la decision que
    -- `architecture.md` §15.1.1 resolvio para el codigo de una venta y que aqui
    -- no hace falta abrir.
    valid_from          date           NOT NULL,

    -- NULA significa VITALICIA (`RN-SP-031`).
    valid_to            date           NULL,

    -- BOOLEANO Y NO `varchar` CON CHECK, al reves que `products.status`. Aquel
    -- se declaro asi porque su dominio ES CANDIDATO A CRECER —un `BORRADOR` era
    -- previsible—; aqui no lo es: la unica distincion que un tercer estado
    -- expresaria —«programada, aun no rige»— YA LA EXPRESAN LAS FECHAS. Es la
    -- forma que `currencies` y `countries` usan en este mismo modulo.
    is_active           boolean        NOT NULL DEFAULT true,

    created_at          timestamptz    NOT NULL DEFAULT now(),
    updated_at          timestamptz    NOT NULL DEFAULT now(),

    -- Retiro logico. El MOTIVO no esta aqui: viaja al registro de eliminacion
    -- con la instantanea de lo retirado (Art. V.7 y V.13, `RN-SP-033`).
    deleted_at          timestamptz    NULL,

    CONSTRAINT fk_exchange_rates_source
        FOREIGN KEY (source_currency_id) REFERENCES currencies (id),

    CONSTRAINT fk_exchange_rates_target
        FOREIGN KEY (target_currency_id) REFERENCES currencies (id),

    -- `RN-SP-029`: una tasa de una moneda a si misma no expresa ningun cambio.
    CONSTRAINT ck_exchange_rates_monedas_distintas
        CHECK (source_currency_id <> target_currency_id),

    CONSTRAINT ck_exchange_rates_price_positive
        CHECK (price > 0),

    -- `RN-SP-031`. LA RAMA `IS NULL` VA DELANTE Y EXPLICITA: `valid_to >=
    -- valid_from` sola admitiria el nulo igualmente —un CHECK que evalua a NULL
    -- ACEPTA la fila—, y eso es justo lo que se quiere para una tasa vitalicia.
    -- Se escribe explicito para que ese permiso sea DELIBERADO y no accidental,
    -- que es lo que este proyecto ya pago una vez con `ck_deletion_reason`.
    CONSTRAINT ck_exchange_rates_vigencia
        CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

COMMENT ON TABLE exchange_rates IS
    'A cuanto se cambia una moneda por otra, con su vigencia. Se administra por '
    'API, al reves que el catalogo de monedas (RN-SP-010).';
COMMENT ON COLUMN exchange_rates.price IS
    'Cuantas unidades de destino da UNA de origen. 18,8 porque NO es un importe: '
    'no esta expresada en ninguna moneda y COP->USD ronda 0,00024.';
COMMENT ON COLUMN exchange_rates.valid_to IS
    'Nula: la tasa es VITALICIA. Vencida no es lo mismo que retirada.';


-- -----------------------------------------------------------------------------
-- `RN-SP-032` — DOS TASAS VIGENTES DEL MISMO PAR NO SE SOLAPAN.
--
-- UN `UNIQUE` NO PUEDE EXPRESARLO, y conviene entenderlo antes de intentarlo:
-- la unicidad compara VALORES IGUALES, y aqui lo que no puede repetirse es un
-- SOLAPAMIENTO DE RANGOS. `USD -> COP` del 1 de enero al 30 de junio y
-- `USD -> COP` del 1 de junio al 31 de diciembre tienen fechas DISTINTAS
-- —pasarian cualquier UNIQUE— y en junio hay DOS TASAS PARA EL MISMO CAMBIO.
--
-- LAS TRES PIEZAS HACEN FALTA Y NINGUNA SOBRA:
--
--   1. LAS DOS MONEDAS `WITH =`. Sin ellas la restriccion compararia solo
--      rangos y prohibiria que `USD -> COP` y `USD -> EUR` convivan, que es
--      justo lo que esta regla SI admite.
--
--   2. `daterange(..., '[]')`, CERRADO POR LOS DOS LADOS. Con `'[)'` —el
--      intervalo por omision— una tasa que termina el 30 de junio y otra que
--      empieza el 30 de junio NO SE SOLAPARIAN, y ese dia habria dos.
--
--   3. `WHERE (is_active AND deleted_at IS NULL)`. Sin el, una tasa retirada o
--      suspendida seguiria BLOQUEANDO SUS DIAS PARA SIEMPRE, y nada mas
--      fallaria — el periodo quedaria inutilizable sin que nadie supiera por
--      que.
--
-- Y DE ESE `WHERE` SALE LA CONSECUENCIA QUE HAY QUE ACEPTAR ENTERA: si las
-- inactivas no bloquean, ACTIVAR es la operacion peligrosa y no el alta. Es el
-- reparto que `RN-PM-004` tiene en `PM`, con la diferencia de que alli hay un
-- requerimiento dedicado al estado y aqui no: `RF-SP-047` y `RF-SP-049` tienen
-- LOS DOS que traducir esta violacion a un `409`.
--
-- REQUIERE `btree_gist`, QUE YA ESTA INSTALADA: la puso `V44` para la primera
-- tabla de tasas de comision. NO se vuelve a declarar — un
-- `CREATE EXTENSION IF NOT EXISTS` de mas no rompe nada, y deja creer que la
-- dependencia nace aqui.
-- -----------------------------------------------------------------------------

ALTER TABLE exchange_rates
    ADD CONSTRAINT uq_exchange_rates_vigente
    EXCLUDE USING gist (
        source_currency_id WITH =,
        target_currency_id WITH =,
        daterange(valid_from, valid_to, '[]') WITH &&
    ) WHERE (is_active AND deleted_at IS NULL);


-- -----------------------------------------------------------------------------
-- El indice del listado (`RF-SP-048` · T-01).
--
-- Va con la tabla y no en una migracion propia: el indice es DE LA TABLA. El
-- orden por omision del listado es `valid_from` descendente con el
-- identificador de desempate — sin un orden TOTAL, dos tasas del mismo dia
-- pueden repetirse o saltarse entre paginas, y eso se descubre como «faltan
-- tasas» sin ningun error de por medio.
--
-- NO SE INDEXAN `is_active` NI LAS MONEDAS POR SEPARADO: dos valores no dan
-- selectividad, y el par ya esta cubierto por el indice que crea el EXCLUDE.
-- -----------------------------------------------------------------------------

CREATE INDEX ix_exchange_rates_listado
    ON exchange_rates (valid_from DESC, id DESC);
