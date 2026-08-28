-- =============================================================================
-- RF-PM-001 · T-19 — El tipo `SERVICIO` pasa a llamarse `BOT`, y el upgrade
-- gana icono (`RN-PM-016`).
--
-- Dos cambios en una migración porque los dos tocan la misma tabla y el mismo
-- `CHECK`: `ck_products_type_target` nombra el literal `'SERVICIO'`, de modo
-- que el renombrado obliga a reescribirlo, y el icono añade su condición sobre
-- el mismo tipo. Separarlos dejaría la restricción intermedia nombrando un
-- valor que ya no existe.
--
-- NO SE EDITA `V39`, que es donde nace la tabla: ya está aplicada, y Flyway
-- valida por suma de comprobación. Editarla haría fallar el arranque de toda
-- base que la tenga, con un mensaje que no dice «alguien editó V39» sino
-- «validación fallida». Mismo criterio que `V42` sobre `V16`, `V38` sobre `V13`
-- y `V30` sobre `V7`.
--
-- POR QUÉ EL RENOMBRADO ES SEGURO HOY Y NO LO SERÁ MAÑANA. `ProductType`
-- advierte que convertir un tipo en otro después de venderlo reescribiría qué
-- compró quien lo compró. Esto NO es eso: la semántica del tipo no cambia
-- —sigue siendo el producto que no toca el nivel de acceso de nadie—, cambia
-- su nombre. Y se hace ahora porque **no existe todavía ninguna tabla de
-- compras** que apunte a un producto: el día que exista, un renombrado de este
-- valor tendrá que arrastrar también lo vendido.
--
-- EL ORDEN NO ES NEGOCIABLE: mientras `ck_products_type` exija
-- `type IN ('UPGRADE_MEMBRESIA', 'SERVICIO')`, ningún `UPDATE` puede escribir
-- `'BOT'`. Por eso los dos `CHECK` caen primero, se traducen las filas, y solo
-- entonces se reponen con el valor nuevo.
-- =============================================================================

-- 1. Fuera las dos restricciones que nombran el literal viejo.
ALTER TABLE products DROP CONSTRAINT ck_products_type;
ALTER TABLE products DROP CONSTRAINT ck_products_type_target;

-- 2. La traducción. En una base sin productos no toca nada, que es el caso
--    normal: `V39` no siembra el catálogo y los productos se dan de alta por la
--    API.
--
--    `updated_at` NO se toca a propósito, por lo mismo que en `V38`: esa marca
--    dice cuándo cambió el producto como hecho de negocio, y renombrar un valor
--    del esquema no lo es. Moverla haría que la auditoría de `RF-SP-011`
--    mostrara una modificación que nadie hizo.
UPDATE products SET type = 'BOT' WHERE type = 'SERVICIO';

-- 3. El icono con el que el frontend pinta el producto (`RN-PM-016`).
--
--    ES UN IDENTIFICADOR, NO UNA IMAGEN. El backend guarda el nombre que el
--    frontend traduce a su propio set de iconos y no sabe pintarlo, igual que
--    con `memberships.color` (`V38`): el sistema no almacena binarios, y
--    decidir dónde vivirían es una decisión abierta que este cambio no necesita.
--
--    Nulo significa «sin icono» y es un estado normal: el icono es OPCIONAL
--    incluso en los upgrades.
ALTER TABLE products ADD COLUMN icon varchar(50);

-- 4. Las restricciones, repuestas sobre el valor nuevo.
ALTER TABLE products
    ADD CONSTRAINT ck_products_type
    CHECK (type IN ('UPGRADE_MEMBRESIA', 'BOT'));

-- La forma de `RN-PM-002` no cambia; solo el literal que nombra.
ALTER TABLE products
    ADD CONSTRAINT ck_products_type_target
    CHECK (
        (type = 'UPGRADE_MEMBRESIA' AND target_membership_id IS NOT NULL)
        OR
        (type = 'BOT' AND target_membership_id IS NULL)
    );

-- 5. `RN-PM-016`: el icono SOLO existe en el upgrade.
--
--    LA MITAD QUE SE OLVIDA ES LA SEGUNDA, y es la que importa. Que un upgrade
--    pueda no tener icono es un estado normal; que un `BOT` lo tenga sería un
--    dato que el frontend pintaría sin que nadie haya decidido que ahí va un
--    icono. Es la misma asimetría que `ck_products_type_target`, con la
--    diferencia de que aquí la primera mitad no obliga a nada.
--
--    El `OR icon IS NULL` va DELANTE a propósito: sin él la condición evalúa a
--    NULL para toda fila sin icono, y una fila que evalúa a NULL SE ACEPTA —el
--    defecto de `ck_deletion_reason` (`requirements.md` v0.31.0)—. Aquí daría
--    igual por casualidad, porque la fila que se quiere rechazar tiene icono no
--    nulo; escribirlo explícito es lo que hace que siga siendo cierto si alguien
--    añade un tercer tipo.
ALTER TABLE products
    ADD CONSTRAINT ck_products_icon_solo_upgrade
    CHECK (icon IS NULL OR type = 'UPGRADE_MEMBRESIA');

-- 6. La forma del identificador: kebab-case en minúsculas, que es como nombran
--    sus iconos los sets al uso (Lucide, Heroicons, Material Symbols).
--
--    Se guarda YA NORMALIZADO —recortado y en minúsculas— por lo mismo que el
--    correo en `V18`: el dato almacenado es el comparable, y una comprobación
--    de forma corriente basta. El `CHECK` impide además que entre por INSERT
--    directo —una migración, una corrección manual— algo que el dominio nunca
--    habría aceptado.
ALTER TABLE products
    ADD CONSTRAINT ck_products_icon_format
    CHECK (icon IS NULL OR icon ~ '^[a-z][a-z0-9-]*$');

COMMENT ON COLUMN products.icon IS
    'Nombre del icono para el frontend, no una imagen. Solo en upgrade y opcional (RN-PM-016).';

-- El comentario de la tabla hablaba de «servicios del sistema».
COMMENT ON TABLE products IS
    'Catalogo de venta del modulo PM: upgrades de membresia y bots del sistema.';
