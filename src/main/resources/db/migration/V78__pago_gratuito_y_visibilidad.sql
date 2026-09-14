-- =============================================================================
-- RF-MV-001 y RF-MV-009 — El pago gratuito, y la visibilidad como eje propio
-- (`RN-MV-022`, `RN-MV-023`).
--
-- LO QUE ESTA MIGRACIÓN CIERRA ES UN AGUJERO DE UN DÍA, y conviene leerlo antes
-- que el SQL.
--
-- `movements.payment_method_id` es NOT NULL: toda venta declara con qué se pagó.
-- Y el 08-09-2026 `RN-PM-006` pasó a ADMITIR EL PRECIO CERO — lo tumbó la
-- renovación `FREE → FREE`, que es un producto legítimo que vale cero.
--
-- Las dos cosas juntas dejaban toda compra gratuita OBLIGADA A DECLARAR
-- `CREDIT_CARD`, `PSE` o `POINTS`, y las tres son falsas. No fallaba nada: el
-- padrón de ventas simplemente dejaba de poder decir qué se cobró de verdad, y
-- `CM` comisionaría sobre un cobro que nunca ocurrió. El esquema no fallaba,
-- MENTÍA.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. La visibilidad, que NO es `is_active` con otro nombre
--
-- Responden DOS PREGUNTAS DISTINTAS QUE NO SON EXCLUYENTES:
--
--   * `is_active`  — ¿sirve para pagar? Un método inactivo está retirado.
--   * `visibility` — ¿se le ofrece a alguien? Un método `INTERNO` SÍ SIRVE, y
--                    nadie lo elige: lo pone el sistema.
--
-- LAS CUATRO COMBINACIONES SIGNIFICAN ALGO, y ahí está el argumento para no
-- fundirlas en un `status` de tres valores: `ACTIVO`+`PUBLICO` es un método
-- corriente; `ACTIVO`+`INTERNO` es el pago gratuito; `INACTIVO`+`PUBLICO` es uno
-- retirado que algún día vuelve; `INACTIVO`+`INTERNO`, uno interno ya retirado.
--
-- Y HAY UNA PRUEBA DE QUE EL EJE FALTABA: `requirements/mv.md` proponía sembrar
-- `POINTS` con `is_active` en falso «para que siga en la tabla y no se ofrezca»
-- — es decir, USABA `is_active` PARA SIGNIFICAR VISIBILIDAD, que es justo la
-- confusión que esta columna deshace.
--
-- ES ENUMERADO Y NO BOOLEANO, por decisión del responsable del proyecto, con el
-- criterio de `products.scope` y no el de `countries.is_active`: el dominio ES
-- CANDIDATO A CRECER —«solo para administración», «solo en un canal»— y con un
-- booleano cada distinción nueva costaría una columna.
--
-- NACE CON `DEFAULT 'PUBLICO'` y aquí el defecto SÍ es correcto, al revés que en
-- `V71` con el país: no describe un dato de nadie, describe el comportamiento
-- por omisión de un catálogo — un método nuevo se ofrece salvo que alguien diga
-- lo contrario, que es la lectura segura. El defecto se queda: no hay filas
-- futuras que pueda falsear.
-- -----------------------------------------------------------------------------
ALTER TABLE payment_methods
    ADD COLUMN visibility varchar(20) NOT NULL DEFAULT 'PUBLICO';

ALTER TABLE payment_methods
    ADD CONSTRAINT ck_payment_methods_visibility
        CHECK (visibility IN ('PUBLICO', 'INTERNO'));

COMMENT ON COLUMN payment_methods.visibility IS
    'PUBLICO se ofrece en el selector; INTERNO sirve para pagar y NADIE lo elige — lo pone el sistema (RN-MV-023). No es is_active con otro nombre.';

-- Las tres sembradas por `V54` quedan `PUBLICO`, que es lo que ya eran de hecho.
-- Se escribe explícitamente en lugar de confiar en el `DEFAULT`: el defecto
-- describe lo que pasa con las filas FUTURAS, y estas ya existían.
UPDATE payment_methods SET visibility = 'PUBLICO' WHERE code IN ('CREDIT_CARD', 'PSE', 'POINTS');


-- -----------------------------------------------------------------------------
-- 2. El pago gratuito
--
-- IDENTIFICADOR UUID v7 LITERAL (Art. V.11), como `V54` con los tres anteriores:
-- las pruebas y el caso de uso lo refieren sin consultarlo. Marca de tiempo
-- 2026-09-09T12:00:00Z (01a08646-7a00).
--
-- `ACTIVO` + `INTERNO`: sirve para pagar y NO SE OFRECE. Es la única fila del
-- catálogo que ningún cliente puede elegir, porque `RF-MV-009` no la devuelve
-- bajo ninguna petición (`RN-MV-023`) — no hay parámetro que la traiga.
--
-- SIN EXCLUSIONES POR PAÍS, y no es un olvido: `payment_method_exclusions`
-- declara dónde NO vale un método para que el cliente lo filtre en un selector,
-- y este no aparece en ningún selector. Excluirlo de un país no cambiaría nada
-- porque nadie lo elige.
-- -----------------------------------------------------------------------------
INSERT INTO payment_methods (id, code, name, is_active, visibility) VALUES
('01a08646-7a00-7001-9c4f-5e7adb000001', 'GRATIS', 'Sin costo', true, 'INTERNO');


-- -----------------------------------------------------------------------------
-- 3. Guarda: el pago gratuito existe, y es INTERNO
--
-- `RN-MV-022` lo hace obligatorio para toda venta de importe cero, de modo que
-- su ausencia no es un catálogo incompleto: es un caso de uso que no puede
-- ejecutarse. Y si alguien lo pasara a `PUBLICO`, aparecería en el selector de
-- pago y cualquiera podría declarar gratis una compra cobrada — `RN-MV-022` lo
-- rechazaría, pero la opción no debería llegar a ofrecerse.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    fila record;
BEGIN
    SELECT is_active, visibility INTO fila
      FROM payment_methods WHERE code = 'GRATIS';

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'V78: falta el método de pago GRATIS. RN-MV-022 obliga a usarlo en toda venta de '
            'importe cero, de modo que sin él esa venta es irrealizable.';
    END IF;

    IF NOT fila.is_active OR fila.visibility <> 'INTERNO' THEN
        RAISE EXCEPTION
            'V78: el método GRATIS debe ser ACTIVO e INTERNO y está activo=% visibilidad=%. '
            'PUBLICO lo pondría en el selector de pago, que es justo lo que no debe ocurrir.',
            fila.is_active, fila.visibility;
    END IF;
END $$;
