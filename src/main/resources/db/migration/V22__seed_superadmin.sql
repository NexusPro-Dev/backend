-- =============================================================================
-- RF-SP-024 · T-05 — Semilla del superadministrador.
--
-- `RN-SP-001` lo convierte en obligación permanente: el sistema no puede
-- quedarse sin un superadministrador activo, y el primero NO PUEDE CREARSE POR
-- ESTA API — haría falta un actor con `users:create`, que es justo lo que
-- todavía no existe.
--
-- LA CREDENCIAL ENTRA POR MARCADOR DE POSICIÓN DE FLYWAY, no como literal en el
-- archivo: un hash escrito en el repositorio es una credencial en el
-- repositorio, y `RNF-SEG-003` lo prohíbe. Si el marcador no tiene valor, la
-- aplicación no arranca —`application.yml` lo declara sin valor por omisión—, y
-- eso es exactamente lo que se busca: un despliegue sin credencial inicial
-- declarada falla, en lugar de arrancar con una conocida.
--
-- EL IDENTIFICADOR ES FIJO y está escrito aquí. Es la única fila de `users` que
-- puede tener un identificador conocido, y hace falta que lo sea: las pruebas de
-- integración de todos los requerimientos posteriores necesitan un actor con
-- `users:create` y deben poder referirlo sin consultarlo. UUID v7 con marca de
-- tiempo 2026-08-24T12:00:00Z (01a033a4-4a00).
-- =============================================================================

INSERT INTO users (
    id, username, email, first_name, last_name,
    password_hash, must_change_password, status
) VALUES (
    '01a033a4-4a00-7001-9c4f-5e7ad4000001',
    'superadmin',
    '${superadmin_email}',
    'Super',
    'Administrador',
    '${superadmin_password_hash}',
    -- NACE MARCADO PARA CAMBIO OBLIGATORIO, igual que cualquier alta: quien
    -- preparó el despliegue conoce la credencial, y la ventana en que dos
    -- personas la conocen se cierra en el primer inicio de sesión.
    true,
    'ACTIVO'
);


-- -----------------------------------------------------------------------------
-- El rol
--
-- Depende de `V7__seed_system_roles.sql`, que es donde nace `SUPERADMIN`. Si ese
-- rol no existiera, el INSERT … SELECT no insertaría fila alguna y el
-- superadministrador quedaría SIN PERMISOS: la migración debe fallar, no
-- continuar en silencio. De ahí la comprobación de abajo.
-- -----------------------------------------------------------------------------
INSERT INTO user_roles (user_id, role_id)
SELECT '01a033a4-4a00-7001-9c4f-5e7ad4000001', id
  FROM roles
 WHERE code = 'SUPERADMIN';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM user_roles
         WHERE user_id = '01a033a4-4a00-7001-9c4f-5e7ad4000001'
    ) THEN
        RAISE EXCEPTION
            'El rol SUPERADMIN no existe: la semilla habría dejado al superadministrador sin permisos';
    END IF;
END $$;


-- -----------------------------------------------------------------------------
-- Auditoría del poblado
--
-- Con actor, correlación e IP en NULL: lo creó el sistema, no una persona
-- (Art. V.15). Mismo criterio que `V7` y `V15`.
--
-- `changes` NO lleva ningún campo derivado de la credencial, ni siquiera su
-- longitud (Art. IV.8).
-- -----------------------------------------------------------------------------
INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    '01a033a4-4a00-7011-9c4f-5e7ad4000001'::uuid,
    now(),
    NULL, NULL, NULL, NULL,
    'SP',
    'users',
    u.id,
    'CREATE',
    jsonb_build_object(
        'username',             u.username,
        'email',                u.email,
        'first_name',           u.first_name,
        'last_name',            u.last_name,
        'status',               u.status,
        'must_change_password', u.must_change_password,
        'roles',                jsonb_build_array('SUPERADMIN')
    )
  FROM users u
 WHERE u.id = '01a033a4-4a00-7001-9c4f-5e7ad4000001';
