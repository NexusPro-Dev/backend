-- =============================================================================
-- RF-PM-001 · RN-PM-002, RN-PM-004, RN-PM-017, RN-PM-018
-- UN UPGRADE DECLARA DE DONDE SALE, NO SOLO A DONDE LLEVA.
--
-- Hasta hoy un upgrade solo decia el destino, y QUIEN PODIA COMPRARLO SE
-- DEDUCIA: cualquiera por debajo de ese nivel. Esa deduccion hacia IMPOSIBLE
-- EL SALTO — «subir a ORO» era el mismo producto y el mismo precio para quien
-- sube un escalon y para quien sube tres.
--
-- Con el origen declarado, CADA SALTO ES UN PRODUCTO y cada uno tiene su
-- precio. `FREE -> ORO` y `PLATINO -> ORO` conviven.
--
-- LA CADENA VA AL REVES DE LO QUE PARECE (`V47`): `level` numera desde la cima.
--
--     ORO      level 1   la cima
--     PLATINO  level 2
--     VIP      level 3
--     FREE     level 4   el suelo
--
-- De modo que «el origen esta por debajo del destino» es
-- `level(origen) > level(destino)`.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. La columna, nula de momento.
--
-- NO LLEVA `NOT NULL`, y no es un descuido: en un BOT tiene que estar VACIA
-- (`RN-PM-002`). Quien exige su presencia es el CHECK de mas abajo, que es el
-- unico que puede decir «obligatoria aqui y prohibida alla».
-- -----------------------------------------------------------------------------

ALTER TABLE products
    ADD COLUMN source_membership_id uuid NULL;

-- -----------------------------------------------------------------------------
-- 2. El relleno: FREE, el suelo de la cadena.
--
-- ES UNA DECISION Y NO UNA DEDUCCION, y por eso se escribe aqui. El origen de
-- los upgrades que ya existen NO ESTA EN NINGUN SITIO: bajo el modelo anterior
-- los veia cualquiera por debajo del destino, de modo que no tenian uno.
--
-- Se eligio el suelo (decision del responsable del proyecto, 02-09-2026) porque
-- CONSERVA LA OFERTA DE QUIEN ESTA EN FREE, que es el caso mas comun. Lo que
-- cuesta hay que verlo: QUIEN ESTA EN VIP DEJA DE VER esos upgrades, y no
-- recibe ningun error — simplemente dejan de ofrecerse.
--
-- La alternativa considerada era el nivel inmediatamente inferior al destino,
-- derivable de la cadena; estrecha igual y ademas deja a FREE sin nada.
-- -----------------------------------------------------------------------------

UPDATE products
   SET source_membership_id = (SELECT id FROM memberships WHERE code = 'FREE')
 WHERE type = 'UPGRADE_MEMBRESIA'
   AND source_membership_id IS NULL;

-- -----------------------------------------------------------------------------
-- 3. `RN-PM-002` — las dos, o ninguna.
--
-- El CHECK anterior solo hablaba del destino. Se reescribe entero en lugar de
-- anadir otro al lado: dos restricciones que dicen la mitad cada una se leen
-- como dos reglas, y esto es UNA.
-- -----------------------------------------------------------------------------

ALTER TABLE products
    DROP CONSTRAINT ck_products_type_target;

ALTER TABLE products
    ADD CONSTRAINT ck_products_type_target
        CHECK (
            (type = 'UPGRADE_MEMBRESIA'
                AND target_membership_id IS NOT NULL
                AND source_membership_id IS NOT NULL)
            OR
            (type = 'BOT'
                AND target_membership_id IS NULL
                AND source_membership_id IS NULL)
        );

ALTER TABLE products
    ADD CONSTRAINT fk_products_source_membership
        FOREIGN KEY (source_membership_id) REFERENCES memberships (id);

-- -----------------------------------------------------------------------------
-- 4. `RN-PM-017`, LA MITAD QUE EL MOTOR PUEDE SOSTENER.
--
-- El CHECK exige que las dos membresias NO SEAN LA MISMA — vender un upgrade de
-- FREE a FREE es vender nada.
--
-- LA OTRA MITAD NO CABE AQUI: «el origen esta por debajo del destino» obliga a
-- leer el `level` de DOS filas de `memberships`, y un CHECK no consulta otra
-- tabla. Vive en el dominio, por el mismo motivo exacto que `RN-PM-007` con los
-- decimales de la moneda.
--
-- La rama `IS NULL` va DELANTE y explicita: un CHECK que evalua a NULL ACEPTA
-- la fila, y sin ella todos los BOT pasarian sin comprobarse — que es lo que se
-- quiere, pero conviene que se lea que se quiere.
--
-- SI ESTA SENTENCIA FALLA, hay un upgrade cuyo destino es FREE: bajo el modelo
-- anterior nada lo impedia, y el relleno del paso 2 lo deja apuntando a si
-- mismo. La migracion se detiene, y es lo correcto — ese producto vendia un
-- descenso llamandolo upgrade, y decidir que hacer con el es del negocio.
-- -----------------------------------------------------------------------------

ALTER TABLE products
    ADD CONSTRAINT ck_products_origen_distinto
        CHECK (source_membership_id IS NULL
               OR source_membership_id <> target_membership_id);

-- -----------------------------------------------------------------------------
-- 5. `RN-PM-004` — la unicidad se mueve del destino A LA PAREJA.
--
-- La anterior PROHIBIA EXACTAMENTE LO QUE EL ORIGEN EXISTE PARA PERMITIR: dos
-- upgrades activos hacia ORO, uno desde FREE y otro desde PLATINO.
--
-- Y el motivo por el que la regla existe NO SE DEBILITA. Dos productos activos
-- desde el mismo sitio y hacia el mismo sitio siguen siendo dos precios
-- simultaneos para lo mismo, y quien compre pagara el que la interfaz liste
-- primero. Lo que cambia es QUE CUENTA COMO «LO MISMO»: dos saltos distintos
-- hacia el mismo destino no lo son, y que cuesten distinto es lo normal.
--
-- El relleno del paso 2 NO PUEDE VIOLAR esta restriccion: bajo la regla
-- anterior no podian coexistir dos upgrades activos hacia el mismo destino, de
-- modo que al darles todos el mismo origen las parejas siguen siendo distintas.
-- -----------------------------------------------------------------------------

DROP INDEX uq_products_upgrade_target;

CREATE UNIQUE INDEX uq_products_upgrade_target
    ON products (source_membership_id, target_membership_id)
    WHERE type = 'UPGRADE_MEMBRESIA' AND status = 'ACTIVO' AND deleted_at IS NULL;

COMMENT ON COLUMN products.source_membership_id IS
    'De que membresia sale el upgrade. Obligatoria en UPGRADE_MEMBRESIA y '
    'prohibida en BOT (`RN-PM-002`). NO tiene por que ser la inmediatamente '
    'inferior al destino: saltar niveles es legitimo y es la razon de que se '
    'declare en lugar de deducirse de la cadena (`RN-PM-018`).';
