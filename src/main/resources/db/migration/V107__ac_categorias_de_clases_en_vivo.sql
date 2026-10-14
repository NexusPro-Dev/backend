-- =============================================================================
-- V107 — Las categorías de las clases en vivo (RF-AC-063 · T-01;
-- requirements/ac.md v0.29.0 §5.2.20, §8.10 y §8.16, RN-AC-034; 14-10-2026).
--
--   * Nace live_session_categories: un catálogo propio de las clases, con
--     nombre y orden, distinto del de cursos. Único entre las VIVAS sin
--     acentos ni mayúsculas, como course_categories: una retirada libera el
--     nombre.
--   * Se siembran «Trading», «Clases» y «Señales», con orden 0, 1 y 2.
--   * live_sessions gana category_id, obligatoria: las clases que existían
--     quedan en «Clases».
--   * Cuatro permisos: las escrituras a SUPERADMIN y ADMIN explícitos; el
--     listado también donde está courses:teach —el instructor elige al
--     programar por /mine— y donde está live-sessions:learn —el alumno filtra
--     la vitrina—.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V94, secuencias 7014 a
-- 7017, y la serie de AC donde V105 la dejó (000046 → 000047 a 000050). Las
-- tres categorías, secuencias 7018 a 701a con la cola ca7e00000001 a 3.
--
-- GUARDAS: 244 en el catálogo; ninguna clase sin categoría.
-- =============================================================================

CREATE TABLE live_session_categories (
    id             uuid         PRIMARY KEY,
    name           varchar(100) NOT NULL,
    display_order  integer      NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL,
    CONSTRAINT ck_live_session_categories_display_order CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX uq_live_session_categories_name
    ON live_session_categories (f_unaccent(lower(name)))
    WHERE deleted_at IS NULL;

COMMENT ON TABLE live_session_categories IS
    'Las categorias de las clases en vivo (AC, RN-AC-034): nombre unico entre las vivas y orden no unico (RN-AC-002). Retirarla no toca las clases que la tienen (RN-AC-018).';

INSERT INTO live_session_categories (id, name, display_order) VALUES
('01a11a5c-4000-7018-9c4f-ca7e00000001', 'Trading', 0),
('01a11a5c-4000-7019-9c4f-ca7e00000002', 'Clases', 1),
('01a11a5c-4000-701a-9c4f-ca7e00000003', 'Señales', 2);

ALTER TABLE live_sessions ADD COLUMN category_id uuid NULL;
UPDATE live_sessions SET category_id = '01a11a5c-4000-7019-9c4f-ca7e00000002';
ALTER TABLE live_sessions
    ALTER COLUMN category_id SET NOT NULL,
    ADD CONSTRAINT fk_live_sessions_category
        FOREIGN KEY (category_id) REFERENCES live_session_categories (id);
CREATE INDEX ix_live_sessions_category ON live_sessions (category_id);

COMMENT ON COLUMN live_sessions.category_id IS
    'La categoria de la clase (RN-AC-034): obligatoria, viva al asignarse. Retirada despues, la clase la conserva.';

-- -----------------------------------------------------------------------------
-- Permisos.
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-4000-7014-9c4f-5e7adc000047', 'live-session-categories:list', 'live-session-categories', 'list',
 'Consultar las categorias de las clases en vivo',
 'Las categorias vivas de las clases en vivo por GET /live-session-categories (RF-AC-063).'),
('01a11a5c-4000-7015-9c4f-5e7adc000048', 'live-session-categories:create', 'live-session-categories', 'create',
 'Registrar una categoria de clases en vivo',
 'Registrar una categoria de las clases en vivo por POST /live-session-categories (RF-AC-064).'),
('01a11a5c-4000-7016-9c4f-5e7adc000049', 'live-session-categories:update', 'live-session-categories', 'update',
 'Corregir una categoria de clases en vivo',
 'Corregir el nombre o el orden de una categoria por PATCH /live-session-categories/{id} (RF-AC-065).'),
('01a11a5c-4000-7017-9c4f-5e7adc000050', 'live-session-categories:delete', 'live-session-categories', 'delete',
 'Retirar una categoria de clases en vivo',
 'Retirar una categoria, con motivo, por POST /live-session-categories/{id}/deletion (RF-AC-066).');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM (VALUES ('01a02a33-4c00-7001-9c4f-5e7ad1000001'::uuid),
               ('01a02a33-4c00-7002-9c4f-5e7ad1000002'::uuid)) AS r (id)
 CROSS JOIN permissions p
 WHERE p.code LIKE 'live-session-categories:%'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p.id
  FROM role_permissions rp
  JOIN permissions t ON t.id = rp.permission_id
                    AND t.code IN ('courses:teach', 'live-sessions:learn')
 CROSS JOIN permissions p
 WHERE p.code = 'live-session-categories:list'
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- -----------------------------------------------------------------------------
-- Guardas.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 244 THEN
        RAISE EXCEPTION 'V107: el catálogo debe tener 244 permisos; tiene %', filas;
    END IF;
    IF (SELECT count(*) FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
         WHERE p.code IN ('live-session-categories:create', 'live-session-categories:update',
                          'live-session-categories:delete')) <> 6 THEN
        RAISE EXCEPTION 'V107: las tres escrituras van a SUPERADMIN y ADMIN, y a nadie más';
    END IF;
    IF EXISTS (SELECT 1 FROM live_sessions WHERE category_id IS NULL) THEN
        RAISE EXCEPTION 'V107: ninguna clase puede quedar sin categoría';
    END IF;
END $$;
