-- =============================================================================
-- RF-SP-019 · T-01 — Catálogo de monedas.
--
-- Campos de `requirements/sp.md` §10.5.
--
-- EL ÚNICO PUNTO DE ENTRADA DE ESTA TABLA ES UNA MIGRACIÓN (`RN-SP-010`): las
-- monedas no se crean, editan ni eliminan por la API. Lo único modificable es
-- `is_active`, a través de `RF-SP-023`. Esa es la razón de que casi todas las
-- garantías de abajo vivan en el esquema y no en Java: una validación de
-- aplicación no cubriría en absoluto el camino por el que esta tabla se escribe.
--
-- Se separa de la siembra por el mismo criterio con el que `RF-SP-010` separó
-- `V2` de `V3`: una migración que se lee «crea la tabla» y otra que se lee
-- «puebla el catálogo» dejan un historial que dice qué pasó. El catálogo crecerá
-- con migraciones posteriores; el esquema no.
-- =============================================================================

CREATE TABLE currencies (
    id             uuid         PRIMARY KEY,
    code           char(3)      NOT NULL,
    name           varchar(100) NOT NULL,
    symbol         varchar(10)  NULL,
    decimal_places smallint     NOT NULL DEFAULT 2,
    is_default     boolean      NOT NULL DEFAULT false,
    is_active      boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),

    -- `updated_at` NO está en `requirements/sp.md` §10.5 y se declara igual: el
    -- Art. V.7 la exige en toda tabla de negocio, y esta sí cambia —`RF-SP-023`
    -- modifica `is_active`—. Sin ella no habría forma de saber cuándo se dio de
    -- baja una moneda sin recorrer la auditoría. §10.5 queda enmendada.
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_currencies_code UNIQUE (code),

    -- No está en §10.7 y se añade: dos filas con el mismo nombre y distinto
    -- código serían indistinguibles en cualquier selector.
    CONSTRAINT uq_currencies_name UNIQUE (name),

    -- ISO 4217 en mayúsculas. `char(3)` acota la longitud pero admitiría `1`,
    -- `-` o un espacio de relleno.
    CONSTRAINT ck_currencies_code_format CHECK (code ~ '^[A-Z]{3}$'),

    -- Cero es legítimo —hay monedas sin fracción— y cuatro es el máximo que usa
    -- ISO 4217. Sin cota, una errata de siembra produce redondeos
    -- silenciosamente erróneos en TODO cálculo posterior, y esa clase de error
    -- no se descubre mirando la tabla.
    CONSTRAINT ck_currencies_decimal_places CHECK (decimal_places BETWEEN 0 AND 4),

    -- Dar de baja la moneda con la que opera el sistema dejaría los importes sin
    -- referencia válida.
    --
    -- Declararla aquí y no esperar a `RF-SP-023` tiene tres consecuencias:
    -- aquel requerimiento nace con la mitad de su trabajo hecho —la operación
    -- falla en la base de datos y su plan solo decide cómo traducir el fallo—;
    -- protege también contra una migración descuidada, que es el único otro
    -- camino de escritura; y cuesta una línea hoy, mientras que añadirla sobre
    -- una tabla en uso obliga a validar las filas existentes.
    CONSTRAINT ck_currencies_default_active CHECK (NOT is_default OR is_active)
);

COMMENT ON TABLE currencies IS
    'Catálogo de monedas. Inmutable por API salvo is_active (RN-SP-010, RF-SP-023).';
COMMENT ON COLUMN currencies.decimal_places IS
    'Condiciona el redondeo de todo cálculo financiero. Cero es legítimo y distinto de «no se sabe».';
COMMENT ON COLUMN currencies.is_default IS
    'Moneda con la que opera el sistema. Exactamente una fila la lleva a true, y no puede desactivarse.';

-- `CA-SP-169`. Garantiza COMO MÁXIMO una moneda por defecto; el «exactamente
-- una» lo aporta la siembra y lo vigila la comprobación de arranque. Es la misma
-- construcción que `uq_roles_single_root`: un índice único parcial, porque una
-- restricción de tabla no admite un WHERE.
CREATE UNIQUE INDEX uq_currencies_single_default ON currencies ((is_default)) WHERE is_default;

-- NO se crea índice de búsqueda ni de ordenamiento: el catálogo tiene hoy una
-- fila y tendrá pocas. Se devuelve entero y ordenado por `code`, y un recorrido
-- secuencial sobre una tabla de ese tamaño es más rápido que consultar cualquier
-- índice. Mismo criterio que `RF-SP-010` aplicó a `permissions`.
