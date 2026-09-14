-- =============================================================================
-- RF-SP-051 · T-01 y T-02 — Catálogo de tipos de documento.
--
-- Campos de `requirements/sp.md` §10.15 y las restricciones de §10.8.
--
-- LO QUE ESTA MIGRACIÓN CONTIENE NO ES CONFIGURACIÓN: ES LA VALIDACIÓN DE
-- MAYORÍA DE EDAD DEL SISTEMA ENTERO. Léase el bloque del paso 2 antes de tocar
-- una sola línea de la siembra.
--
-- `RN-SP-036` saca este catálogo de la API por completo: no hay alta, ni
-- edición, ni eliminación, ni siquiera cambio de estado — al contrario que
-- `countries` y `currencies`, que sí tienen `RF-SP-022` y `RF-SP-023` para su
-- `is_active`. La asimetría es deliberada y el motivo está abajo.
-- =============================================================================

CREATE TABLE document_types (
    id           uuid         PRIMARY KEY,

    -- LA ABREVIACIÓN ES EL CÓDIGO, y por eso no hay una columna `code` además.
    -- Tener las dos daría TRES identificadores para el mismo concepto —id,
    -- código y abreviación— y obligaría a decidir cuál manda en cada consulta.
    -- Es la diferencia con `currencies`, que sí separa `code` de `symbol`:
    -- allí el símbolo no identifica nada, aquí la abreviación sí.
    abbreviation varchar(10)  NOT NULL,

    -- MISMA INTERCALACIÓN QUE `countries.name`, y por el mismo motivo que `V16`
    -- dejó escrito: la API de criterios no puede expresar `COLLATE`, de modo
    -- que declararlo aquí es lo que hace que un `ORDER BY name` corriente
    -- ordene bien. Con la intercalación `C` —la de una base creada sin
    -- configuración regional— un nombre acentuado cae al final y el desplegable
    -- parece roto.
    --
    -- Aquí importa menos que en los países —son cuatro filas— y se declara
    -- igual: cuesta cero hoy y el día que se note ya no se puede añadir sin
    -- migrar la columna.
    name         varchar(100) COLLATE "es-x-icu" NOT NULL,

    -- `is_active` EXISTE Y NINGÚN ENDPOINT LA ESCRIBE, y conviene justificarlo
    -- porque contradice en apariencia el criterio de `V18`: «una columna
    -- disponible antes de que exista la regla que la gobierna se acaba usando
    -- por un camino que nadie diseñó».
    --
    -- La diferencia es que ESTA COLUMNA SÍ SE LEE DESDE EL PRIMER DÍA:
    -- `RF-SP-051` publica solo los activos. No es una columna dormida.
    --
    -- Y sin ella NO HAY FORMA DE RETIRAR UN TIPO DE DOCUMENTO:
    -- `fk_users_document_type` impide borrar la fila en cuanto una sola persona
    -- la referencie, de modo que la alternativa a `is_active` no es «no tener la
    -- columna», es «no poder retirar nunca».
    is_active    boolean      NOT NULL DEFAULT true,

    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),

    -- Restricción TOTAL y no parcial: no hay borrado lógico en esta tabla, de
    -- modo que no existe estado en el que una abreviación deba poder repetirse.
    CONSTRAINT uq_document_types_abbreviation UNIQUE (abbreviation),

    -- `varchar(10)` acota la longitud y no impide `cc`, `1` ni un espacio.
    -- EL ÚNICO PUNTO DE ENTRADA DE ESTA TABLA ES UNA MIGRACIÓN (`RN-SP-036`), y
    -- precisamente por eso hace falta el CHECK: la API no puede meter basura
    -- aquí, pero una migración descuidada sí — y no habría ningún DTO que la
    -- rechazara antes.
    CONSTRAINT ck_document_types_abbreviation_format
        CHECK (abbreviation ~ '^[A-Z][A-Z0-9]{0,9}$'),

    CONSTRAINT ck_document_types_name_not_blank
        CHECK (length(btrim(name)) > 0)
);

COMMENT ON TABLE document_types IS
    'Documentos de identidad admitidos. SOLO LOS DE MAYOR DE EDAD: su contenido ES la validación (RN-SP-035, RN-SP-036).';
COMMENT ON COLUMN document_types.abbreviation IS
    'Es el código; no hay columna code aparte. Mayúsculas, sin espacios.';
COMMENT ON COLUMN document_types.is_active IS
    'La lee RF-SP-051 y solo la escribe una migración: RN-SP-036 deja este catálogo fuera de la API.';

-- LA UNICIDAD DEL NOMBRE VA SOBRE LA FORMA NORMALIZADA, mismo criterio que
-- `uq_countries_name`: dos entradas que solo difieran en acentos o en caja
-- serían dos opciones indistinguibles en el desplegable del alta de personas.
CREATE UNIQUE INDEX uq_document_types_name
    ON document_types (f_unaccent(lower(name)));

-- NO se declara `deleted_at`: un tipo de documento no se elimina, se retira de
-- la circulación con `is_active`. Es el mismo criterio de `countries` y
-- `currencies`, y aquí es más fuerte todavía — quienes ya lo declararon lo
-- siguen resolviendo, y `fk_users_document_type` lo garantiza.


-- =============================================================================
-- LA SIEMBRA, QUE ES LA REGLA
--
-- VA EN LA MISMA MIGRACIÓN QUE LA TABLA, y no en una aparte. Es el criterio que
-- `V54` aplicó a `movement_types` y `payment_methods` —un catálogo de tipos
-- vacío deja el módulo sin poder registrar nada—, y aquí es peor: `users`
-- apunta a esta tabla y `RN-SP-035` exige un tipo, de modo que una base con
-- este catálogo vacío NO ADMITE DAR DE ALTA A NADIE.
--
-- IDENTIFICADORES UUID v7 LITERALES (Art. V.11), generados una sola vez al
-- redactar esta migración. Marca de tiempo 2026-09-08T12:00:00Z (01a080e3-ae00).
-- Deben ser los mismos en todos los entornos: las pruebas de los seis
-- requerimientos que esta enmienda toca los refieren sin consultarlos.
--
-- -----------------------------------------------------------------------------
-- ⚠  LO QUE ESTA LISTA **NO** LLEVA ES LA FUNCIONALIDAD ENTERA  ⚠
-- -----------------------------------------------------------------------------
--
-- NO ESTÁN LA TARJETA DE IDENTIDAD NI EL REGISTRO CIVIL, que son los documentos
-- que identifican a un MENOR DE EDAD. Esa ausencia ES la validación de mayoría
-- de edad que pide `RN-SP-035`.
--
-- No hay ninguna columna `acredita_mayoria_de_edad` y ningún caso de uso
-- comprueba la edad. La diferencia con esa alternativa es la razón de ser de
-- este diseño:
--
--   * Con una columna, registrar a un menor sería POSIBLE Y RECHAZADO, y
--     bastaría con que un caso de uso futuro —una carga masiva, un endpoint
--     nuevo, el registro por enlace— olvidara mirarla para que dejara de
--     rechazarse. Nada fallaría.
--
--   * Sin ella, registrar a un menor es INEXPRESABLE: no hay identificador que
--     poner en `users.document_type_id` que signifique «Tarjeta de Identidad»,
--     y `fk_users_document_type` no admite otra cosa. Ningún caso de uso puede
--     olvidar una regla que no tiene que ejecutar.
--
-- AÑADIR UNA FILA A ESTA LISTA ES DESACTIVAR LA REGLA PARA TODO EL SISTEMA, y
-- por eso `RN-SP-036` saca el catálogo de la API: si se pudiera administrar por
-- endpoint, cualquiera con el permiso lo haría sin migración, sin revisión y
-- sin que nada fallara. `CA-SP-587` comprueba que estas dos ausencias siguen
-- ahí, y es la única prueba del sistema que verifica que algo NO ESTÁ.
--
-- EL LÍMITE QUEDA DECLARADO EN LUGAR DE FINGIRSE: `PA` y `NIT` NO PRUEBAN
-- mayoría de edad —un pasaporte lo tiene un niño igual—; están porque el
-- negocio los admite como identificación. Lo que esta lista compra es que el
-- camino barato para colar a un menor —declarar su tarjeta de identidad— NO
-- EXISTE. La prueba de verdad exige fecha de nacimiento, y la condición para
-- registrarla está en `RF-SP-051` `spec.md` §14, pregunta 2.
-- =============================================================================

INSERT INTO document_types (id, abbreviation, name) VALUES
('01a080e3-ae00-7001-9c4f-5e7ad6000001', 'CC',  'Cédula de ciudadanía'),
('01a080e3-ae00-7002-9c4f-5e7ad6000002', 'CE',  'Cédula de extranjería'),
('01a080e3-ae00-7003-9c4f-5e7ad6000003', 'PA',  'Pasaporte'),
('01a080e3-ae00-7004-9c4f-5e7ad6000004', 'NIT', 'Número de identificación tributaria');


-- -----------------------------------------------------------------------------
-- Guarda: el catálogo no puede quedar vacío ni contener un documento de menor.
--
-- La primera mitad protege contra una siembra que fallara a medias; la segunda
-- es `CA-SP-587` declarada EN EL MOTOR además de en una prueba, porque una
-- prueba se puede borrar y una migración aplicada no.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    activos  integer;
    menores  text;
BEGIN
    SELECT count(*) INTO activos FROM document_types WHERE is_active;
    IF activos = 0 THEN
        RAISE EXCEPTION
            'V70: el catálogo de tipos de documento quedó vacío. Sin al menos uno activo, '
            'RN-SP-035 hace irrealizable el alta de personas.';
    END IF;

    SELECT string_agg(abbreviation, ', ' ORDER BY abbreviation) INTO menores
      FROM document_types
     WHERE abbreviation IN ('TI', 'RC');

    IF menores IS NOT NULL THEN
        RAISE EXCEPTION
            'V70: el catálogo contiene documentos de MENOR de edad (%). Ese catálogo ES la '
            'validación de RN-SP-035: añadirlos la desactiva para todo el sistema.', menores;
    END IF;
END $$;
