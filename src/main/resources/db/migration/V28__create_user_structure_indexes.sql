-- =============================================================================
-- RF-SP-030 · T-01 y RF-SP-031 · T-04 — Accesos sobre la estructura de personas.
--
-- Ninguna tabla nueva, ninguna columna nueva y ninguna restricción nueva. Las
-- tres tablas que esta migración indexa las crean `V19`, `V20` y `V21`
-- (`RF-SP-024`), porque el alta de usuario ya escribe en ellas.
--
-- NUMERACIÓN: los planes reservaban `V25` para el índice de `user_roles`
-- (`RF-SP-030` §2) y `V24` para el de `user_supervisors` (`RF-SP-028`). Esos
-- números YA NO SIRVEN: `V26` y `V27` se aplicaron el 24-08-2026 con el bloque
-- de sesión, y Flyway rechaza una migración con número inferior al último
-- aplicado salvo que se habilite `outOfOrder` — que no se habilita, porque
-- permitir que el orden de aplicación difiera del orden de numeración convierte
-- el historial en algo que ya no describe cómo llegó el esquema a su estado.
--
-- Se toma el siguiente número libre y se declara aquí. Los huecos `V8`-`V12` y
-- `V23`-`V25` quedan MUERTOS: quien implemente `RF-SP-002`, los cuatro listados
-- de auditoría, `RF-SP-025` o `RF-SP-028` toma el número que esté libre
-- entonces, y no el que su plan reservó. La reserva por requerimiento no
-- funciona cuando los requerimientos no se implementan en orden.
--
-- Los dos índices van juntos porque son la misma clase de acceso —la pregunta
-- inversa sobre una tabla de asociación— y porque separarlos en dos migraciones
-- consecutivas no aporta nada que este comentario no diga.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. `user_roles` por rol (`RF-SP-030` §2)
--
-- La clave primaria compuesta `(user_id, role_id)` solo sirve consultas que
-- empiezan por la persona. La pregunta inversa —«cuántas personas portan este
-- rol»— la necesitan `RF-SP-003` y `RF-SP-009` para sus conteos, y `RF-SP-031`
-- para `RN-SP-001`: sin este índice, comprobar que queda algún
-- superadministrador activo recorre la tabla entera en cada retiro de roles.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_user_roles_role_id ON user_roles (role_id);

COMMENT ON INDEX ix_user_roles_role_id IS
    'Portadores de un rol. La clave primaria no sirve esta dirección (RF-SP-030 §2).';


-- -----------------------------------------------------------------------------
-- 2. `user_supervisors` por superior vigente (`RF-SP-031` §2, nominal `RF-SP-028`)
--
-- PARCIAL sobre `ended_at IS NULL` a propósito. La pregunta que sostiene
-- `RN-SP-022` —«¿tiene alguien a cargo?»— es siempre sobre asignaciones
-- VIGENTES, y la tabla conserva el historial cerrado para siempre: un índice
-- total crecería con cada reasignación de la historia de la empresa para
-- responder una pregunta que solo mira el presente.
--
-- `uq_user_supervisors_vigente` (`V21`) no sirve aquí: va sobre `user_id`, que
-- es la dirección contraria.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_user_supervisors_supervisor_vigente
    ON user_supervisors (supervisor_id) WHERE ended_at IS NULL;

COMMENT ON INDEX ix_user_supervisors_supervisor_vigente IS
    'Equipo vigente de una persona (RN-SP-022, RF-SP-042). Parcial: el historial cerrado no se consulta por esta vía.';
