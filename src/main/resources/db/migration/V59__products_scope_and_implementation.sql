-- =============================================================================
-- RF-PM-001 · RN-PM-019, RN-PM-020
-- UN PRODUCTO DICE HASTA DONDE SE MUESTRA Y QUIEN APLICA LO QUE OTORGA.
--
-- Dos columnas obligatorias, EN LOS DOS TIPOS y SIN VALOR POR OMISION. Ahi se
-- apartan de todo lo demas que hay en esta tabla: `ck_products_type_target` y
-- `ck_products_icon_solo_upgrade` obligan o prohiben SEGUN EL TIPO, y estas dos
-- no distinguen — un bot tambien se muestra en algun sitio y tambien se entrega
-- de alguna forma.
--
-- EL ALCANCE ES UNA ESCALA, NO UN REPARTO. `HOTLINKS` INCLUYE la tienda, de
-- modo que no existe forma de publicar algo SOLO en hotlinks. Se acepta a
-- conciencia (`requirements/pm.md` §5.2.2), y el dia que ese caso exista lo que
-- entra es un TERCER VALOR — cambiarle el significado a los dos que hay
-- reescribiria en silencio cada fila ya declarada.
--
-- LA IMPLEMENTACION ES LO PRIMERO DE ESTE CATALOGO QUE GOBIERNA A OTRO MODULO:
-- `RN-MV-020` deja de conceder la membresia en toda venta confirmada y la
-- concede SOLO cuando el producto es `AUTOMATICA`.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Las columnas, nulas de momento.
--
-- EN TRES PASOS Y NO EN UNO: una columna `NOT NULL` no se puede anadir de golpe
-- a una tabla con filas sin darle un `DEFAULT`, y EL `DEFAULT` ES JUSTO LO QUE
-- NO QUEREMOS — ver el paso 3.
-- -----------------------------------------------------------------------------

ALTER TABLE products ADD COLUMN scope          varchar(20) NULL;
ALTER TABLE products ADD COLUMN implementation varchar(20) NULL;

-- -----------------------------------------------------------------------------
-- 2. El relleno: `TIENDA` y `MANUAL`.
--
-- ES UNA DECISION Y NO UNA DEDUCCION, igual que el `FREE` de `V53`: bajo el
-- modelo anterior estos productos NO TENIAN ni alcance ni implementacion, de
-- modo que no hay nada de donde derivarlos.
--
-- `TIENDA` es el alcance MAS CORTO y CONSERVA EXACTAMENTE LA OFERTA DE HOY: lo
-- que se veia en la tienda se sigue viendo, y nada aparece de golpe en un canal
-- que todavia no existe.
--
-- `MANUAL` ES LA QUE NO ENTREGA SOLA, y esa es la mitad que hay que leer
-- despacio. Con `AUTOMATICA`, el dia que `RF-MV-003` se construya TODO PRODUCTO
-- ANTERIOR A ESTA MIGRACION ENTREGARIA SOLO — membresias concedidas por
-- productos que nadie reviso, con el cobro hecho y sin que ninguna decision lo
-- hubiera dicho. El defecto NO FALLA: ENTREGA. Con `MANUAL`, lo peor que pasa
-- es que alguien autorice a mano algo que podria haberse aplicado solo, y eso
-- se nota, se corrige con `RF-PM-004` y no deja nada mal concedido detras.
--
-- `updated_at` NO se toca, por lo mismo que en `V38` y `V43`: esa marca dice
-- cuando cambio el producto como hecho de negocio, y rellenar una columna nueva
-- no lo es. Moverla haria que la auditoria de `RF-SP-011` mostrara una
-- modificacion que nadie hizo.
-- -----------------------------------------------------------------------------

UPDATE products SET scope          = 'TIENDA' WHERE scope          IS NULL;
UPDATE products SET implementation = 'MANUAL' WHERE implementation IS NULL;

-- -----------------------------------------------------------------------------
-- 3. Obligatorias, y SIN `DEFAULT`.
--
-- La diferencia entre el relleno de arriba y un `DEFAULT` no es de matiz: el
-- relleno lo escribe LA MIGRACION, UNA VEZ, sobre lo que ya existe; un
-- `DEFAULT` lo escribiria LA COLUMNA, SIEMPRE, sobre todo lo que venga — y con
-- el, un alta que olvidara declarar el alcance se guardaria sin error y sin que
-- nadie pudiera distinguirla de una que lo declaro.
--
-- `status` SI lleva `DEFAULT` porque una regla lo exige (`RN-PM-012`: todo
-- producto nace inactivo). Aqui ninguna regla dice cual es el valor natural, y
-- ese es precisamente el motivo por el que el valor se declara.
-- -----------------------------------------------------------------------------

ALTER TABLE products ALTER COLUMN scope          SET NOT NULL;
ALTER TABLE products ALTER COLUMN implementation SET NOT NULL;

-- -----------------------------------------------------------------------------
-- 4. Los dominios cerrados.
--
-- `varchar` con `CHECK` y no `boolean`, por lo mismo que `status`: el alcance
-- YA es candidato a crecer —un tercer valor que publique solo en hotlinks es
-- previsible— y anadirlo a un `varchar` es una migracion, mientras que
-- convertir un `boolean` en tres estados es reescribir todo lo que lo consulta.
--
-- Estos dos CHECK no necesitan la precaucion de la rama `IS NULL` explicita que
-- llevan `ck_products_validity_positive` y `ck_products_icon_format`: la
-- columna es `NOT NULL`, de modo que `scope IN (...)` no puede evaluar a NULL y
-- no hay ninguna fila que pudiera colarse por ahi.
-- -----------------------------------------------------------------------------

ALTER TABLE products
    ADD CONSTRAINT ck_products_scope
        CHECK (scope IN ('TIENDA', 'HOTLINKS'));

ALTER TABLE products
    ADD CONSTRAINT ck_products_implementation
        CHECK (implementation IN ('AUTOMATICA', 'MANUAL'));

COMMENT ON COLUMN products.scope IS
    'Hasta donde se muestra el producto. ACUMULATIVO: HOTLINKS incluye TIENDA, '
    'de modo que no existe un alcance que excluya la tienda (RN-PM-019).';

COMMENT ON COLUMN products.implementation IS
    'Si lo comprado se aplica solo (AUTOMATICA) o espera a que un funcionario '
    'lo autorice (MANUAL). Gobierna que hace MV al confirmar (RN-PM-020, '
    'RN-MV-020).';
