-- =============================================================================
-- V44 — Nace `academy_images` y las portadas de academia se atan a ella
-- (RF-AC-006, RN-AC-004, ac.md §8.8 y §8.9, 25-09-2026).
--
-- `product_images` COLUMNA A COLUMNA, en el módulo que la escribe (ac.md
-- §5.2.3): un INSERT de AC en una tabla de PM es lo que modules.md §7 prohíbe.
-- Las dos restricciones se repiten a propósito: son dos tablas, y el tope y la
-- lista de tipos viven en el esquema de cada una Y en `shared/images`, de modo
-- que subir el tope es una constante y dos restricciones.
--
-- LAS SEIS RESTRICCIONES DE LAS TRES COLUMNAS LLEGAN AQUÍ, y no en V18, V21 y
-- V23: la tabla no existía, y las columnas nacieron nulables para que la forma
-- de las respuestas fuera la definitiva desde el primer día. Las tres tablas
-- están vacías de portadas al aplicar —nada escribía `cover_image_id` hasta
-- hoy—, de modo que se añaden sin relleno.
--
-- SIN `ON DELETE`: la imagen se borra DESPUÉS de que la columna deje de
-- señalarla, nunca al revés.
--
-- Los planes la numeraban V28; el número se asigna al construir.
-- =============================================================================

CREATE TABLE academy_images (
    id           uuid        PRIMARY KEY,
    content_type varchar(30) NOT NULL,
    content      bytea       NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_academy_images_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_academy_images_size         CHECK (octet_length(content) BETWEEN 1 AND 5242880)
);

ALTER TABLE course_categories
    ADD CONSTRAINT fk_course_categories_cover_image FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_course_categories_cover_image UNIQUE (cover_image_id);

ALTER TABLE courses
    ADD CONSTRAINT fk_courses_cover_image FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_courses_cover_image UNIQUE (cover_image_id);

ALTER TABLE course_modules
    ADD CONSTRAINT fk_course_modules_cover_image FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_course_modules_cover_image UNIQUE (cover_image_id);
