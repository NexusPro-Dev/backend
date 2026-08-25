-- =============================================================================
-- Siembra del rol de negocio `CLIENTE`.
--
-- POR QUÉ ESTÁ AQUÍ Y NO EN `V7`. La primera versión de este rol se escribió
-- dentro de `V7__seed_system_roles.sql`, que YA ESTABA APLICADA. Flyway valida
-- cada migración por su suma de comprobación: editar una migración aplicada
-- hace que toda base de datos que ya la ejecutó —incluido cualquier entorno
-- local— falle al arrancar con un desajuste de checksum, y el fallo no dice
-- «alguien editó V7», dice «validación fallida». Por eso `V7` se devolvió a su
-- estado íntegro y el rol vive en esta migración nueva.
--
-- El código estaba además mal escrito —`CLINETE`— y se corrige aquí. Un código
-- de rol es un identificador permanente: se referencia desde configuración,
-- desde consultas y desde el propio catálogo, y un error tipográfico en él no
-- se corrige después sin tocar todo lo que ya lo cita.
--
-- REVIERTE UNA DECISIÓN DECLARADA, y conviene que quede escrito. `V7` afirma en
-- su encabezado que «`ESTUDIANTE` y `CLIENTE` quedan fuera: están marcados
-- is_system = false, es decir, son roles de negocio que se crean por la API»,
-- y `SystemRolesSeedIT` lo verificaba con una prueba dedicada. Sembrar `CLIENTE`
-- invierte esa decisión para este rol y solo para este: `ESTUDIANTE` sigue
-- fuera, y sigue creándose por la API.
--
-- NUMERACIÓN: siguiente número libre. La reserva de números por requerimiento
-- quedó muerta el 24-08-2026 (ver `V28`).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. El rol
--
-- SIN PERMISOS, igual que los cuatro roles que `V7` siembra a la espera de
-- `RF-SP-005`: sembrarlos a ojo produciría un catálogo que nadie aprobó y que
-- quedaría como referencia.
--
-- Clasificación `CONSUMIDOR`, que es lo que lo hace inseparable de una membresía
-- (`RN-SP-018`): a partir de ahora, dar de alta a alguien con este rol exige
-- indicar su membresía en la misma operación, y retirárselo arrastra la
-- membresía en cascada (`RN-SP-015`).
--
-- Identificador UUID v7 literal y estable entre entornos, continuando la serie
-- de `V7`: `7008` es el siguiente libre.
-- -----------------------------------------------------------------------------
INSERT INTO roles (id, code, name, description, role_type, parent_role_id, status, is_system) VALUES

('01a02a33-4c00-7008-9c4f-5e7ad1000008', 'CLIENTE', 'Cliente',
 'Rol de negocio de consumidor. Se siembra sin permisos, a la espera de RF-SP-005.',
 'CONSUMIDOR', '01a02a33-4c00-7001-9c4f-5e7ad1000001', 'ACTIVO', true);


-- -----------------------------------------------------------------------------
-- 2. Auditoría del poblado
--
-- Con actor_id, correlation_id e ip_address en NULL: lo creó el sistema, no una
-- persona (Art. V.15). Mismo criterio y misma forma que `V7`, y con el estado
-- INICIAL COMPLETO en `changes` —no un diff con `before` en nulo—, porque en un
-- CREATE es el estado lo que hay que conservar (`architecture.md` §6.6.2).
--
-- El identificador continúa la serie 7011 en adelante que `V7` usa para las
-- filas de auditoría, y no colisiona con ella: aquella llegó hasta la séptima.
-- -----------------------------------------------------------------------------
INSERT INTO audit_change_log (
    id, occurred_at, actor_id, correlation_id, ip_address, user_agent,
    module, entity, entity_id, action, changes
)
SELECT
    '01a02a33-4c00-7011-9c4f-5e7ad1000008'::uuid,
    now(),
    NULL, NULL, NULL, NULL,
    'SP',
    'roles',
    r.id,
    'CREATE',
    jsonb_build_object(
        'code',           r.code,
        'name',           r.name,
        'description',    r.description,
        'role_type',      r.role_type,
        'parent_role_id', r.parent_role_id,
        'status',         r.status,
        'is_system',      r.is_system,
        'permissions',    '[]'::jsonb
    )
  FROM roles r
 WHERE r.id = '01a02a33-4c00-7008-9c4f-5e7ad1000008';
