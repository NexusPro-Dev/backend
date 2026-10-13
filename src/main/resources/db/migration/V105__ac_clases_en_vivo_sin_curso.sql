-- =============================================================================
-- V105 — Las clases en vivo se separan de los cursos (RF-AC-058 · T-01;
-- requirements/ac.md v0.27.0 §5.2.18 y §8.10, §8.14, RN-AC-032; 13-10-2026).
--
--   * Nace live_session_instructors: la clase declara sus instructores. Se
--     rellena con el instructor del curso de cada clase que colgaba de uno, que
--     era quien la administraba (RN-AC-028 hasta hoy).
--   * live_sessions pierde course_id y gana cover_image_id, la portada en
--     academy_images, como el curso (RN-AC-004).
--   * Cuatro permisos de portada y el retiro de courses:list-own con
--     GET /courses/mine (RF-AC-057). Las descripciones de los propios de las
--     clases dejan de hablar del curso.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V94, secuencias 7010 a
-- 7013, y la serie de AC donde V104 la dejó (000042 → 000043 a 000046).
--
-- GUARDAS: 240 en el catálogo; el reparto de los cuatro.
-- =============================================================================

CREATE TABLE live_session_instructors (
    live_session_id  uuid        NOT NULL,
    user_id          uuid        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_live_session_instructors PRIMARY KEY (live_session_id, user_id),
    CONSTRAINT fk_live_session_instructors_session
        FOREIGN KEY (live_session_id) REFERENCES live_sessions (id),
    CONSTRAINT fk_live_session_instructors_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_live_session_instructors_user ON live_session_instructors (user_id);

INSERT INTO live_session_instructors (live_session_id, user_id)
SELECT s.id, c.instructor_id
  FROM live_sessions s
  JOIN courses c ON c.id = s.course_id
 WHERE c.instructor_id IS NOT NULL;

DROP INDEX ix_live_sessions_course;
ALTER TABLE live_sessions
    DROP CONSTRAINT fk_live_sessions_course,
    DROP COLUMN course_id,
    ADD COLUMN cover_image_id uuid NULL,
    ADD CONSTRAINT fk_live_sessions_cover_image
        FOREIGN KEY (cover_image_id) REFERENCES academy_images (id),
    ADD CONSTRAINT uq_live_sessions_cover_image UNIQUE (cover_image_id);

COMMENT ON TABLE live_session_instructors IS
    'Los instructores de una clase en vivo (RN-AC-032): portan courses:teach al asignarse, y gobiernan la clase con los permisos -own (RN-AC-028).';
COMMENT ON COLUMN live_sessions.cover_image_id IS
    'La portada de la clase (RN-AC-032, RN-AC-004): la fila de academy_images que se sirve sin token en /api/v1/academy-images/{id}. NULL = sin portada.';

-- -----------------------------------------------------------------------------
-- Permisos.
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-4000-7010-9c4f-5e7adc000043', 'live-sessions:set-cover', 'live-sessions', 'set-cover',
 'Poner la portada de una clase en vivo',
 'Subir o reemplazar la portada de cualquier clase por PUT /live-sessions/{id}/cover (RF-AC-058).'),
('01a11a5c-4000-7011-9c4f-5e7adc000044', 'live-sessions:remove-cover', 'live-sessions', 'remove-cover',
 'Quitar la portada de una clase en vivo',
 'Quitar la portada de cualquier clase por DELETE /live-sessions/{id}/cover (RF-AC-059).'),
('01a11a5c-4000-7012-9c4f-5e7adc000045', 'live-sessions:set-cover-own', 'live-sessions', 'set-cover-own',
 'Poner la portada de una clase en vivo mía',
 'Subir o reemplazar la portada de una clase de la que es instructor por PUT /live-sessions/mine/{id}/cover (RF-AC-060).'),
('01a11a5c-4000-7013-9c4f-5e7adc000046', 'live-sessions:remove-cover-own', 'live-sessions', 'remove-cover-own',
 'Quitar la portada de una clase en vivo mía',
 'Quitar la portada de una clase de la que es instructor por DELETE /live-sessions/mine/{id}/cover (RF-AC-061).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
 CROSS JOIN permissions p
 WHERE p.code IN ('live-sessions:set-cover', 'live-sessions:remove-cover')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
  FROM role_permissions rp
  JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:teach'
 CROSS JOIN permissions p
 WHERE p.code IN ('live-sessions:set-cover-own', 'live-sessions:remove-cover-own')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DELETE FROM role_permissions
 WHERE permission_id = (SELECT id FROM permissions WHERE code = 'courses:list-own');
DELETE FROM permissions WHERE code = 'courses:list-own';

UPDATE permissions SET name = 'Consultar mis clases en vivo',
       description = 'Listar las clases de las que es instructor por GET /live-sessions/mine (RF-AC-048, RN-AC-028).'
 WHERE code = 'live-sessions:list-own';
UPDATE permissions SET name = 'Programar una clase en vivo mía',
       description = 'Programar una clase de la que queda como instructor por POST /live-sessions/mine (RF-AC-049).'
 WHERE code = 'live-sessions:create-own';
UPDATE permissions SET name = 'Corregir una clase en vivo mía',
       description = 'Corregir una clase de la que es instructor por PATCH /live-sessions/mine/{id} (RF-AC-050).'
 WHERE code = 'live-sessions:update-own';
UPDATE permissions SET name = 'Cancelar una clase en vivo mía',
       description = 'Cancelar una clase de la que es instructor por POST /live-sessions/mine/{id}/cancellation (RF-AC-051).'
 WHERE code = 'live-sessions:cancel-own';
UPDATE permissions SET name = 'Iniciar como anfitrion una clase mía',
       description = 'El enlace de anfitrion de una clase de la que es instructor por POST /live-sessions/mine/{id}/host-link (RF-AC-052). Se audita.'
 WHERE code = 'live-sessions:host-own';
UPDATE permissions SET name = 'Ver una clase en vivo mía',
       description = 'El detalle de una clase de la que es instructor, con sus registrados, por GET /live-sessions/mine/{id} (RF-AC-056, RN-AC-028).'
 WHERE code = 'live-sessions:read-own';

-- -----------------------------------------------------------------------------
-- Guardas.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 240 THEN
        RAISE EXCEPTION 'V105: el catálogo debe tener 240 permisos; tiene %', filas;
    END IF;
    IF (SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
         WHERE p.code IN ('live-sessions:set-cover', 'live-sessions:remove-cover')) <> 4 THEN
        RAISE EXCEPTION 'V105: los dos de administración van a SUPERADMIN y ADMIN, y a nadie más';
    END IF;
    IF (SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
         WHERE p.code IN ('live-sessions:set-cover-own', 'live-sessions:remove-cover-own'))
       <> 2 * (SELECT count(*) FROM role_permissions rp JOIN permissions t ON t.id = rp.permission_id
                WHERE t.code = 'courses:teach') THEN
        RAISE EXCEPTION 'V105: los dos propios van a todo rol que porte courses:teach';
    END IF;
END $$;
