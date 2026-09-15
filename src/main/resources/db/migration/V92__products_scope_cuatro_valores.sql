-- =============================================================================
-- RF-PM-001 · RN-PM-019 reescrita (15-09-2026)
-- EL ALCANCE PASA A CUATRO VALORES EXPLICITOS: TIENDA, HOTLINK, AMBOS, NINGUNO.
--
-- Por decision del responsable del proyecto (`requirements/pm.md` §5.2.11), el
-- alcance deja de ser una escala de dos peldaños —TIENDA < HOTLINKS, donde el
-- segundo INCLUIA la tienda— y pasa a decir EN QUE VISTAS DE VENTA esta el
-- producto: TIENDA (solo la oferta), HOTLINK (solo el canal de hotlinks),
-- AMBOS, o NINGUNO (existe y se activa, pero no se ofrece en ninguna parte;
-- solo lo ve administracion).
--
-- EL RENOMBRADO ES FIEL: `HOTLINKS` significaba «tienda y hotlinks», que es lo
-- que `AMBOS` dice con su nombre. Ningun producto cambia lo que mostraba. Es
-- exactamente la salida que V59 dejo escrita —«el dia que ese caso exista, lo
-- que entra es un valor nuevo y no un cambio de significado de los dos que
-- hay»— con una diferencia: en lugar de un tercer peldaño se declaran los
-- canales tal cual, porque con dos vistas de venta construidas los nombres ya
-- no fingen nada.
--
-- EL ORDEN IMPORTA: UPDATE → DROP → ADD. Un CHECK no se puede declarar sobre
-- filas que lo violan, y hasta el UPDATE hay filas que dicen HOTLINKS.
-- =============================================================================

-- 1. Las filas: lo que decia HOTLINKS pasa a decir AMBOS.
UPDATE products SET scope = 'AMBOS' WHERE scope = 'HOTLINKS';

-- 2. La restriccion, reemplazada. SIN DEFAULT, como siempre (`RN-PM-019`): un
-- valor por omision seria una decision comercial tomada por la columna.
ALTER TABLE products DROP CONSTRAINT ck_products_scope;

ALTER TABLE products
    ADD CONSTRAINT ck_products_scope
    CHECK (scope IN ('TIENDA', 'HOTLINK', 'AMBOS', 'NINGUNO'));

-- 3. El comentario dice el dominio nuevo y que NINGUNO no es un estado.
COMMENT ON COLUMN products.scope IS
    'En que vistas de venta se ofrece el producto (RN-PM-019, 15-09-2026): '
    'TIENDA (solo la oferta), HOTLINK (solo el canal de hotlinks), AMBOS, o '
    'NINGUNO (activo o no, no se ofrece en ninguna vista; solo administracion). '
    'Hasta el 15-09-2026 era una escala de dos valores y HOTLINKS incluia la '
    'tienda; esas filas pasaron a AMBOS.';
