-- =============================================================================
-- V106 — Varios instructores por curso (RF-AC-062 · T-01; requirements/ac.md
-- v0.28.0 §5.2.19, §8.2 y §8.15, RN-AC-033; 13-10-2026).
--
--   * Nace course_instructors: la lista de instructores del curso, uno o más,
--     como live_session_instructors (V105). Se rellena con el instructor que
--     cada curso tenía en courses.instructor_id.
--   * courses pierde instructor_id, con su clave foránea y su índice.
--
-- Que la lista no quede vacía no cabe en el esquema: lo exige el dominio
-- (RN-AC-033). Que cada instructor porte courses:teach, tampoco (RN-AC-006).
--
-- GUARDA: ningún curso queda sin instructor.
-- =============================================================================

CREATE TABLE course_instructors (
    course_id   uuid        NOT NULL,
    user_id     uuid        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_course_instructors PRIMARY KEY (course_id, user_id),
    CONSTRAINT fk_course_instructors_course
        FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_course_instructors_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_course_instructors_user ON course_instructors (user_id);

INSERT INTO course_instructors (course_id, user_id)
SELECT id, instructor_id FROM courses;

DROP INDEX ix_courses_instructor;
ALTER TABLE courses
    DROP CONSTRAINT fk_courses_instructor,
    DROP COLUMN instructor_id;

COMMENT ON TABLE course_instructors IS
    'Los instructores de un curso (RN-AC-033): uno o más, que portan courses:teach al asignarse (RN-AC-006). Corregir la lista borra y escribe filas.';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM courses c
                WHERE NOT EXISTS (SELECT 1 FROM course_instructors i WHERE i.course_id = c.id)) THEN
        RAISE EXCEPTION 'V106: todo curso conserva al menos un instructor';
    END IF;
END $$;
