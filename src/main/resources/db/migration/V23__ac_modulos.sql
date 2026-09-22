-- ---------------------------------------------------------------------------
-- V23 — Los MÓDULOS de un curso (RF-AC-022, requirements/ac.md §8.6,
-- 18-09-2026).
--
-- Un módulo es una parte del curso: título, dos descripciones, video de
-- presentación —el «contenido url» de la lista original, ac.md §5.2.1—,
-- orden, estado y portada. NACE DENTRO DEL CURSO Y NO SE MUEVE (RN-AC-019):
-- course_id no tiene mutador, porque el orden, la unicidad del título y la
-- ofrecibilidad del padre están definidos dentro del curso.
--
-- `course_modules` y no `modules`, que en este proyecto nombra otra cosa.
-- SIN ON DELETE: nada se borra físicamente; retirar un curso retira sus
-- módulos con una fila de auditoría cada uno (RN-AC-018).
--
-- `uq_course_modules_title` lleva course_id delante: el título es único
-- DENTRO del curso (RN-AC-001), entre los vivos, y la carrera la muerde el
-- índice. `ix_course_modules_course` es el índice del árbol (ac.md §8.9):
-- sostiene el detalle del curso en su orden y la cuenta de activos; parcial
-- sobre vivos porque los retirados solo los lee el detalle de administración.
-- ---------------------------------------------------------------------------

CREATE TABLE course_modules (
    id                     uuid         PRIMARY KEY,
    course_id              uuid         NOT NULL,
    title                  varchar(150) NOT NULL,
    short_description      varchar(300) NULL,
    long_description       text         NULL,
    presentation_video_url varchar(500) NULL,
    display_order          integer      NOT NULL,
    status                 varchar(20)  NOT NULL DEFAULT 'INACTIVO',
    cover_image_id         uuid         NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    deleted_at             timestamptz  NULL,
    CONSTRAINT fk_course_modules_course        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT ck_course_modules_status        CHECK (status IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_course_modules_display_order CHECK (display_order >= 0),
    CONSTRAINT ck_course_modules_presentation_video_url
        CHECK (presentation_video_url IS NULL OR presentation_video_url ~ '^https?://[^[:space:]]+$')
);

CREATE UNIQUE INDEX uq_course_modules_title
    ON course_modules (course_id, f_unaccent(lower(title)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_course_modules_course
    ON course_modules (course_id, display_order, id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE course_modules IS
    'Las partes de un curso (AC, RN-AC-019): nacen en un curso y no se mueven. Nacen INACTIVO y se activan con una leccion activa (RN-AC-009); se ofrecen dentro de un curso ofrecido si estan ACTIVO, vivos y con una leccion activa con contenido (RN-AC-015). Titulo unico dentro del curso entre los vivos (RN-AC-001).';

COMMENT ON COLUMN course_modules.presentation_video_url IS
    'El video de presentacion del modulo (RN-AC-005; ac.md §5.2.1): un enlace que el sistema comprueba en su forma y no sigue. Opcional.';

COMMENT ON COLUMN course_modules.cover_image_id IS
    'La portada del modulo (RN-AC-004): la fila de academy_images cuyos bytes se sirven sin token. NULL = no tiene. La clave foranea y la unicidad las anade la migracion que crea academy_images (RF-AC-006).';
