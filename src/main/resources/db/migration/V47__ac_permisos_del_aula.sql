-- =============================================================================
-- V47 — El aula pasa a un permiso por vista (RF-AC-034, RF-AC-035,
-- requirements/ac.md v0.20.0 §5.2.13, security.md §4.4 v0.77.0, 26-09-2026).
--
-- `courses:learn` estaba diseñado para las tres vistas del aula —catálogo,
-- detalle y contenido— y ninguna estaba construida. Al construirlas, RN-SEG-014
-- (una operación, un permiso) exige dos códigos más: `courses:read-available`
-- para el detalle y `lessons:learn` para el contenido. `courses:learn` se queda
-- con el catálogo. Ningún código se renombra ni se retira.
--
-- SE REPARTE A QUIEN YA PORTA `courses:learn`, como V28 hizo con los hijos de
-- cada padre: quien veía el aula la sigue viendo entera. Hoy son SUPERADMIN y
-- ADMIN (V22); CLIENTE no, por la misma decisión de ac.md §4 —quien administra
-- roles concede la vista del alumno a los de tipo CONSUMIDOR—.
--
-- IDENTIFICADORES LITERALES (Art. V.11): la marca v7 del 26-09-2026 a
-- medianoche UTC —`01a0db02f800`— continuando la serie de `courses:` (5e7adc)
-- donde V28 la dejó: 000028 → 000029, 000030.
--
-- GUARDA POR CONJUNTO Y NO POR RECUENTO, como V40 y V41: el catálogo cambia de
-- tamaño en varias ramas a la vez.
-- =============================================================================

INSERT INTO permissions (id, code, resource, action, name, description) VALUES
('01a0db02-f800-7001-9c4f-5e7adc000029', 'courses:read-available', 'courses', 'read-available',
 'Ver el detalle de un curso como alumno',
 'Ver un curso que se ofrece con su arbol ofrecido, sin contenido, y que le abren las llaves de quien mira (RF-AC-034). El catalogo es courses:learn y el contenido lessons:learn (RN-SEG-014).'),
('01a0db02-f800-7002-9c4f-5e7adc000030', 'lessons:learn', 'lessons', 'learn',
 'Estudiar el contenido de una leccion',
 'Leer el contenido de una leccion que se ofrece, si esta abierta, si el curso no declara llaves o si la membresia o un servicio vigentes lo abren (RF-AC-035). Desde administracion se lee con lessons:read.');

-- ---------------------------------------------------------------------------
-- El reparto: a todo rol que porte `courses:learn`. `ON CONFLICT` por si
-- alguien los concedió a mano entre dos arranques.
-- ---------------------------------------------------------------------------

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, hijo.id
  FROM role_permissions rp
  JOIN permissions padre ON padre.id = rp.permission_id AND padre.code = 'courses:learn'
  CROSS JOIN permissions hijo
 WHERE hijo.code IN ('courses:read-available', 'lessons:learn')
ON CONFLICT ON CONSTRAINT pk_role_permissions DO NOTHING;

-- ---------------------------------------------------------------------------
-- Guardas.
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    sin_hijos  integer;
    sin_padre  integer;
BEGIN
    -- Todo rol con `courses:learn` porta los dos: nadie pierde una vista.
    SELECT count(*) INTO sin_hijos
      FROM role_permissions rp
      JOIN permissions padre ON padre.id = rp.permission_id AND padre.code = 'courses:learn'
     WHERE (SELECT count(*) FROM role_permissions x
              JOIN permissions p ON p.id = x.permission_id
             WHERE x.role_id = rp.role_id
               AND p.code IN ('courses:read-available', 'lessons:learn')) <> 2;
    IF sin_hijos <> 0 THEN
        RAISE EXCEPTION 'V47: % roles con courses:learn se quedaron sin la vista entera', sin_hijos;
    END IF;

    -- Contencion (RN-SEG-003): el hijo va donde va el padre, y el padre ya la
    -- cumplía; se comprueba igual.
    SELECT count(*) INTO sin_padre
      FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id
     WHERE r.parent_role_id IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM role_permissions x
                        WHERE x.role_id = r.parent_role_id
                          AND x.permission_id = rp.permission_id);
    IF sin_padre <> 0 THEN
        RAISE EXCEPTION 'V47: % asociaciones rompen la contencion de RN-SEG-003', sin_padre;
    END IF;
END $$;
