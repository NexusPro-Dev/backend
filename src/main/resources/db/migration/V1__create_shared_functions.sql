-- RF-SP-010 · T-01 — Funciones y extensiones compartidas.
--
-- Primera migración del sistema. No crea ninguna tabla: deja disponibles las
-- herramientas de búsqueda que varios módulos necesitan desde su primer día.
-- Cada requerimiento decide después si las indexa (plan.md §2).
--
-- Vive aquí y no en RF-SP-002 —que fue quien la estrenó sobre papel— porque
-- RF-SP-010 se implementa antes y su búsqueda sobre la descripción de los
-- permisos también ignora acentos. Con la función en la última migración de
-- RF-SP-002, el catálogo habría quedado con la búsqueda rota entre un
-- requerimiento y otro: no un fallo visible al arrancar, sino un 42883 en
-- ejecución la primera vez que alguien escribiera en el buscador.

CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Por qué no se indexa unaccent() directamente.
--
-- La función unaccent(text) de una sola firma que instala la extensión está
-- declarada STABLE, no IMMUTABLE, porque resuelve el diccionario a través del
-- search_path de la sesión. PostgreSQL rechaza todo índice de expresión que
-- invoque una función no inmutable, de modo que
--
--     CREATE INDEX ... ON roles (unaccent(lower(name)))
--
-- falla. La forma admitida es la variante de dos argumentos, que recibe el
-- diccionario explícito y es determinista; envolverla y declararla IMMUTABLE
-- es lo que la vuelve indexable.
--
-- El diccionario se escribe cualificado ('public.unaccent'::regdictionary)
-- para que la función no dependa del search_path de quien la llame.
--
-- ADVERTENCIA: esa declaración IMMUTABLE es una promesa que la base de datos
-- no verifica. Quien redefina el diccionario unaccent DEBE reindexar todo lo
-- que dependa de esta función: los índices conservarían los valores calculados
-- con el diccionario anterior y devolverían resultados incorrectos sin error.
CREATE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    RETURN public.unaccent('public.unaccent'::regdictionary, $1);

COMMENT ON FUNCTION f_unaccent(text) IS
    'Envoltorio IMMUTABLE de unaccent, indexable. RF-SP-010. '
    'Tocar el diccionario unaccent obliga a REINDEX de sus consumidores.';
