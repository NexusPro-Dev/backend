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
-- repositorio, y `RNF-SEG-003` lo prohíbe. Un despliegue sin credencial inicial
-- declarada debe fallar, en lugar de arrancar con una conocida (Art. IX.5), y de
-- eso se encarga la guarda que abre este archivo.
--
-- EL IDENTIFICADOR ES FIJO y está escrito aquí. Es la única fila de `users` que
-- puede tener un identificador conocido, y hace falta que lo sea: las pruebas de
-- integración de todos los requerimientos posteriores necesitan un actor con
-- `users:create` y deben poder referirlo sin consultarlo. UUID v7 con marca de
-- tiempo 2026-08-24T12:00:00Z (01a033a4-4a00).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Guarda de la credencial
--
-- «Si el marcador no tiene valor, la aplicación no arranca» es falso cuando la
-- variable está declarada y **vacía**: la sustitución de una variable de entorno
-- vacía en `application.yml` resuelve a cadena vacía sin error, `password_hash`
-- es `NOT NULL` y la cadena vacía lo satisface. La siembra terminaba con éxito y
-- el superadministrador no podía entrar nunca, sin que nada fallara. Lo tapaba
-- el `:?` de `docker-compose.yml`, que además tumbaba el archivo entero e
-- impedía levantar la base de datos sola.
--
-- (Y una advertencia para quien edite este archivo: Flyway sustituye los
-- marcadores **también dentro de los comentarios**. Escribir aquí un ejemplo de
-- marcador con una variable que no exista aborta la migración antes de
-- ejecutarla. Este comentario ya costó una vez.)
--
-- La comprobación vive aquí porque es el único punto por el que pasan **todos**
-- los caminos: contenedor, `mvn spring-boot:run`, integración continua y
-- cualquier despliegue futuro.
--
-- La segunda condición no es celo. Docker Compose interpola los valores del
-- `.env` y convierte `$argon2id$v=19$…` en un resto irreconocible sin avisar;
-- exigir el prefijo transforma ese estropicio silencioso en un fallo de
-- migración inmediato, que es donde se puede leer.
-- -----------------------------------------------------------------------------
DO $guarda$
BEGIN
    IF btrim('${superadmin_password_hash}') = '' THEN
        RAISE EXCEPTION
            'SUPERADMIN_PASSWORD_HASH no está declarado. Un despliegue sin credencial inicial debe fallar, no arrancar con una conocida (Art. IX.5).';
    END IF;

    IF left('${superadmin_password_hash}', 9) <> '$argon2id' THEN
        RAISE EXCEPTION
            'SUPERADMIN_PASSWORD_HASH no parece un hash Argon2id. Si viene de un archivo .env, sus $ deben ir DUPLICADOS: Docker Compose los interpola y destruye el valor (ver .env.example).';
    END IF;

    IF btrim('${superadmin_email}') = '' THEN
        RAISE EXCEPTION
            'SUPERADMIN_EMAIL no está declarado.';
    END IF;
END
$guarda$;


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
