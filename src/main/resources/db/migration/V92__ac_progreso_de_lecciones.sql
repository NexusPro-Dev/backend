-- =============================================================================
-- V92 — El progreso del alumno por lección (RF-AC-039 · T-01; requirements/ac.md
-- v0.21.0 §5.2.14 y §8.7.1, RN-AC-021 a RN-AC-024; security.md §4.4 v0.127.0;
-- 09-10-2026).
--
-- Una fila por persona y lección: la mayor posición alcanzada de un video, la
-- fecha en que se completó y la primera y la última apertura. Se escribe con
-- INSERT … ON CONFLICT DO UPDATE y el máximo lo calcula el motor (GREATEST),
-- sin leer antes: dos reportes simultáneos no se pisan.
--
-- SIN course_id: sale de la lección por su módulo, que no se mueven
-- (RN-AC-019). SIN deleted_at: el progreso no se retira (RN-AC-023). SIN
-- ON DELETE en las claves, como todas las de AC.
--
-- Y los tres permisos del progreso (RN-SEG-014, sembrados por tipo de rol,
-- RN-SEG-015):
--
--   * lessons:track-progress a TODO ROL QUE PORTE lessons:learn, como V47.
--   * courses:list-progress y courses:read-progress a SUPERADMIN, ADMIN, todo
--     rol de tipo VENDEDOR y todo rol que porte courses:teach.
--
-- GUARDAS: 219 en el catálogo / SUPERADMIN 219 / ADMIN 217 / ningún rol con
-- lessons:learn sin lessons:track-progress / cero filas que rompan RN-SEG-003.
-- =============================================================================

CREATE TABLE lesson_progress (
    user_id          uuid        NOT NULL,
    lesson_id        uuid        NOT NULL,
    watched_seconds  integer     NOT NULL DEFAULT 0,
    completed_at     timestamptz NULL,
    first_opened_at  timestamptz NOT NULL,
    last_opened_at   timestamptz NOT NULL,
    CONSTRAINT pk_lesson_progress         PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user    FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_progress_lesson  FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_lesson_progress_watched CHECK (watched_seconds >= 0),
    CONSTRAINT ck_lesson_progress_dates   CHECK (last_opened_at >= first_opened_at)
);

-- De las lecciones de un curso a sus filas: la consulta del instructor y la
-- suma del avance por curso (RF-AC-040).
CREATE INDEX ix_lesson_progress_lesson
    ON lesson_progress (lesson_id);

COMMENT ON TABLE lesson_progress IS
    'Lo que una persona hizo con una leccion (AC, RN-AC-021 a RN-AC-023): la mayor posicion alcanzada de un video, cuando se completo y la primera y ultima apertura. No se retira ni lo toca nada que haga administracion con la leccion. El avance del curso se calcula, no se guarda.';

COMMENT ON COLUMN lesson_progress.watched_seconds IS
    'La mayor posicion reportada, acotada a la duracion de la leccion en el momento del reporte (RN-AC-021). Nunca baja. 0 en un TEXTO y al abrir un VIDEO.';

COMMENT ON COLUMN lesson_progress.completed_at IS
    'Cuando se completo: al llegar al 90 % de la duracion (VIDEO) o al abrirla (TEXTO). Se escribe una vez y no se borra (RN-AC-021, RN-AC-022).';

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-2000-7001-9c4f-5e7adc000031', 'lessons:track-progress', 'lessons', 'track-progress',
 'Reportar el avance de un video',
 'Reportar la posicion del reproductor de una leccion VIDEO que se ofrece y se abre por PUT /courses/available/{courseId}/lessons/{lessonId}/progress (RF-AC-039, RN-AC-021).'),
('01a11a5c-2000-7002-9c4f-5e7adc000032', 'courses:list-progress', 'courses', 'list-progress',
 'Consultar el progreso de los alumnos',
 'Listar el avance por alumno y curso dentro del alcance por GET /courses/progress (RF-AC-040, RN-AC-024): FUNCIONARIO todo, VENDEDOR su red y los clientes de su red, el instructor sus cursos.'),
('01a11a5c-2000-7003-9c4f-5e7adc000033', 'courses:read-progress', 'courses', 'read-progress',
 'Consultar el progreso de un alumno en un curso',
 'Ver leccion a leccion el avance de un alumno en un curso dentro del alcance por GET /courses/{courseId}/progress/{userId} (RF-AC-041, RN-AC-024).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, '01a11a5c-2000-7001-9c4f-5e7adc000031'::uuid
  FROM role_permissions rp
  JOIN permissions padre ON padre.id = rp.permission_id AND padre.code = 'lessons:learn'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (SELECT '01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid AS id
        UNION SELECT '01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid
        UNION SELECT id FROM roles WHERE role_type = 'VENDEDOR'
        UNION SELECT rp.role_id
                FROM role_permissions rp
                JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:teach') AS r
 CROSS JOIN permissions p
 WHERE p.id IN ('01a11a5c-2000-7002-9c4f-5e7adc000032',
                '01a11a5c-2000-7003-9c4f-5e7adc000033')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas       integer;
    de_raiz     integer;
    de_admin    integer;
    sin_hijo    integer;
    sin_padre   integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 219 THEN
        RAISE EXCEPTION 'V92: el catálogo debe tener 219 permisos; tiene %', filas;
    END IF;

    SELECT count(*) INTO de_raiz  FROM role_permissions WHERE role_id = '01a02a33-4c00-7001-9c4f-5e7ad1000001';
    SELECT count(*) INTO de_admin FROM role_permissions WHERE role_id = '01a02a33-4c00-7002-9c4f-5e7ad1000002';
    IF de_raiz <> 219 OR de_admin <> 217 THEN
        RAISE EXCEPTION 'V92: SUPERADMIN debe portar 219 permisos y ADMIN 217; tienen % y %', de_raiz, de_admin;
    END IF;

    -- Quien estudia reporta lo que estudia.
    SELECT count(*) INTO sin_hijo
      FROM role_permissions rp
      JOIN permissions padre ON padre.id = rp.permission_id AND padre.code = 'lessons:learn'
     WHERE NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = rp.role_id
                          AND x.permission_id = '01a11a5c-2000-7001-9c4f-5e7adc000031');
    IF sin_hijo <> 0 THEN
        RAISE EXCEPTION 'V92: % roles con lessons:learn sin lessons:track-progress', sin_hijo;
    END IF;

    -- Contención (RN-SEG-003): ningún rol con un permiso que su padre no porte.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V92: % filas de role_permissions rompen la contención de RN-SEG-003', sin_padre;
    END IF;
END $$;
