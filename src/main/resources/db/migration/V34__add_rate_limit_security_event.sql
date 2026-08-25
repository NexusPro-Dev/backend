-- =============================================================================
-- Issue #21 — El catálogo de eventos de seguridad gana un vigésimo código:
-- `RATE_LIMIT_EXCEEDED`.
--
-- POR QUÉ HACE FALTA UNA MIGRACIÓN PARA AÑADIR UN VALOR. El catálogo de
-- `audit_security_log` es CERRADO por diseño (`security.md` §8.1) y el `CHECK`
-- lo hace cumplir: sin él, cada emisor inventaría su forma de escribir el mismo
-- hecho —RATE_LIMITED, TOO_MANY_REQUESTS, LIMITE_TASA— y el registro dejaría de
-- ser consultable por tipo. El precio de esa garantía es exactamente esto: un
-- valor nuevo se declara, no se cuela.
--
-- POR QUÉ NO SE REUTILIZA `LOGIN_FAILURE`. Un rechazo por tasa NO ES UN INTENTO
-- DE ACCESO FALLIDO: la credencial ni siquiera llega a comprobarse. Mezclarlos
-- corrompería las dos lecturas que ese registro sirve — el contador de intentos
-- de una cuenta y la investigación de un acceso—, porque una ráfaga bloqueada
-- inflaría el primero sin que nadie haya fallado una contraseña.
--
-- SEVERIDAD. La emite el filtro con `ALTA`: quien topa con el límite está
-- haciendo algo que ningún cliente legítimo hace, y debe poder encontrarse
-- buscando por severidad junto a los intentos de escalada.
--
-- `outcome` es siempre `FAILURE`: la petición no se atendió.
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
        'RATE_LIMIT_EXCEEDED'
    ));

COMMENT ON COLUMN audit_security_log.event_type IS
    'Catálogo cerrado de veinte códigos (security.md §8.1). Ampliarlo exige migración.';
