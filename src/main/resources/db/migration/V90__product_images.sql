-- =============================================================================
-- RF-PM-014 · RN-PM-033, RN-PM-034 (14-09-2026)
-- LA PORTADA DE UN PRODUCTO: EL PRIMER ARCHIVO QUE EL SISTEMA GUARDA.
--
-- Por decision del responsable del proyecto (`requirements/pm.md` §5.2.9), un
-- producto lleva una imagen de portada y EL SISTEMA GUARDA LOS BYTES. La frase
-- «el sistema no almacena binarios», que `V43` (icono) y `V89` (video)
-- repitieron, deja de ser cierta A PROPOSITO. Lo que sobrevive de aquella
-- frontera es la otra mitad: EL SISTEMA NO INTERPRETA EL CONTENIDO. Guarda lo
-- que recibe —JPEG, PNG o WebP, hasta 5 MB, y el tipo lo deciden los primeros
-- bytes y no la cabecera— y lo devuelve tal cual: ni recorte, ni redimension,
-- ni conversion.
--
-- EN POSTGRESQL, con `bytea`, y no en disco ni en un bucket: mismo volumen,
-- misma copia de seguridad, misma transaccion que el producto. TOAST saca la
-- columna fuera de la fila en cuanto pasa de dos kilobytes, de modo que un
-- SELECT de `products` que no nombre `content` no la lee del disco — y ninguna
-- lectura del catalogo la nombra.
--
-- LA TABLA NO ES UNA ENTIDAD: es el valor de una columna de `products` sacado
-- a una tabla propia porque cinco megas no caben con dignidad en una fila del
-- catalogo. Por eso no lleva `updated_at` ni `deleted_at`: una fila no se
-- modifica nunca. Reemplazar la portada es OTRA fila con otro identificador
-- —para que la direccion publica sea inmutable y se pueda cachear un año— y la
-- anterior SE BORRA FISICAMENTE, en la misma transaccion. No es una baja
-- logica del Art. V.13: se corrige el valor de un campo, y la auditoria de
-- cambios de `products` conserva el antes y el despues del identificador.
--
-- RN-PM-034 —un upgrade siempre tiene portada o icono— NO SE DECLARA AQUI, y
-- podria: hay upgrades anteriores a hoy sin icono, y un CHECK NOT VALID los
-- romperia en su proximo UPDATE —activar, retirar— con un 500 justo donde la
-- regla dice que no pasa nada. Vive en el agregado (`pm.md` §10.3).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. La tabla. `content_type` es el tipo REAL, detectado en los bytes al subir,
-- y es lo que `RF-PM-016` devuelve al servir la imagen: por eso tiene que ser
-- verdad. Solo tres tipos: SVG puede llevar codigo y servido inline desde este
-- origen seria servir codigo de quien lo subio; GIF es una decision de diseño
-- que nadie tomo. `octet_length(content) BETWEEN 1 AND 5242880`: cinco
-- megabytes exactos, y ningun byte de cero — un archivo vacio no es una imagen.
-- Va en el esquema para que ninguna otra ruta pueda meter algo mayor.
--
-- Y lo que NO hay: ningun CHECK sobre la firma de los bytes. Seria posible, y
-- seria la unica linea de SQL del sistema que sabe lo que es una imagen —
-- ese conocimiento vive en un solo sitio, `ImageSignature`.
-- -----------------------------------------------------------------------------

CREATE TABLE product_images (
    id           uuid PRIMARY KEY,
    content_type varchar(30) NOT NULL,
    content      bytea NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_product_images_content_type
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),

    CONSTRAINT ck_product_images_size
        CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);

COMMENT ON TABLE product_images IS
    'Los bytes de la portada de un producto (RN-PM-033). NO es una entidad: '
    'es el valor de products.cover_image_id. Una fila no se modifica; '
    'reemplazar la portada es otra fila y la anterior se borra.';

COMMENT ON COLUMN product_images.content_type IS
    'El tipo REAL, detectado en los primeros bytes al subir — no la cabecera '
    'de la peticion. Es lo que se devuelve al servir la imagen.';

COMMENT ON COLUMN product_images.content IS
    'Los bytes TAL CUAL se subieron: sin recorte, redimension ni conversion. '
    'De 1 byte a 5 MB.';

-- -----------------------------------------------------------------------------
-- 2. La columna en `products`. NULL significa «no tiene portada» — y es el
-- estado de todo producto de hoy: sin relleno, porque no hay ninguna imagen
-- honesta que inventar.
--
-- SIN `ON DELETE`: la fila de la imagen se borra DESPUES de que la columna deje
-- de señalarla, en la misma transaccion, y nunca al reves. Un `ON DELETE SET
-- NULL` dejaria que borrar una imagen quitara una portada sin pasar por
-- RN-PM-034. UNIQUE total: una imagen es portada de UN producto como maximo, y
-- eso es lo que hace seguro borrar la reemplazada — nadie mas la señala. Los
-- nulos no colisionan en un UNIQUE de PostgreSQL, de modo que mil productos
-- sin portada caben.
-- -----------------------------------------------------------------------------

ALTER TABLE products ADD COLUMN cover_image_id uuid;

ALTER TABLE products
    ADD CONSTRAINT fk_products_cover_image
    FOREIGN KEY (cover_image_id) REFERENCES product_images (id);

ALTER TABLE products
    ADD CONSTRAINT uq_products_cover_image UNIQUE (cover_image_id);

COMMENT ON COLUMN products.cover_image_id IS
    'La portada (RN-PM-033): la fila de product_images cuyos bytes se sirven '
    'sin token en /api/v1/product-images/{id}. NULL = no tiene. En un upgrade, '
    'si es NULL el icono es obligatorio (RN-PM-034, en el dominio). Cada '
    'subida estrena identificador y la reemplazada se borra.';
