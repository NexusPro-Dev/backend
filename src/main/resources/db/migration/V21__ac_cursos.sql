-- ---------------------------------------------------------------------------
-- V21 — Los CURSOS del módulo AC (RF-AC-008, requirements/ac.md §8.2,
-- 18-09-2026).
--
-- Un curso es lo que la membresía abre: título, instructor, dificultad, dos
-- descripciones, video de introducción, orden, estado y portada. SIN código
-- (ac.md §5.2.6): no se teclea en ninguna venta ni se imprime en ningún
-- comprobante, y el día que un curso se venda suelto el código lo tendrá el
-- producto que lo venda.
--
-- `fk_courses_instructor` se declara hacia `users` SIN ON DELETE: la persona
-- no se borra físicamente nunca (RF-SP-029), y la frontera de modules.md §7 es
-- la del código, no la del esquema. Que el instructor PORTE `courses:teach`
-- (RN-AC-006) no cabe en una clave: vive en el caso de uso, que lo pregunta a
-- SP por PermissionHolderLookup al asignar y solo al asignar.
--
-- `status` con DEFAULT 'INACTIVO' y no 'ACTIVO' como `products`: aquel es una
-- herencia de antes de RN-PM-012; aquí el esquema dice lo mismo que RN-AC-008
-- (nace inactivo y se publica cuando tiene con qué).
--
-- `cover_image_id` nace nulable y SIN clave foránea, como en `course_categories`:
-- `academy_images` la crea RF-AC-006, cuya migración añade la FK y la unicidad
-- de las tres tablas que la señalan.
--
-- `ck_courses_intro_video_url` es la expresión de RN-PM-032 (RN-AC-005 por
-- extensión) con la rama IS NULL delante y explícita: el video es opcional y
-- el sistema comprueba su forma y NO lo sigue.
-- ---------------------------------------------------------------------------

CREATE TABLE courses (
    id                uuid         PRIMARY KEY,
    title             varchar(150) NOT NULL,
    instructor_id     uuid         NOT NULL,
    difficulty        varchar(20)  NOT NULL,
    short_description varchar(300) NULL,
    long_description  text         NULL,
    intro_video_url   varchar(500) NULL,
    display_order     integer      NOT NULL,
    status            varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    cover_image_id    uuid         NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    deleted_at        timestamptz  NULL,
    CONSTRAINT fk_courses_instructor      FOREIGN KEY (instructor_id) REFERENCES users (id),
    CONSTRAINT ck_courses_difficulty      CHECK (difficulty IN ('PRINCIPIANTE', 'INTERMEDIO', 'AVANZADO')),
    CONSTRAINT ck_courses_status          CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_courses_display_order   CHECK (display_order >= 0),
    CONSTRAINT ck_courses_intro_video_url CHECK (intro_video_url IS NULL OR intro_video_url ~ '^https?://[^[:space:]]+$')
);

-- Único entre los VIVOS, sin distinguir mayúsculas ni acentos (RN-AC-001):
-- un retirado libera el título. Parcial, y por parcial no admite DEFERRABLE:
-- muerde en el INSERT y el repositorio lo traduce al mismo 409 que la
-- comprobación previa.
CREATE UNIQUE INDEX uq_courses_title
    ON courses (f_unaccent(lower(title)))
    WHERE deleted_at IS NULL;

-- Sostiene el filtro por instructor de RF-AC-009. El orden por display_order
-- no lleva índice por lo mismo que en la categoría: son decenas de filas.
CREATE INDEX ix_courses_instructor ON courses (instructor_id);

COMMENT ON TABLE courses IS
    'Lo que la membresia abre (AC, RN-AC-005 a RN-AC-009, RN-AC-015). SIN codigo (ac.md §5.2.6). Nace INACTIVO y se activa con descripciones y un modulo activo (RN-AC-009); se ofrece cuando ademas tiene membresias y un modulo con leccion activa con contenido, calculado en cada lectura y nunca guardado (RN-AC-015). Retirarlo arrastra modulos y lecciones (RN-AC-018).';

COMMENT ON COLUMN courses.instructor_id IS
    'La persona de SP que lo ensena (RN-AC-006): existe, no retirada y portando courses:teach AL ASIGNAR. Que lo pierda despues no toca el curso. Se resuelve por puertos al escribir y por JOIN users al leer (RF-PM-012 como precedente).';

COMMENT ON COLUMN courses.difficulty IS
    'PRINCIPIANTE, INTERMEDIO o AVANZADO (RN-AC-007): una etiqueta para que el alumno elija, no un orden ni una cadena.';

COMMENT ON COLUMN courses.status IS
    'ACTIVO o INACTIVO (RN-AC-008): lo que alguien decidio. Activar exige las dos descripciones y un modulo activo (RN-AC-009); lo que despues se vacia no desactiva nada, deja de ofrecerse (RN-AC-015).';

COMMENT ON COLUMN courses.cover_image_id IS
    'La portada del curso (RN-AC-004): la fila de academy_images cuyos bytes se sirven sin token. NULL = no tiene, y el frontend pinta el icono por omision. La clave foranea y la unicidad las anade la migracion que crea academy_images (RF-AC-006).';
