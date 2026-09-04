-- =============================================================================
-- RF-MV-001 · T-01 a T-03 — LAS CUATRO TABLAS QUE FUNDAN EL MODULO MV.
--
--   movement_types    el catalogo de tipos. Se crea Y SE SIEMBRA aqui mismo.
--   payment_methods   con que se paga. Sembrada con dos filas.
--   movements         la cabecera: el hecho economico ya sucedido.
--   movement_details  las lineas, con LO QUE SE COPIO del catalogo.
--
-- Su forma la fija requirements/mv.md §7 y no se reescribe aqui: esta migracion
-- la aplica y anade lo que aquel documento no puede tener, que es el numero.
--
-- POR QUE ESTA MIGRACION SE LLAMA `V54` Y NO `V53`, que es lo que plan.md §2 y
-- tasks.md `T-01` a `T-03` dejaron escrito el 02-09-2026. Es la tercera vez que
-- este numero se mueve, y siempre por el mismo motivo: EL NUMERO LO TOMA QUIEN
-- SE APLICA PRIMERO (modelo-datos.md §1). El 03-09-2026 se fusiono
-- `V53__products_source_membership.sql` —`RF-PM-001`, el origen del upgrade—,
-- de modo que el `53` dejo de estar libre antes de que estas tablas existieran.
-- Reservar un numero por adelantado y aplicarlo despues es justo lo que Flyway
-- no perdona: una migracion con un numero por debajo del ultimo aplicado se
-- queda fuera, sin error y sin aviso.
--
-- SE CREAN Y SE SIEMBRAN LOS DOS CATALOGOS EN LA MISMA MIGRACION, y es el unico
-- punto de orden que este archivo decide por su cuenta. Una tabla de tipos
-- vacia deja el modulo sin poder registrar absolutamente nada, y separar la
-- siembra permitiria desplegar ese estado — un esquema completo que no admite
-- ni una venta.
--
-- ESTA MIGRACION NO EMITE AUDITORIA, igual que V3, V40, V45 y V51, y por el
-- mismo motivo: ninguno de los dos catalogos se administra por API
-- (mv.md §5.3, `RN-MV-017`). Lo que no cambia por API no tiene linea de tiempo
-- que reconstruir, y su unico historial posible es flyway_schema_history. Es la
-- diferencia con `V15`, que si audita: las monedas SI se editan.
--
-- IDENTIFICADORES LITERALES, no generados (Art. V.11), por lo mismo que V3,
-- V15, V40, V45 y V51: deben ser iguales en todos los entornos para que las
-- pruebas los referencien por constante en lugar de consultarlos. Son UUID v7
-- con marca de tiempo 2026-09-04T00:00:00Z (01a061ba-3400), version 7 y
-- variante RFC 9562, y continuan la serie de sufijos por modulo con `5e7ad7`,
-- que ya es de MV desde V51.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. `movement_types` — el catalogo de tipos
--
-- SIN `updated_at` Y SIN `deleted_at` (`RN-MV-017`): el catalogo no se edita por
-- API y no se borra. El caso de uso decide segun el tipo, y un tipo que
-- desapareciera dejaria movimientos apuntando a nada.
--
-- ES UNA TABLA Y NO UNA COLUMNA `type` CON UN CHECK, y este proyecto ya pago dos
-- veces por esa forma: el catalogo de `event_type` de `audit_security_log` es un
-- CHECK, y anadirle un valor costo dos migraciones con DROP CONSTRAINT sobre una
-- tabla en uso. Los tipos de las etapas 2 a 6 —deposito, compra de puntos,
-- comision— entran entonces COMO FILAS.
--
-- NO LLEVA BANDERAS DE COMPORTAMIENTO. El diseno anterior tenia
-- `requires_product`, `affects_cash` y `generates_commission`, y CON UN SOLO
-- TIPO LAS TRES SERIAN CONSTANTES: una columna que no distingue nada no dice
-- nada. Entran cuando entre el segundo tipo, que es cuando empiezan a
-- significar algo.
-- -----------------------------------------------------------------------------
CREATE TABLE movement_types (
    id         uuid         PRIMARY KEY,
    code       varchar(50)  NOT NULL,
    name       varchar(100) NOT NULL,
    prefix     varchar(6)   NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_movement_types_code UNIQUE (code),

    -- Mismo formato que `roles`, `memberships` y `products`.
    CONSTRAINT ck_movement_types_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),

    -- El prefijo viaja en el codigo del comprobante (`RN-MV-016`), que se dicta
    -- por telefono: se acota al mismo alfabeto por el mismo motivo.
    CONSTRAINT ck_movement_types_prefix CHECK (prefix ~ '^[A-Z][A-Z0-9]*$')
);

COMMENT ON TABLE movement_types IS
    'Catalogo de tipos de movimiento. Inmutable por API (RN-MV-017). Hoy una sola fila: VENTA.';

COMMENT ON COLUMN movement_types.prefix IS
    'Prefijo del codigo de comprobante (RN-MV-016): VTA-20260904-K7M2QX.';

INSERT INTO movement_types (id, code, name, prefix) VALUES
('01a061ba-3400-7001-9c4f-5e7ad7000011', 'VENTA', 'Venta', 'VTA');


-- -----------------------------------------------------------------------------
-- 2. `payment_methods` — con que se paga
--
-- Se siembra por migracion y NO SE ADMINISTRA POR API todavia (mv.md §5.3). Lo
-- minimo para que una venta pueda decir con que se pago.
--
-- DOS FILAS Y NO TRES. `PUNTOS` es de la etapa 3 del modulo y sembrarlo hoy
-- ofreceria un metodo con el que no se puede pagar: la venta lo aceptaria, no
-- habria de donde descontar, y el fallo aparecerian mucho despues.
--
-- `is_active` EXISTE AUNQUE NADIE PUEDA CAMBIARLO TODAVIA, y es lo que hace
-- verificable `RN-MV-018`: un metodo desactivado no invalida lo que ya se pago
-- con el —las ventas viejas lo siguen referenciando— pero no sirve para vender
-- hoy. Sin la columna, retirar un metodo obligaria a borrar la fila, que es
-- exactamente lo que esa regla prohibe.
-- -----------------------------------------------------------------------------
CREATE TABLE payment_methods (
    id         uuid         PRIMARY KEY,
    code       varchar(50)  NOT NULL,
    name       varchar(100) NOT NULL,
    is_active  boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_methods_code UNIQUE (code),
    CONSTRAINT ck_payment_methods_code CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

COMMENT ON TABLE payment_methods IS
    'Catalogo de metodos de pago. Sembrado por migracion; sin API de administracion todavia.';

COMMENT ON COLUMN payment_methods.is_active IS
    'RN-MV-018: un metodo desactivado no invalida lo ya pagado con el, pero no sirve para vender hoy.';

INSERT INTO payment_methods (id, code, name) VALUES
('01a061ba-3400-7002-9c4f-5e7ad7000021', 'EFECTIVO',      'Efectivo'),
('01a061ba-3400-7003-9c4f-5e7ad7000022', 'TRANSFERENCIA', 'Transferencia bancaria');


-- -----------------------------------------------------------------------------
-- 3. `movements` — la cabecera
--
-- SIN `updated_at` Y SIN `deleted_at` (`RN-MV-001`). ES LA UNICA TABLA DEL
-- SISTEMA QUE NO LOS LLEVA, y es deliberado: aqui no hay nada que actualizar y
-- nada que retirar. Una venta no se edita y no se borra; solo avanza su
-- `status`. La consecuencia practica es que el gestor de auditoria de la
-- aplicacion NO PUEDE TRATAR ESTA TABLA COMO A LAS DEMAS.
--
-- `occurred_at` Y `created_at` NO SON LO MISMO, y separarlas cuesta una columna.
-- Coinciden casi siempre y no tienen por que: un funcionario registra hoy la
-- venta que se cerro el sabado. De `occurred_at` sale ademas EL DIA DEL CODIGO
-- (`RN-MV-016`), de modo que confundirlas haria que una venta de ayer llevara la
-- fecha de hoy en el papel que se le entrega al cliente.
--
-- `discount_amount` EXISTE Y HOY VALE SIEMPRE CERO. Por decision del responsable
-- del proyecto del 02-09-2026 no hay descuentos todavia. La columna se declara
-- ahora —con su CHECK de coherencia— para que el dia que lleguen NO HAYA QUE
-- TOCAR NI UNA FILA DE LO YA VENDIDO.
--
-- `reference_id` SE RESERVA SIN FK Y SIN CHECK. Queda disponible para que otro
-- registro —una cotizacion, hoy sin tabla ni requerimiento propio— apunte a la
-- venta de la que salio. Sin regla de negocio y sin la tabla a la que
-- referenciaria, declararle una FK hoy seria inventar a que apunta.
--
-- NO HAY COLUMNA DE IMPUESTOS, y no es un olvido: separar base e impuesto exige
-- decidir con que tasa se recalcula lo ya vendido, y esa decision llega con la
-- factura fiscal (mv.md §1.5).
-- -----------------------------------------------------------------------------
CREATE TABLE movements (
    id                uuid           PRIMARY KEY,
    movement_type_id  uuid           NOT NULL,
    client_id         uuid           NOT NULL,
    seller_id         uuid           NOT NULL,
    payment_method_id uuid           NOT NULL,
    currency_id       uuid           NOT NULL,
    code              varchar(30)    NOT NULL,
    status            varchar(20)    NOT NULL,
    total_amount      numeric(14, 2) NOT NULL,
    discount_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    payable_amount    numeric(14, 2) NOT NULL,
    occurred_at       timestamptz    NOT NULL,
    confirmed_at      timestamptz    NULL,
    created_at        timestamptz    NOT NULL DEFAULT now(),
    reference_id      uuid           NULL,

    CONSTRAINT fk_movements_type
        FOREIGN KEY (movement_type_id) REFERENCES movement_types (id) ON DELETE RESTRICT,

    -- ON DELETE RESTRICT en las dos personas. `users` tiene borrado LOGICO
    -- (`deleted_at`), de modo que esta restriccion no se ejerce en la practica
    -- — y esta para que un DELETE fisico hecho a mano no se lleve por delante
    -- la atribucion de una venta.
    CONSTRAINT fk_movements_client
        FOREIGN KEY (client_id) REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT fk_movements_seller
        FOREIGN KEY (seller_id) REFERENCES users (id) ON DELETE RESTRICT,

    CONSTRAINT fk_movements_payment_method
        FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id) ON DELETE RESTRICT,

    CONSTRAINT fk_movements_currency
        FOREIGN KEY (currency_id) REFERENCES currencies (id) ON DELETE RESTRICT,

    -- El comprobante es unico. Dos peticiones simultaneas burlan cualquier
    -- comprobacion previa que hiciera la aplicacion: sin este indice, una
    -- colision del aleatorio produciria DOS COMPROBANTES IGUALES sin que nada
    -- avisara. Es ademas lo que hace posible el reintento acotado del caso de
    -- uso, que necesita que el motor rechace para saber que reintentar.
    CONSTRAINT uq_movements_code UNIQUE (code),

    -- `RN-MV-005`: el dominio de estados, no la transicion. Que de CONFIRMADA
    -- no se salga es del caso de uso; que no exista un quinto estado, del
    -- esquema.
    CONSTRAINT ck_movements_status
        CHECK (status IN ('PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'ANULADA')),

    -- `RN-MV-013`. Cruza tres columnas de la misma fila, que es exactamente lo
    -- que un CHECK sabe hacer.
    CONSTRAINT ck_movements_payable
        CHECK (payable_amount = total_amount - discount_amount),

    -- Una venta negativa es un retiro disfrazado, y los retiros son la etapa 6.
    CONSTRAINT ck_movements_amounts
        CHECK (total_amount >= 0 AND discount_amount >= 0 AND payable_amount >= 0),

    -- SI Y SOLO SI. Sin esta restriccion, una venta confirmada sin fecha o una
    -- pendiente con fecha son estados que el codigo puede escribir y nadie
    -- detecta. No la ejercita `RF-MV-001` —aqui toda venta nace pendiente y con
    -- la fecha vacia— y se declara igual: la coherencia entre una fecha y un
    -- estado es del esquema, y anadirla despues obligaria a comprobar antes que
    -- ninguna fila la incumpla ya.
    CONSTRAINT ck_movements_confirmed
        CHECK ((status = 'CONFIRMADA') = (confirmed_at IS NOT NULL))
);

COMMENT ON TABLE movements IS
    'El libro de hechos economicos. NO lleva updated_at ni deleted_at (RN-MV-001): una venta no se edita y no se borra.';

COMMENT ON COLUMN movements.seller_id IS
    'RN-MV-003: el vendedor sale del cliente y SE CONGELA. Reasignar la cartera manana no cambia a quien se atribuyo esto.';

COMMENT ON COLUMN movements.occurred_at IS
    'Cuando ocurrio la venta. De aqui sale el dia del codigo (RN-MV-016), no de created_at.';

COMMENT ON COLUMN movements.reference_id IS
    'Columna RESERVADA, sin FK y sin regla que la gobierne. Pendiente de definir (mv.md §7.1).';

-- Es el filtro de `RF-MV-008` —las ventas propias—, la unica lectura de la que
-- hoy se sabe la forma. El resto de indices se anadira cuando `RF-MV-006`
-- decida por que se lista: indexar por adelantado lo que nadie consulta cuesta
-- en cada INSERT y no ahorra en ninguna consulta.
CREATE INDEX idx_movements_client ON movements (client_id);


-- -----------------------------------------------------------------------------
-- 4. `movement_details` — las lineas
--
-- `unit_price` Y `validity_days` SON COPIAS, Y AHI ESTA TODA LA RAZON DE SER DE
-- ESTA TABLA (`RN-MV-002`). `RF-PM-004` corrige el precio de un producto y
-- `RN-PM-015` declara su vigencia en dias: leerlas del catalogo al mostrar una
-- venta de hace un ano REESCRIBIRIA LO QUE ALGUIEN PAGO Y LO QUE COMPRO.
--
-- `line_amount` SE GUARDA AUNQUE SEA `quantity * unit_price`, por el mismo
-- motivo que el total en la cabecera: es el numero que se imprimio. Recalcular
-- al leer hace que un cambio de redondeo reescriba comprobantes ya entregados.
--
-- LA MEMBRESIA DESTINO NO SE COPIA. `RF-PM-004` rechaza cambiarla, de modo que
-- leerla del producto dentro de tres anos da exactamente el mismo valor, y
-- `RN-PM-010` garantiza que el producto no desaparece nunca. Copiarla solo
-- anadiria un sitio donde el dato pudiera discrepar de si mismo. Es el criterio
-- del modulo: SE COPIA LO QUE PUEDE CAMBIAR; LO INMUTABLE SE REFERENCIA.
-- -----------------------------------------------------------------------------
CREATE TABLE movement_details (
    id            uuid           PRIMARY KEY,
    movement_id   uuid           NOT NULL,
    product_id    uuid           NOT NULL,
    quantity      integer        NOT NULL,
    unit_price    numeric(14, 2) NOT NULL,
    line_amount   numeric(14, 2) NOT NULL,
    validity_days integer        NULL,

    -- ON DELETE CASCADE, que aqui NO SIGNIFICA NADA porque nada borra ventas
    -- (`RN-MV-001`): esta para que el esquema no admita lineas huerfanas.
    CONSTRAINT fk_movement_details_movement
        FOREIGN KEY (movement_id) REFERENCES movements (id) ON DELETE CASCADE,

    CONSTRAINT fk_movement_details_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE RESTRICT,

    -- `RN-MV-011`: el mismo producto no se repite. La aplicacion tambien lo
    -- comprueba —`VAL-006`, mirando la peticion, para no gastar la resolucion
    -- de la oferta en una peticion ya mal formada—, y esta es la que lo
    -- garantiza.
    CONSTRAINT uq_movement_details_producto UNIQUE (movement_id, product_id),

    -- Una linea de cero unidades no es una linea.
    CONSTRAINT ck_movement_details_quantity CHECK (quantity > 0),

    -- LA RAMA NULA VA DELANTE Y EXPLICITA, por lo mismo que en
    -- `ck_products_icon_solo_upgrade`: un CHECK que evalua a NULL ACEPTA la
    -- fila. Escrito solo como `validity_days > 0`, una vigencia nula —que es
    -- legitima y significa «no caduca»— pasaria por la razon equivocada, y el
    -- dia que alguien invirtiera la condicion nadie se enteraria.
    CONSTRAINT ck_movement_details_validity
        CHECK (validity_days IS NULL OR validity_days > 0)
);

COMMENT ON TABLE movement_details IS
    'Las lineas de la venta, con LO QUE SE COPIO del catalogo (RN-MV-002).';

COMMENT ON COLUMN movement_details.unit_price IS
    'COPIA del precio del catalogo en el momento de la venta. No se relee nunca (RN-MV-002).';

COMMENT ON COLUMN movement_details.validity_days IS
    'COPIA de la vigencia en dias. NULL significa que lo adquirido no caduca (RN-PM-015).';

-- Se recorre siempre entero al leer una venta: no hay ninguna lectura de una
-- linea suelta, y no la habra — una linea sin su cabecera no significa nada.
CREATE INDEX idx_movement_details_movement ON movement_details (movement_id);
