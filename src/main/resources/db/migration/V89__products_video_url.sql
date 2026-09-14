-- =============================================================================
-- RF-PM-001 · RN-PM-032 (14-09-2026)
-- UN PRODUCTO PUEDE ENLAZAR UN VIDEO, Y EL ENLACE SALE EN LAS CUATRO LECTURAS.
--
-- Por decision del responsable del proyecto, un producto declara LA DIRECCION
-- de un video que lo presenta (`requirements/pm.md` §5.2.8). Es `icon` otra
-- vez: el sistema guarda un nombre y no una imagen; aqui guarda una direccion
-- y no un archivo. Y va un paso mas alla: TAMPOCO LA CONSULTA. Nada comprueba
-- que el enlace exista, nada lo descarga, nada lo incrusta. Se comprueba la
-- forma y nada mas.
--
-- AL REVES QUE `purchase_price`, ESTA COLUMNA SE PUBLICA: la devuelven las
-- cuatro lecturas —catalogo, detalle, oferta y el hotlink SIN TOKEN—, porque
-- es material de venta y no un costo. Lo que se acepta al publicar sin token
-- una direccion que alguien escribio esta en `pm.md` §5.2.8.
--
-- Opcional, en los DOS tipos —sin la condicion cruzada del icono— y sin
-- condicionar la activacion. El nulo significa «no tiene video».
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. La columna. `varchar(500)` y no `text`: un enlace que no cabe en
-- quinientos caracteres no es uno que nadie vaya a escribir a mano, y
-- ENSANCHAR un varchar es un ALTER de solo metadatos en PostgreSQL — es
-- estrechar lo que reescribe. SIN DEFAULT: la cadena vacia no es un estado; o
-- hay enlace o hay NULL. Y sin relleno: ningun producto de hoy tiene video, y
-- el nulo lo dice.
-- -----------------------------------------------------------------------------

ALTER TABLE products ADD COLUMN video_url varchar(500);

-- -----------------------------------------------------------------------------
-- 2. La forma, y nada mas: esquema `http` o `https` y ningun espacio. La rama
-- `IS NULL` va DELANTE Y EXPLICITA, como en la vigencia y en el icono: un
-- CHECK que evalua a NULL acepta la fila, y el permiso debe ser deliberado.
-- Que el enlace resuelva a algo NO es cosa del esquema ni del dominio.
-- -----------------------------------------------------------------------------

ALTER TABLE products
    ADD CONSTRAINT ck_products_video_url_format
    CHECK (video_url IS NULL OR video_url ~ '^https?://[^[:space:]]+$');

-- -----------------------------------------------------------------------------
-- 3. El comentario dice que es, que significa su nulo y DONDE SI SE VE — que
-- es lo que un lector del esquema no puede deducir de la columna, y lo que la
-- distingue de `purchase_price`, su vecina opcional.
-- -----------------------------------------------------------------------------

COMMENT ON COLUMN products.video_url IS
    'La DIRECCION de un video que presenta el producto, no el video '
    '(RN-PM-032). Se comprueba la forma —http(s), sin espacios— y NADA MAS: '
    'el sistema no sigue el enlace. NULL significa "no tiene video". SE '
    'PUBLICA en las cuatro lecturas, hotlink sin token incluido: es material '
    'de venta, no un costo — al reves que purchase_price.';
