-- ---------------------------------------------------------------------------
-- V13 — El paquete declara su VIGENCIA (RN-PM-047, requirements/pm.md §5.2.13,
-- 16-09-2026). Desde qué día se ofrece, obligatorio, y hasta qué día, opcional:
-- NULL es «indefinidamente». Fuera de esas fechas el paquete SE OCULTA de la
-- oferta y del hotlink y NO cambia de estado — eso lo decide Java, en el mismo
-- objeto y en el mismo orden que RN-PM-039, porque depende de qué día es y un
-- CHECK no consulta el reloj.
--
-- `date` y no `timestamptz`: se declara un día, no un instante, como en
-- user_commission_rates — la segunda tabla del sistema con vigencia.
--
-- TRES PASOS Y NO UNO: la columna nace nula, las filas que ya había reciben su
-- fecha de alta —el único valor que no inventa nada: desde que existen se han
-- podido ofrecer— y después se vuelve obligatoria. SIN DEFAULT: el alta la
-- declara siempre, y un DEFAULT CURRENT_DATE haría que un INSERT que la
-- olvidara pareciera correcto.
--
-- Sobre V5, que no se reescribe (modelo-datos.md §5.4).
-- ---------------------------------------------------------------------------

ALTER TABLE product_packages
    ADD COLUMN valid_from date NULL,
    ADD COLUMN valid_to   date NULL;

UPDATE product_packages
   SET valid_from = (created_at AT TIME ZONE 'UTC')::date
 WHERE valid_from IS NULL;

ALTER TABLE product_packages
    ALTER COLUMN valid_from SET NOT NULL;

ALTER TABLE product_packages
    ADD CONSTRAINT ck_product_packages_validity
        CHECK (valid_to IS NULL OR valid_to >= valid_from);

COMMENT ON COLUMN product_packages.valid_from IS
    'Desde qué día se ofrece el paquete (RN-PM-047). Obligatoria y sin DEFAULT: el alta la declara siempre. Las filas anteriores a V13 recibieron su fecha de alta.';
COMMENT ON COLUMN product_packages.valid_to IS
    'Último día en que se ofrece el paquete (RN-PM-047); NULL = indefinidamente. Fuera de la vigencia el paquete se oculta y no cambia de estado.';
