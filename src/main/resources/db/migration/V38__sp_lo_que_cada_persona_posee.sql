-- =============================================================================
-- V38 — `user_memberships` pasa a ser `user_products` (RN-SP-056,
-- requirements/sp.md §10.12, RF-SP-024 plan.md §2.3.quater, 23-09-2026).
--
-- LA TABLA DEJA DE GUARDAR NIVELES Y PASA A GUARDAR LO QUE CADA PERSONA TIENE.
-- Una fila por cosa poseída —un bot o una membresía—, con su periodo. La
-- membresía deja de ser lo único poseible y pasa a ser EL CASO QUE CONCEDE
-- NIVEL, reconocible porque lleva `membership_id` poblado.
--
-- SE RENOMBRA Y NO SE COPIA, y la diferencia importa. Un RENAME conserva los
-- datos, las claves foráneas que apuntan aquí y los asientos de auditoría ya
-- escritos; copiar a una tabla nueva obligaría a DECIDIR QUÉ PRODUCTO
-- INVENTARLES a las filas que ya existen, y no hay ninguno que sea cierto. Las
-- filas anteriores quedan con `product_id` nulo, que es exactamente lo que son:
-- niveles concedidos SIN COMPRA.
--
-- `product_id` NULO NO ES UN HUECO DEL DISEÑO, ES LO QUE NO SE COMPRA. Hay dos
-- cosas que se poseen sin venta y las dos son la misma regla: el suelo que
-- RN-SP-018 concede al registrarse y la fila del superadministrador que siembra
-- V9. `ck_user_products_origen` impide que esa nulidad se extienda a una fila
-- que no sea ni una cosa ni un nivel.
--
-- LAS DOS RESTRICCIONES DEL INVARIANTE PASAN A SER PARCIALES, y es la
-- consecuencia mayor. Decían «por persona» y ahora dicen «por persona, entre lo
-- que concede nivel»: el invariante nunca fue de la tabla sino DEL NIVEL. Nadie
-- tiene dos niveles a la vez, pero tener un bot y una membresía a la vez es lo
-- corriente, y dos bots comprados el mismo mes se solapan sin que nada esté mal.
-- Dejarlas como estaban convertiría la tabla nueva en una que NO ADMITE DOS
-- PRODUCTOS, que es lo contrario de lo que se pedía.
--
-- Y RETIRA DOS PERMISOS, que es lo único que no se deshace solo: RF-SP-032 y
-- RF-SP-033 quedan DESCARTADOS el mismo día (requirements.md v0.211.0). El
-- nivel se compra (RF-MV-003) o se recibe al registrarse; fijarlo a mano deja de
-- ser una puerta del sistema. El catálogo baja de 135 a 133 — es la primera vez
-- que BAJA, y por eso las guardas del final son tan estrictas como las de V37.
--
-- Sobre V4, que no se reescribe (modelo-datos.md §5.4).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. El renombrado. La tabla y detrás todo lo que lleva su nombre: renombrar la
--    tabla NO renombra sus restricciones ni sus índices, y dejarlos con el
--    nombre viejo haría que el esquema mintiera en cada mensaje de error.
-- ---------------------------------------------------------------------------

ALTER TABLE user_memberships RENAME TO user_products;

ALTER TABLE user_products RENAME CONSTRAINT pk_user_memberships              TO pk_user_products;
ALTER TABLE user_products RENAME CONSTRAINT ck_user_memberships_periodo      TO ck_user_products_periodo;
ALTER TABLE user_products RENAME CONSTRAINT ck_user_memberships_cierre       TO ck_user_products_cierre;
ALTER TABLE user_products RENAME CONSTRAINT fk_user_memberships_user         TO fk_user_products_user;
ALTER TABLE user_products RENAME CONSTRAINT fk_user_memberships_membership   TO fk_user_products_membership;

-- ---------------------------------------------------------------------------
-- 2. Las tres columnas nuevas, y `membership_id` que pasa a admitir nulo.
--
--    `validity_days` es una COPIA de lo vendido y no un cálculo: el catálogo
--    cambia, y sin la copia «hasta cuándo» dejaría de ser verificable el día que
--    alguien edite el producto. Es el mismo motivo por el que la línea de venta
--    ya la copia (RN-MV-020).
-- ---------------------------------------------------------------------------

ALTER TABLE user_products
    ADD COLUMN product_id         uuid    NULL,
    ADD COLUMN movement_detail_id uuid    NULL,
    ADD COLUMN validity_days      integer NULL;

ALTER TABLE user_products ALTER COLUMN membership_id DROP NOT NULL;

ALTER TABLE user_products
    ADD CONSTRAINT fk_user_products_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,
    -- La segunda clave foránea de SP que apunta a MV, después de
    -- `client_sellers.first_movement_id`. NO invierte la dependencia entre
    -- módulos: SP no lee `movement_details` ni sabe qué es una venta; guarda el
    -- identificador que la orden de D-26 le entrega, y del resto se encarga el
    -- motor.
    --
    -- CASCADE Y NO RESTRICT, que es la única de las cuatro que no lo lleva. La
    -- posesión es DERIVADA de la línea: si la línea desapareciera, la fila que
    -- queda no apunta a nada y no dice de dónde salió lo que alguien tiene.
    -- En producción la cláusula NO SE DISPARA NUNCA —una venta no se edita ni se
    -- borra (`RN-MV-001`), solo avanza de estado—, de modo que lo único que
    -- RESTRICT protegía aquí era un caso que no ocurre, a cambio de obligar a
    -- cada suite de pruebas a aprenderse un orden de borrado. El producto sí va
    -- con RESTRICT, y la asimetría es la que importa: un producto se retira en
    -- lógico y nadie puede hacerlo desaparecer mientras alguien lo tenga.
    ADD CONSTRAINT fk_user_products_movement_detail
        FOREIGN KEY (movement_detail_id) REFERENCES movement_details (id) ON DELETE CASCADE,
    -- Una línea produce COMO MUCHO UNA posesión: es lo que hace IDEMPOTENTE la
    -- entrega. Una confirmación repetida no puede duplicar lo que alguien tiene,
    -- y no hay que comprobarlo antes de insertar — comprobarlo sería una carrera.
    ADD CONSTRAINT uq_user_products_linea
        UNIQUE (movement_detail_id),
    ADD CONSTRAINT ck_user_products_origen
        CHECK (product_id IS NOT NULL OR membership_id IS NOT NULL),
    ADD CONSTRAINT ck_user_products_validity_days
        CHECK (validity_days IS NULL OR validity_days > 0);

-- ---------------------------------------------------------------------------
-- 3. El invariante del nivel, ahora parcial. Las dos se DESTRUYEN y se vuelven a
--    declarar porque a ninguna se le puede añadir un predicado en el sitio.
-- ---------------------------------------------------------------------------

DROP INDEX uq_user_memberships_abierta;
DROP INDEX ix_user_memberships_membership_id;
ALTER TABLE user_products DROP CONSTRAINT ex_user_memberships_sin_solape;

CREATE UNIQUE INDEX uq_user_products_membresia_abierta
    ON user_products (user_id)
 WHERE closed_at IS NULL AND membership_id IS NOT NULL;

ALTER TABLE user_products
    ADD CONSTRAINT ex_user_products_membresia_sin_solape
        EXCLUDE USING gist (
            user_id WITH =,
            tstzrange(started_at, COALESCE(LEAST(ends_at, closed_at), 'infinity'::timestamptz)) WITH &&)
        WHERE (membership_id IS NOT NULL);

-- Filtro por membresía de RF-SP-025. La segunda condición evita indexar las
-- posesiones sin nivel, que pasan a ser la mayoría de las filas.
CREATE INDEX ix_user_products_membership
    ON user_products (membership_id)
 WHERE closed_at IS NULL AND membership_id IS NOT NULL;

-- «¿Qué tiene HOY esta persona?», que es lo que pregunta RF-MV-014 y lo que
-- antes no preguntaba nadie. Hace falta porque el único de arriba dejó de
-- cubrirlo al volverse parcial: aquel ya solo indexa las filas con nivel.
CREATE INDEX ix_user_products_user_abierto
    ON user_products (user_id)
 WHERE closed_at IS NULL;

-- ---------------------------------------------------------------------------
-- 4. Los comentarios, que son los de V4 reescritos para lo que la tabla es hoy.
-- ---------------------------------------------------------------------------

COMMENT ON TABLE user_products IS
    'HISTORIAL de lo que cada persona POSEE: una fila por cosa y por periodo (RN-SP-056). La que lleva membership_id es la que concede nivel, y de esas la abierta (closed_at IS NULL) es la actual y hay como mucho una. Vigente = abierta Y dentro de fecha (RN-SP-014). Se llamaba user_memberships hasta el 23-09-2026.';
COMMENT ON COLUMN user_products.product_id IS
    'Que se tiene. NULO SOLO EN LO QUE NO SE COMPRA: el suelo de RN-SP-018 y la semilla fundacional. ck_user_products_origen impide la fila que no es ni una cosa ni un nivel.';
COMMENT ON COLUMN user_products.membership_id IS
    'El nivel que concede lo poseido, si concede alguno. NULO en un bot. Es la columna que las dos restricciones parciales miran para decidir si la fila entra en el invariante de RN-SP-014.';
COMMENT ON COLUMN user_products.movement_detail_id IS
    'La linea que lo entrego. UNICA (uq_user_products_linea): es lo que hace idempotente la entrega. Nula en lo que no viene de una venta.';
COMMENT ON COLUMN user_products.validity_days IS
    'COPIA de los dias vendidos, no un calculo. Nula = no caduca. Esta aqui por lo mismo que en la linea: el catalogo cambia y el vencimiento ya concedido no puede moverse con el.';
COMMENT ON COLUMN user_products.ends_at IS
    'Nula = indefinida. Hasta cuando se PAGO, no cuando se cerro.';
COMMENT ON COLUMN user_products.closed_at IS
    'Nula = es la fila ABIERTA (la actual). Poblada = ya no se tiene. NO es una marca de caducado: nadie la escribe por el paso del tiempo. Hoy solo la escribe conceder una membresia encima de otra y eliminar a la persona (RF-SP-029); dice ademas «se cancelo antes de tiempo» para el dia que exista una operacion que cancele lo entregado.';

-- ---------------------------------------------------------------------------
-- 5. Los dos permisos que se van con RF-SP-032 y RF-SP-033.
--
--    EN ESTE ORDEN Y NO AL REVÉS: `fk_role_permissions_permissions` es
--    ON DELETE RESTRICT, de modo que el permiso no se puede borrar mientras un
--    rol lo porte. Se borra el reparto y después el permiso.
-- ---------------------------------------------------------------------------

DELETE FROM role_permissions
 WHERE permission_id IN (SELECT id FROM permissions
                          WHERE code IN ('users:assign-membership', 'users:revoke-membership'));

DELETE FROM permissions
 WHERE code IN ('users:assign-membership', 'users:revoke-membership');

-- ---------------------------------------------------------------------------
-- 6. Guardas. Aborta si el esquema o el catálogo no quedan como se diseñó.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    filas       integer;
    de_raiz     integer;
    de_admin    integer;
    huerfanas   integer;
    sin_origen  integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 133 THEN
        RAISE EXCEPTION 'V38: el catalogo debe quedar en 133 permisos; tiene %. Si dice 135, los dos DELETE no alcanzaron nada', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 133 OR de_admin <> 127 THEN
        RAISE EXCEPTION 'V38: SUPERADMIN debe portar 133 permisos y ADMIN 127 (la reserva sigue siendo seis); tienen % y %', de_raiz, de_admin;
    END IF;

    -- Ningun rol se queda apuntando a un permiso que ya no existe. Lo garantiza
    -- la clave foranea, y se comprueba igual: es la mitad del cambio que no se
    -- ve en ninguna prueba de la operacion retirada.
    SELECT count(*) INTO huerfanas
      FROM role_permissions rp
      LEFT JOIN permissions p ON p.id = rp.permission_id
     WHERE p.id IS NULL;
    IF huerfanas <> 0 THEN
        RAISE EXCEPTION 'V38: quedan % filas de role_permissions sin permiso', huerfanas;
    END IF;

    -- Ninguna fila de la tabla renombrada quedo sin producto y sin membresia.
    -- Antes de V38 todas llevaban membresia, de modo que esto debe dar cero; si
    -- no lo da, el renombrado alcanzo a algo que no era esta tabla.
    SELECT count(*) INTO sin_origen
      FROM user_products
     WHERE product_id IS NULL AND membership_id IS NULL;
    IF sin_origen <> 0 THEN
        RAISE EXCEPTION 'V38: % filas de user_products no son ni un producto ni un nivel', sin_origen;
    END IF;
END $$;
