-- =============================================================================
-- Amplía el catálogo cerrado de eventos de seguridad con el vigesimoprimer
-- código, `SESSION_TOKENS_PURGED` (issue #25).
--
-- POR QUÉ HACE FALTA UNA MIGRACIÓN PARA ESTO. El catálogo de `event_type` es
-- cerrado y lo impone un `CHECK` en la base, no una convención del código
-- (`security.md` §8.1). Esa es su virtud —un literal inventado no entra— y su
-- coste: ampliarlo cuesta una migración. Se paga a propósito.
--
-- QUÉ REGISTRA. La purga de familias de sesión ya caducadas borra las filas que
-- sostienen la detección de robo por reutilización de `RF-SP-035`. Una purga
-- que elimina evidencia sin dejar constancia de cuánta eliminó no es auditable,
-- y su ausencia sería indistinguible de una purga que nunca corrió.
--
-- POR QUÉ NO REUTILIZA NINGUNO DE LOS VEINTE ANTERIORES. `LOGOUT` es la persona
-- cerrando su sesión y `REFRESH_TOKEN_REUSE` es una alarma de robo; esto no es
-- ninguna de las dos, sino mantenimiento del sistema sobre sesiones que ya no
-- existían. Meterlo en cualquiera de ellos contaminaría dos lecturas que se
-- consultan por separado.
--
-- SEVERIDAD `INFORMATIVA` Y ACTOR NULO. No lo hizo nadie: lo hizo el sistema a su hora.
-- Lo que merece atención no es que ocurra, sino que deje de ocurrir — y eso no
-- lo cuenta un evento, sino la falta de él (issue #31).
--
-- NUMERACIÓN. Continúa después de `V35` y no en el hueco `V8`–`V12`: Flyway
-- aborta el arranque ante una migración fuera de orden sobre una base ya
-- migrada, de modo que los números reservados en su día son inservibles.
-- =============================================================================

ALTER TABLE audit_security_log
    DROP CONSTRAINT ck_audit_security_log_event_type;

ALTER TABLE audit_security_log
    ADD CONSTRAINT ck_audit_security_log_event_type CHECK (event_type IN (
        'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'ACCOUNT_LOCKED', 'REFRESH_TOKEN_REUSE',
        'LOGOUT', 'AUTHORIZATION_DENIED',
        'ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'ROLE_PERMISSIONS_CHANGED',
        'USER_CREATED', 'EMAIL_CHANGED',
        'USER_ROLES_ASSIGNED', 'USER_ROLES_REVOKED', 'USER_STATUS_CHANGED', 'USER_DELETED',
        'PASSWORD_CHANGED', 'PASSWORD_RESET',
        'SECURITY_AUDIT_READ',
        'RATE_LIMIT_EXCEEDED',
        'SESSION_TOKENS_PURGED'
    ));

COMMENT ON COLUMN audit_security_log.event_type IS
    'Catálogo cerrado de veintiún códigos (security.md §8.1). Ampliarlo exige migración.';
