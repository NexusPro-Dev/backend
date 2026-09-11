-- =============================================================================
-- RF-CM-006 — La tasa personalizada se asocia a productos CON EL MISMO
-- MECANISMO que la de rol (`RN-CM-014`, `RN-CM-012` y `RN-CM-015`, 11-09-2026).
--
-- DESHACE `V84`, QUE SE APLICO ESTE MISMO DIA, y conviene decir por que existen
-- las dos en lugar de editar aquella: una migracion aplicada NO SE EDITA nunca
-- —Flyway valida por suma de comprobacion, y tocarla hace que toda base que ya
-- la ejecuto falle al arrancar con un «validacion fallida» que no dice quien la
-- edito (ver la cabecera de `V30`)—. De modo que el historial conserva el paso
-- en falso, que es lo correcto: se ve que hubo dos formas y cual gano.
--
-- EL FONDO NO CAMBIA. `V84` quito a la personalizada el regir sobre todo el
-- catalogo, y eso se mantiene. Lo que cambia es LA FORMA: aquella le dio una
-- COLUMNA `product_id` fijada al crearla; esta le da una TABLA DE ASOCIACION,
-- gemela de `product_commission_rates`, con lo que el modulo pasa a tener UNA
-- SOLA manera de decir sobre que rige una tasa.
--
-- LO QUE ESO HABILITA: una misma tasa personalizada puede cubrir VARIOS
-- productos, puede existir SIN REGIR NADA (`RN-CM-012`, que deja de tener
-- excepcion) y puede DESASOCIARSE sin retirarse.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Fuera la columna de `V84`.
--
-- SIN GUARDA ESTA VEZ, al reves que `V84`. Alli la guarda existia porque habia
-- que INVENTAR un dato que no se podia deducir; aqui se quita una columna, y lo
-- que se pierde —a que producto se ato una tasa ayer— se puede volver a
-- declarar asociandola. Que `V84` naciera con una guarda que aborta significa
-- ademas que NINGUNA base pudo aplicarla teniendo personalizadas vivas: las que
-- existan son de hoy.
-- -----------------------------------------------------------------------------
-- EL `EXCLUDE` VA PRIMERO Y A PROPOSITO: `V84` lo redefinio SOBRE `product_id`,
-- de modo que soltar la columna se lo llevaria por delante en silencio y el
-- `DROP CONSTRAINT` de mas abajo fallaria con «no existe». Retirarlo aqui deja
-- las dos decisiones separadas y visibles: primero se retira la regla, despues
-- se quita la columna.
ALTER TABLE user_commission_rates
    DROP CONSTRAINT uq_user_commission_rates_vigente;

ALTER TABLE user_commission_rates
    DROP CONSTRAINT fk_user_commission_rates_product;

ALTER TABLE user_commission_rates
    DROP COLUMN product_id;


-- -----------------------------------------------------------------------------
-- 2. `RN-CM-006` SALE DEL MOTOR, y se retira sin sustituto.
--
-- ESTO HAY QUE LEERLO DESPACIO, porque es el unico movimiento del modulo en
-- esta direccion y el `EXCLUDE` llevaba aqui desde `V49`.
--
-- La regla es «ninguna persona con dos tasas vivas cubriendo el mismo dia SOBRE
-- EL MISMO PRODUCTO». El indice cabia mientras persona, vigencia y producto
-- vivian en la MISMA FILA. Con el producto en una tabla de asociacion, la regla
-- CRUZA DOS TABLAS — y ningun indice hace eso.
--
-- NO SE SUSTITUYE POR UNO SOBRE (user_id, daterange), que es la tentacion: eso
-- prohibiria dos tasas simultaneas de la misma persona sobre productos
-- DISTINTOS, que es justamente lo que esta enmienda existe para permitir.
--
-- PASA AL CASO DE USO, al asociar, con un BLOQUEO CONSULTIVO POR PERSONA — el
-- mismo patron que `ProductCommissionCapGuard` ya usa por producto. Lo que se
-- pierde queda dicho: era la unica garantia del modulo que no dependia de que
-- alguien se acordara de comprobarla.
-- -----------------------------------------------------------------------------
COMMENT ON TABLE user_commission_rates IS
    'Excepcion por persona. Rige SOLO donde este asociada (RN-CM-012, 11-09-2026). RN-CM-006 ya no vive aqui: cruza dos tablas.';


-- -----------------------------------------------------------------------------
-- 3. La asociacion, gemela de `product_commission_rates`.
--
-- LA CLAVE PRIMARIA ES LA REGLA, igual que alli: la misma tasa no se asocia dos
-- veces al mismo producto, y no es algo que alguien comprueba.
--
-- LO QUE NO COPIA DE SU GEMELA ES EL `role_id`. Alli esa columna viaja COPIADA
-- de la tasa para que `RN-CM-013` —un porcentaje por rol y producto— pudiera
-- declararse en el esquema, y con una clave foranea compuesta que impide que
-- diverja. Aqui NO HAY NADA EQUIVALENTE QUE COPIAR: la regla hermana
-- (`RN-CM-006`) habla de PERSONA Y FECHAS, y las fechas no caben en una clave
-- primaria sin volver a necesitar el `EXCLUDE` que acabamos de retirar. Copiar
-- `user_id` aqui no compraria ninguna restriccion y solo anadiria un dato que
-- puede mentir.
--
-- SIN RETIRO LOGICO, como su gemela: una asociacion no es un hecho del pasado
-- que haya que conservar, es configuracion vigente. Desasociar deja registro de
-- eliminacion FISICA con motivo (Art. V.13), que es donde queda la huella. De
-- ahi sale `RN-CM-015`: una tasa asociada no se retira.
-- -----------------------------------------------------------------------------
CREATE TABLE user_commission_rate_products (

    user_commission_rate_id  uuid         NOT NULL,

    product_id               uuid         NOT NULL,

    created_at               timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT pk_user_commission_rate_products
        PRIMARY KEY (user_commission_rate_id, product_id),

    CONSTRAINT fk_user_commission_rate_products_rate
        FOREIGN KEY (user_commission_rate_id) REFERENCES user_commission_rates (id),

    CONSTRAINT fk_user_commission_rate_products_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);


-- EL INDICE POR PRODUCTO, que la clave primaria no da: la resolucion de
-- `RF-CM-005` entra por (persona, producto) y la comprobacion de `RN-CM-006`
-- pregunta «que tasas vivas de esta persona tocan ESTE producto». Los dos
-- caminos empiezan por el producto, y la primaria empieza por la tasa.
CREATE INDEX ix_user_commission_rate_products_producto
    ON user_commission_rate_products (product_id);


COMMENT ON TABLE user_commission_rate_products IS
    'Sobre que productos rige una tasa personalizada. Gemela de product_commission_rates (RN-CM-014, 11-09-2026).';
