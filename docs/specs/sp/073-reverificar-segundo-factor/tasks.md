# TASKS — `RF-SP-073` Reverificar el segundo factor antes de una operación sensible

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-073` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PreAuthorizePermission` extraído de `RequiredPermissionCustomizer` | — | El contrato regenerado no cambia | Pendiente |
| `T-02` | `SensitivePermissions` | `RF-SP-071` `T-01` | Carga los dieciocho | Pendiente |
| `T-03` | `RecentMfaInterceptor` y `ProblemKind.REVERIFICACION_REQUERIDA` | `T-01`, `T-02` | | Pendiente |
| `T-04` | `MfaVerificationService` y `POST /auth/mfa/verification`; `CredentialFailures` revoca las sesiones al bloquear desde aquí | `RF-SP-072` `T-04` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-05` | `requiresRecentMfa` en `PermissionResponse` y `PermissionItem` | `RF-SP-071` `T-01` | | Pendiente |
| `T-06` | `RequiredPermissionCustomizer` añade el `403` y `x-requires-recent-mfa` a las operaciones sensibles | `T-02` | Las dieciocho lo llevan en el contrato | Pendiente |
| `T-07` | `MfaStepUpIT`: `CA-SP-839` a `CA-SP-852` | `T-03`, `T-04`, `T-05` | | Pendiente |
| `T-08` | `EndpointPermissionsIT`: lo sensible con `hasAuthority` simple; las suites con tokens reales contra rutas sensibles; contrato regenerado con la prosa releída; `security.md` §3.3 (interceptor, no filtro); `requirements.md` | `T-07` | Suite completa en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03`; `T-04` y `T-05` en paralelo → `T-06` → `T-07` → `T-08`. Va **después** de `RF-SP-072`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-839`, `CA-SP-844` a `CA-SP-849`, `CA-SP-851`, `CA-SP-852` | `T-04`, `T-07` |
| `CA-SP-840` a `CA-SP-843` | `T-03`, `T-07` |
| `CA-SP-850` | `T-05`, `T-07` |

---

## 4. Bloqueos

Ninguno, salvo `RF-SP-072`.

---

## 5. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los catorce criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
