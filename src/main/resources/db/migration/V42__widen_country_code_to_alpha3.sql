-- =============================================================================
-- RF-SP-020 · T-18 — El código de un país pasa a ISO 3166-1 alfa-3.
--
-- `V16` lo declaró `char(2)` con `CHECK (code ~ '^[A-Z]{2}$')`, que es alfa-2
-- (`CO`, `US`). Pasa a `char(3)`, que es alfa-3 (`COL`, `USA`).
--
-- NO SE EDITA `V16`, que es donde nace la tabla: ya está aplicada, y Flyway
-- valida por suma de comprobación. Editarla haría fallar el arranque de toda
-- base que la tenga, con un mensaje que no dice «alguien editó V16» sino
-- «validación fallida». Mismo criterio que `V38` sobre `V13` y `V30` sobre `V7`.
--
-- POR QUÉ ESTO NO ES UN `ALTER` INOFENSIVO. `RN-SP-009` prohíbe editar un país,
-- de modo que el código de una fila ya registrada NO TIENE CAMINO DE
-- CORRECCIÓN por la API: si esta migración lo deja mal, queda mal para siempre.
-- Esa irreversibilidad es la que decide los dos criterios de abajo.
--
-- CRITERIO 1 — EL RELLENO CON ESPACIOS ES LA TRAMPA. Ensanchar `char(2)` a
-- `char(3)` no convierte `CO` en `COL`: lo convierte en `'CO '`. Esa fila pasa
-- el `NOT NULL`, pasa el UNIQUE y solo la caza el `CHECK` nuevo, que fallaría
-- con un mensaje sobre una restricción y no sobre el dato. Por eso el orden es
-- ensanchar, traducir, comprobar, y solo entonces volver a poner el `CHECK`.
--
-- CRITERIO 2 — LA TABLA DE EQUIVALENCIAS NO ADIVINA. Lleva los países de
-- América, que son el mercado de la plataforma, y los mercados con los que ya
-- se opera o se documenta. NO lleva la lista ISO completa a propósito: una
-- equivalencia escrita de memoria y equivocada no da error, da un país mal
-- codificado para siempre. Lo que no está en la lista NO SE TRADUCE A CIEGAS,
-- levanta la excepción del paso 4 y obliga a una decisión humana.
--
-- EN UNA BASE VACÍA —que es el caso normal, porque `V16` no siembra el catálogo
-- y los países se dan de alta por la API— los pasos 2 y 3 no tocan nada.
-- =============================================================================

-- 1. Fuera el CHECK de dos letras: mientras esté, ningún código de tres cabe.
ALTER TABLE countries DROP CONSTRAINT ck_countries_code_format;

-- 2. La columna. `uq_countries_code` e `ix_countries_busqueda` se reconstruyen
--    solos: PostgreSQL rehace todo índice que dependa de la columna alterada.
--
--    Sigue siendo `char` y no `varchar`, por el mismo motivo que en `V16`: con
--    el CHECK exigiendo exactamente tres mayúsculas, el relleno con espacios que
--    caracteriza a `char` no llega a producirse, y la diferencia entre ambos
--    tipos queda sin efecto observable. Cambiar de tipo aquí obligaría además a
--    tocar el `@JdbcTypeCode(SqlTypes.CHAR)` de la entidad.
ALTER TABLE countries ALTER COLUMN code TYPE char(3);

-- 3. Traducción alfa-2 → alfa-3 de lo que ya estuviera registrado.
--
--    Se compara con `btrim` porque el paso 2 dejó `'CO '`, no `'CO'`.
UPDATE countries c
   SET code = e.alfa3
  FROM (VALUES
            -- América
            ('AG', 'ATG'), ('AR', 'ARG'), ('AW', 'ABW'), ('BB', 'BRB'),
            ('BM', 'BMU'), ('BO', 'BOL'), ('BR', 'BRA'), ('BS', 'BHS'),
            ('BZ', 'BLZ'), ('CA', 'CAN'), ('CL', 'CHL'), ('CO', 'COL'),
            ('CR', 'CRI'), ('CU', 'CUB'), ('CW', 'CUW'), ('DM', 'DMA'),
            ('DO', 'DOM'), ('EC', 'ECU'), ('GD', 'GRD'), ('GT', 'GTM'),
            ('GY', 'GUY'), ('HN', 'HND'), ('HT', 'HTI'), ('JM', 'JAM'),
            ('KN', 'KNA'), ('LC', 'LCA'), ('MX', 'MEX'), ('NI', 'NIC'),
            ('PA', 'PAN'), ('PE', 'PER'), ('PR', 'PRI'), ('PY', 'PRY'),
            ('SR', 'SUR'), ('SV', 'SLV'), ('TT', 'TTO'), ('US', 'USA'),
            ('UY', 'URY'), ('VC', 'VCT'), ('VE', 'VEN'),
            -- Resto de mercados documentados
            ('AE', 'ARE'), ('AT', 'AUT'), ('AU', 'AUS'), ('BE', 'BEL'),
            ('CH', 'CHE'), ('CN', 'CHN'), ('DE', 'DEU'), ('DK', 'DNK'),
            ('EG', 'EGY'), ('ES', 'ESP'), ('FI', 'FIN'), ('FR', 'FRA'),
            ('GB', 'GBR'), ('GR', 'GRC'), ('IE', 'IRL'), ('IL', 'ISR'),
            ('IN', 'IND'), ('IT', 'ITA'), ('JP', 'JPN'), ('KR', 'KOR'),
            ('MA', 'MAR'), ('NL', 'NLD'), ('NO', 'NOR'), ('NZ', 'NZL'),
            ('PL', 'POL'), ('PT', 'PRT'), ('RU', 'RUS'), ('SE', 'SWE'),
            ('TR', 'TUR'), ('ZA', 'ZAF')
       ) AS e(alfa2, alfa3)
 WHERE btrim(c.code) = e.alfa2;

-- 4. Lo que no se pudo traducir para el proceso, y dice cuál.
--
--    Sin este paso el fallo llegaría del `CHECK` del paso 5, cuyo mensaje habla
--    de una restricción violada y no del país concreto que hay que resolver.
DO $$
DECLARE
    sin_equivalencia text;
BEGIN
    SELECT string_agg(btrim(code), ', ' ORDER BY code)
      INTO sin_equivalencia
      FROM countries
     WHERE code !~ '^[A-Z]{3}$';

    IF sin_equivalencia IS NOT NULL THEN
        RAISE EXCEPTION
            'V42: estos códigos de país no tienen equivalencia alfa-3 en la migración: %. '
            'Añade su alfa-3 al paso 3 —o borra la fila si el país se registró por error— '
            'y vuelve a ejecutar. No se traducen a ciegas: RN-SP-009 impide corregirlos después.',
            sin_equivalencia;
    END IF;
END $$;

-- 5. El CHECK de vuelta, ahora sobre tres letras. Mismo motivo que en `V16`:
--    `char(3)` acota la longitud pero no impide `1`, `-` ni un espacio de
--    relleno, y sin el CHECK un INSERT directo mete basura en un catálogo que
--    después nadie puede corregir.
ALTER TABLE countries
    ADD CONSTRAINT ck_countries_code_format CHECK (code ~ '^[A-Z]{3}$');

COMMENT ON COLUMN countries.code IS
    'ISO 3166-1 alfa-3 (COL, USA). Tres letras mayúsculas; ck_countries_code_format lo exige.';
