-- =============================================================================
-- `brokers` — el catálogo de los brokers con los que opera la plataforma.
--
-- `RF-SP-052` · `T-01`. Decisión del responsable del proyecto del 08-09-2026:
-- nace el submódulo de brokers, y el catálogo guarda DE MOMENTO SOLO EL NOMBRE.
--
-- NACE VACÍA, y es lo correcto: qué brokers entran es una decisión de negocio
-- que todavía no se ha tomado. Cada alta será su propia migración, que es lo
-- que `RN-SP-039` exige — el catálogo no se administra por API.
--
-- SE NUMERÓ COMPROBANDO EL MÁXIMO APLICADO. La `V62` y la `V67` se planificaron
-- y las tomó otra rama el mismo día, dos veces en dos días: aquí se miró antes.
-- =============================================================================

CREATE TABLE brokers (
    id         uuid         PRIMARY KEY,

    -- LA ÚNICA COLUMNA DE NEGOCIO, y por eso ES LA CLAVE DE NEGOCIO. No hay
    -- `code` aparte: con una sola columna, tener además un código daría dos
    -- identificadores para lo mismo y obligaría a decidir cuál manda.
    --
    -- Lo que eso cuesta, dicho aquí para que nadie lo descubra: RENOMBRAR UN
    -- BROKER CAMBIA SU CLAVE DE NEGOCIO. Hoy no lo referencia nadie por nombre
    -- —`user_brokers` apunta por `id`—, de modo que el coste es cero. El día
    -- que un integrador los pida por nombre, hace falta una columna `code`.
    --
    -- MISMA INTERCALACIÓN QUE `countries.name` y `document_types.name`: la API
    -- de criterios no puede expresar `COLLATE`, de modo que declararlo aquí es
    -- lo que hace que un `ORDER BY name` corriente ordene bien.
    name       varchar(120) COLLATE "es-x-icu" NOT NULL,

    -- EXISTE Y NINGÚN ENDPOINT LA ESCRIBE, igual que en `document_types`.
    -- Dejar de operar con un broker no puede borrar las cuentas que ya se
    -- declararon en él: la baja es un cambio de estado por migración, nunca un
    -- DELETE. Por eso tampoco hay `deleted_at`.
    is_active  boolean      NOT NULL DEFAULT true,

    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now()
);

-- ÚNICO FUNCIONAL Y NO SOBRE LA COLUMNA LITERAL, como en `countries` y
-- `document_types`: sin normalizar, «Exness» y «exness» serían dos brokers
-- distintos y las cuentas se repartirían entre los dos sin que nada fallara.
CREATE UNIQUE INDEX uq_brokers_name ON brokers (f_unaccent(lower(name)));

COMMENT ON TABLE brokers IS
    'Catálogo de brokers. Se puebla por migración y solo se consulta (RN-SP-039).';
COMMENT ON COLUMN brokers.name IS
    'Nombre comercial. ES la clave de negocio: no hay columna `code` (08-09-2026).';
