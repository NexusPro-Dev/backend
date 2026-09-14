-- =============================================================================
-- RF-PM-009 · T-01 — Las reseñas de producto (`requirements/pm.md` §10.4).
--
-- LA SEGUNDA TABLA DE `PM`, Y LA PRIMERA CUYA FILA PERTENECE A UNA PERSONA.
-- Una reseña es una puntuación de uno a cinco y un texto, escrita por quien
-- porta `products:comment` sobre un producto que se puede comprar, y que SOLO
-- SU AUTOR corrige y retira (`RN-PM-027`) — ni la administración.
--
-- `user_id` ES EL AUTOR, NO EL ACTOR (Art. V.7). Sin él la fila no significa
-- nada, igual que `user_commission_rates.user_id` dice de quién es la
-- excepción. Quién corrigió o retiró la reseña sigue viviendo en la auditoría,
-- y coincide con el autor porque la regla lo obliga, no porque la columna lo
-- diga.
--
-- LAS DOS CLAVES FORÁNEAS VAN SIN `ON DELETE`: ni `products` ni `users` se
-- borran físicamente, y declararlo documentaría un caso que no ocurre. La de
-- `users` es la primera de `PM` hacia una persona; cruza a `SP` por el motor,
-- no por el código (D-25): `PM` no lee esa tabla desde Java salvo por el JOIN
-- de la lista pública, que solo le pone nombre al autor.
--
-- EL PROMEDIO NO ESTÁ AQUÍ NI EN `products` (`RN-PM-031`): se cuenta sobre las
-- vivas en cada lectura. Una columna desnormalizada obligaría a mantenerla en
-- tres operaciones, y la que se quedara atrás no fallaría, mentiría.
--
-- SE RETIRA SIN MOTIVO DECLARADO (`RN-PM-029`): es la tercera excepción del
-- Art. V.13, el contenido propio, escrita el 14-09-2026. El registro de
-- eliminación se escribe igual, con la instantánea y un motivo fijo; el
-- esquema de la auditoría no cambia.
-- =============================================================================

CREATE TABLE product_comments (
    id          uuid        PRIMARY KEY,
    product_id  uuid        NOT NULL,
    user_id     uuid        NOT NULL,

    -- `smallint` y no `integer`: el dominio son cinco valores y no va a crecer
    -- a decimales — media estrella sería otra escala, no la misma con más
    -- resolución. El promedio, que sí lleva decimales, no se guarda.
    rating      smallint    NOT NULL,

    -- `text` con CHECK de longitud y no `varchar(1000)`: el límite es una
    -- decisión de producto y puede subir; con `varchar` subirlo es alterar el
    -- tipo de una columna en uso, con CHECK es reemplazar una restricción.
    comment     text        NOT NULL,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    -- Retiro lógico. Sin columna de motivo, y aquí además sin motivo
    -- declarado: ver el encabezado.
    deleted_at  timestamptz NULL,

    CONSTRAINT fk_product_comments_product
        FOREIGN KEY (product_id) REFERENCES products (id),

    CONSTRAINT fk_product_comments_user
        FOREIGN KEY (user_id) REFERENCES users (id),

    -- RN-PM-025. SIN rama `IS NULL`, al revés que la vigencia y el icono del
    -- producto: la columna es NOT NULL, y un CHECK sobre una columna
    -- obligatoria no puede evaluar a NULL. Que nadie la añada «por si acaso».
    CONSTRAINT ck_product_comments_rating
        CHECK (rating BETWEEN 1 AND 5),

    -- RN-PM-025. El `btrim` va DENTRO a propósito: mil espacios no son una
    -- reseña. El valor se guarda ya recortado desde el dominio; el CHECK es la
    -- red, no la regla.
    CONSTRAINT ck_product_comments_comment_length
        CHECK (char_length(btrim(comment)) BETWEEN 1 AND 1000)
);

-- -----------------------------------------------------------------------------
-- RN-PM-026: una reseña por persona y producto ENTRE LAS VIVAS.
--
-- Parcial porque retirada la suya la persona puede escribir otra. Y por
-- parcial NO ADMITE `DEFERRABLE` —eso es propiedad de una restricción, no de
-- un índice—, de modo que la carrera entre dos altas simultáneas del mismo
-- actor muerde en el segundo INSERT y el repositorio la traduce ahí a `409`
-- (hallazgo de `RF-SP-019`).
-- -----------------------------------------------------------------------------

CREATE UNIQUE INDEX uq_product_comments_autor
    ON product_comments (product_id, user_id)
    WHERE deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- No implementa ninguna regla: sostiene la lista pública (`RF-PM-012`) en su
-- orden —de la más reciente a la más antigua, con el identificador como
-- desempate— y el agregado de `RN-PM-031`, que corre por producto sobre este
-- mismo predicado. Parcial porque las retiradas ni se listan ni se suman.
-- -----------------------------------------------------------------------------

CREATE INDEX ix_product_comments_product
    ON product_comments (product_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
