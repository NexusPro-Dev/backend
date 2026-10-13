-- =============================================================================
-- V104 — Las dos lecturas que le faltaban al instructor (RF-AC-056 · T-01 y
-- RF-AC-057; requirements/ac.md v0.26.0 §5.2.17; 13-10-2026).
--
--   * live-sessions:read-own — el detalle de una clase de un curso que dicta,
--     GET /live-sessions/mine/{id} (RF-AC-056).
--   * courses:list-own — los cursos que dicta, GET /courses/mine (RF-AC-057).
--
-- Se siembran como los propios de V94: en todo rol que porte courses:teach.
-- El catálogo pasa de 235 a 237.
--
-- IDENTIFICADOR LITERAL (Art. V.11): la marca v7 de V94, secuencias 700e y
-- 700f, y la serie de AC donde V94 la dejó (000040 → 000041, 000042).
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a11a5c-4000-700e-9c4f-5e7adc000041', 'live-sessions:read-own', 'live-sessions', 'read-own',
 'Ver una clase en vivo de mi curso',
 'El detalle de una clase de un curso que dicta, con sus registrados, por GET /live-sessions/mine/{id} (RF-AC-056, RN-AC-028).'),
('01a11a5c-4000-700f-9c4f-5e7adc000042', 'courses:list-own', 'courses', 'list-own',
 'Consultar los cursos que dicto',
 'Los cursos no retirados que dicta, por GET /courses/mine (RF-AC-057): de donde programa sus clases en vivo.');

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
  FROM role_permissions rp
  JOIN permissions t ON t.id = rp.permission_id AND t.code = 'courses:teach'
 CROSS JOIN permissions p
 WHERE p.code IN ('live-sessions:read-own', 'courses:list-own')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

DO $$
DECLARE
    filas integer;
BEGIN
    SELECT count(*) INTO filas FROM permissions;
    IF filas <> 237 THEN
        RAISE EXCEPTION 'V104: el catálogo debe tener 237 permisos; tiene %', filas;
    END IF;
    IF EXISTS (SELECT 1
                 FROM permissions p
                 JOIN role_permissions rp ON rp.permission_id = p.id
                WHERE p.code IN ('live-sessions:read-own', 'courses:list-own')
                  AND NOT EXISTS (SELECT 1 FROM role_permissions x
                                    JOIN permissions t ON t.id = x.permission_id
                                   WHERE x.role_id = rp.role_id AND t.code = 'courses:teach'))
       OR (SELECT count(*) FROM role_permissions rp
             JOIN permissions p ON p.id = rp.permission_id
            WHERE p.code IN ('live-sessions:read-own', 'courses:list-own'))
          <> 2 * (SELECT count(*) FROM role_permissions rp
                    JOIN permissions t ON t.id = rp.permission_id
                   WHERE t.code = 'courses:teach') THEN
        RAISE EXCEPTION 'V104: los dos permisos tienen que ir a todo rol que porte courses:teach, y a ningún otro';
    END IF;
END $$;
