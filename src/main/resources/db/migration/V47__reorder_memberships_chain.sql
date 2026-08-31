-- =============================================================================
-- RF-SP-016 — Corrección del orden de la cadena sembrada por `V46`.
--
-- QUÉ ESTABA MAL. `V46` sembró los cuatro niveles con la jerarquía INVERTIDA.
-- Puso `FREE` en `level = 1` y con la superior en nulo, y encadenó hacia
-- `ORO` en `level = 4`. Con la semántica que fija `V13` —«`level` es la
-- DISTANCIA HASTA LA CIMA: la superior tiene 1 y el número crece hacia
-- abajo»— eso no dice «Free es el suelo»: dice que **Free es la cima**, y que
-- la superior de `VIP` es `Free`. Los enlaces decían lo mismo que los números,
-- de modo que no era una discrepancia entre columnas sino una cadena entera del
-- revés.
--
-- POR QUÉ IMPORTA Y NO ES COSMÉTICO. `level` viaja en la respuesta de
-- `RF-SP-017` y el listado ordena por él, así que `GET /api/v1/memberships`
-- venía devolviendo `Free, VIP, Platino, Oro` y presentando `Free` como la cima
-- de la cadena. Y `products.target_membership_id` ya apunta a estas filas: el
-- día que `RF-PM-007` ofrezca los upgrades «hacia una membresía POR ENCIMA de
-- la vigente» —es decir, de `level` MENOR— habría ofrecido subir a `Free`.
--
-- POR QUÉ UNA MIGRACIÓN NUEVA Y NO CORREGIR `V46`. Las versionadas son
-- inmutables una vez aplicadas: editarla cambiaría su suma de verificación y
-- Flyway abortaría el arranque en toda base que ya la tenga, incluidas las de
-- desarrollo. El coste de dejar el rastro es una migración más; el de editarla
-- es un despliegue que no arranca.
--
-- LOS IDENTIFICADORES NO CAMBIAN, y es lo que hace segura esta corrección:
-- `user_memberships` y `products.target_membership_id` siguen apuntando a las
-- mismas filas. Lo único que se mueve es la POSICIÓN de cada una en la cadena.
--
-- CÓMO QUEDA:
--
--     ORO      level 1   sin superior — la cima
--     PLATINO  level 2   bajo ORO
--     VIP      level 3   bajo PLATINO
--     FREE     level 4   bajo VIP — el suelo
-- =============================================================================


-- La renumeración da por hecho que en la tabla están EXACTAMENTE las cuatro
-- filas de `V46`. Si alguien registró otra membresía por `RF-SP-016` antes de
-- este despliegue, reasignar estos cuatro niveles a ciegas chocaría con los
-- suyos y dejaría la cadena partida. Se prefiere abortar el despliegue con un
-- mensaje que diga qué pasa, a que la corrección se convierta en el problema.
DO $$
DECLARE
    total    integer;
    conocidas integer;
BEGIN
    SELECT count(*) INTO total FROM memberships;
    SELECT count(*) INTO conocidas
      FROM memberships
     WHERE code IN ('FREE', 'VIP', 'PLATINO', 'ORO');

    IF total <> 4 OR conocidas <> 4 THEN
        RAISE EXCEPTION
            'V47 esperaba las cuatro membresias de V46 y encontro % filas (% conocidas). '
            'Reordenar la cadena a mano antes de aplicar esta migracion.',
            total, conocidas;
    END IF;
END $$;


-- UNA SOLA SENTENCIA, y no cuatro. Las dos restricciones que esto violaría a
-- mitad de camino —`uq_memberships_level` y `uq_memberships_parent`— se
-- declararon en `V13` como DEFERRABLE INITIALLY DEFERRED, precisamente porque
-- reordenar pasa por estados intermedios en los que dos filas comparten nivel o
-- superior. Se comprueban en el COMMIT, cuando el estado ya es el correcto.
--
-- Las descripciones se corrigen aquí también: las de `V46` afirmaban quién no
-- tenía superior, y eso ha dejado de ser cierto para `FREE` y ha pasado a serlo
-- para `ORO`. Viajan en la respuesta de `RF-SP-017`, de modo que dejarlas
-- mintiendo sería dejar el defecto a medio arreglar.
UPDATE memberships AS m
   SET level                = destino.level,
       parent_membership_id = destino.parent,
       description          = destino.description,
       updated_at           = now()
  FROM (VALUES
        ('ORO',     1::smallint, NULL::uuid,
         'Nivel más alto de la cadena. Es la cima y no está sujeta a ninguna otra.'),

        ('PLATINO', 2::smallint, '01a04ad0-e800-7004-9c4f-5e7ad7000004'::uuid,
         'Nivel intermedio alto, por debajo de Oro.'),

        ('VIP',     3::smallint, '01a04ad0-e800-7003-9c4f-5e7ad7000003'::uuid,
         'Primer nivel de pago, por debajo de Platino.'),

        ('FREE',    4::smallint, '01a04ad0-e800-7002-9c4f-5e7ad7000002'::uuid,
         'Nivel de entrada, sin costo. Es el suelo de la cadena.')
       ) AS destino(code, level, parent, description)
 WHERE m.code = destino.code;


-- ESTA MIGRACIÓN NO EMITE AUDITORÍA, por lo mismo que `V46`: corrige el
-- catálogo que nace con el sistema, no un cambio que alguien hiciera y del que
-- haya que responder.


-- Y SE COMPRUEBA EL RESULTADO, en la migración y no en una prueba.
--
-- La suite no puede protegerlo: varias clases hacen `DELETE FROM memberships`
-- para montar sus propios catálogos, de modo que una prueba sobre las filas
-- sembradas afirmaría algo que depende del orden de ejecución, y reponerlas
-- ella misma la volvería circular —verificaría lo que acaba de escribir—.
--
-- Aquí, en cambio, la comprobación corre en TODOS los arranques: el de cada
-- entorno y el de cada prueba de integración, que levanta la base desde cero y
-- aplica esta migración. Si la cadena quedara mal, no falla una prueba: no
-- arranca nada. Que es la señal correcta para un catálogo del que cuelga qué
-- puede comprar cada persona.
DO $$
DECLARE
    cadena text;
BEGIN
    SELECT string_agg(code, ' > ' ORDER BY level) INTO cadena FROM memberships;

    IF cadena <> 'ORO > PLATINO > VIP > FREE' THEN
        RAISE EXCEPTION 'V47 dejo la cadena como "%" y se esperaba "ORO > PLATINO > VIP > FREE".',
            cadena;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM memberships WHERE code = 'ORO' AND parent_membership_id IS NULL) THEN
        RAISE EXCEPTION 'V47 dejo la cima de la cadena en una membresia distinta de ORO.';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM memberships WHERE code = 'FREE' AND level = 4) THEN
        RAISE EXCEPTION 'V47 no dejo FREE en el suelo de la cadena.';
    END IF;
END $$;
