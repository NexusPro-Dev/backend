-- =============================================================================
-- RF-SP-020 · T-01 — Catálogo de países.
--
-- Campos de `requirements/sp.md` §10.6, restricciones de §10.8 más las tres que
-- el plan añade.
--
-- `RN-SP-009` hace el país inmutable salvo su estado: no se edita ni se elimina.
-- Esa irreversibilidad es la razón de que las garantías de abajo sean más
-- estrictas que las equivalentes de `roles`, donde un error se corrige
-- renombrando.
-- =============================================================================

CREATE TABLE countries (
    id         uuid        PRIMARY KEY,
    code       char(2)     NOT NULL,

    -- LA INTERCALACIÓN SE DECLARA EN LA COLUMNA, no en cada consulta.
    --
    -- `RF-SP-021` exige orden alfabético según el idioma y no por bytes: con la
    -- intercalación `C` —la que una base de datos tiene por defecto si se creó
    -- sin configuración regional— «Panamá» se coloca DESPUÉS de «Perú», porque
    -- la `á` tiene un valor mayor que cualquier letra sin acento. Un selector
    -- ordenado así parece roto.
    --
    -- Declararla aquí y no en la sentencia no es preferencia: la API de
    -- criterios con la que se construye aquella consulta NO PUEDE expresar
    -- COLLATE, ni tampoco Hibernate 6. Con la intercalación en la columna, un
    -- `ORDER BY name` corriente ya ordena bien, y el orden correcto pasa a ser
    -- el comportamiento por omisión de cualquier consulta futura en lugar de
    -- algo que cada una deba recordar.
    --
    -- Se elige ICU y no una intercalación del sistema operativo porque las de la
    -- biblioteca C dependen de qué configuraciones regionales estén instaladas
    -- en la imagen, y `postgres:17-alpine` es una imagen mínima.
    name       varchar(100) COLLATE "es-x-icu" NOT NULL,

    is_active  boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),

    -- `requirements/sp.md` §10.6 y `modelo-datos.md` §2 la omiten, y no es una
    -- omisión inofensiva: el Art. V.7 la exige en toda tabla de negocio, y aquí
    -- además hay algo que modificar —`RF-SP-022` cambia `is_active`—, de modo
    -- que sin ella no habría forma de saber cuándo se retiró un país de la
    -- circulación salvo recorriendo la auditoría. Ambos documentos se enmiendan.
    updated_at timestamptz NOT NULL DEFAULT now(),

    -- Restricción TOTAL y no parcial: no hay borrado lógico, de modo que no
    -- existe estado en el que un código deba poder repetirse. Es la diferencia
    -- con `uq_roles_code`, que sí es parcial porque un rol eliminado libera el
    -- suyo.
    CONSTRAINT uq_countries_code UNIQUE (code),

    -- `char(2)` acota la longitud pero no impide `1`, `-` ni un espacio de
    -- relleno; sin el CHECK, un INSERT directo mete basura en un catálogo que
    -- después nadie puede corregir.
    CONSTRAINT ck_countries_code_format CHECK (code ~ '^[A-Z]{2}$'),

    -- Un nombre de un solo espacio pasaría el NOT NULL y quedaría para siempre.
    CONSTRAINT ck_countries_name_not_blank CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE countries IS
    'Catálogo de países. Inmutable salvo is_active (RN-SP-009, RF-SP-022).';
COMMENT ON COLUMN countries.name IS
    'Intercalación es-x-icu: el orden alfabético del listado depende de ella, no de la consulta.';

-- LA UNICIDAD DEL NOMBRE VA SOBRE LA FORMA NORMALIZADA, y es la única
-- restricción de esta tabla que se aparta de cómo se resolvió lo mismo en
-- `roles`.
--
-- `uq_roles_name` es UNIQUE(name) literal, y `RF-SP-001` aceptó a conciencia que
-- `Contabilidad` y `contabilidad` pudieran coexistir: allí el coste es acotado,
-- porque `RF-SP-004` permite renombrar uno de los dos.
--
-- Aquí no existe esa salida. `RN-SP-009` no admite edición, de modo que `Panamá`
-- y `Panama` conviviendo serían DOS OPCIONES INDISTINGUIBLES EN CADA SELECTOR,
-- PARA SIEMPRE, sin forma de fusionarlas ni de corregir ninguna. Desactivar una
-- retira la opción pero deja los datos que ya la referenciaban apuntando a un
-- país distinto del que apunta el resto.
--
-- El coste asumido es que dos países cuyos nombres solo difieran en acentos o
-- mayúsculas no podrán coexistir. En ISO 3166-1 no hay ninguno, y si apareciera,
-- el código de dos letras los distingue.
--
-- Decidirlo ahora no cuesta nada; decidirlo después del primer país registrado
-- obliga a migrar datos, porque crear el índice sobre una tabla que ya contiene
-- variantes falla.
CREATE UNIQUE INDEX uq_countries_name ON countries (f_unaccent(lower(name)));

-- NO se declara `deleted_at`: `RN-SP-009` hace que un país no se elimine nunca,
-- ni lógica ni físicamente. Añadirla «por simetría con roles» crearía un camino
-- que ningún requerimiento contempla y que el día que alguien lo use dejará
-- huérfanos los datos que referencien al país.
--
-- NO se crea `ix_countries_busqueda`: pertenece a `RF-SP-021`, que es quien
-- introduce la búsqueda. El alta no lo necesita —la unicidad la resuelven los
-- índices únicos— y crearlo ahora sería mantener una estructura que ninguna
-- consulta de este requerimiento usa.
