# TASKS — `RF-SP-073` Reverificar el segundo factor antes de una operación sensible

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-073` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-08` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PreAuthorizePermission` extraído de `RequiredPermissionCustomizer` | — | El contrato regenerado no cambia | **Hecha** — 06-10-2026 |
| `T-02` | `SensitivePermissions` | `RF-SP-071` `T-01` | Carga los dieciocho | **Hecha** — 06-10-2026 |
| `T-03` | `RecentMfaInterceptor` y `ProblemKind.REVERIFICACION_REQUERIDA` | `T-01`, `T-02` | | **Hecha** — 06-10-2026 |
| `T-04` | `MfaVerificationService` y `POST /auth/mfa/verification`; `CredentialFailures` revoca las sesiones al bloquear desde aquí | `RF-SP-072` `T-04` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-05` | `requiresRecentMfa` en `PermissionResponse` y `PermissionItem` | `RF-SP-071` `T-01` | | **Hecha** — 06-10-2026 |
| `T-06` | `RequiredPermissionCustomizer` añade el `403` y `x-requires-recent-mfa` a las operaciones sensibles | `T-02` | Las dieciocho lo llevan en el contrato | **Hecha** — 06-10-2026 |
| `T-07` | `MfaStepUpIT`: `CA-SP-839` a `CA-SP-852` | `T-03`, `T-04`, `T-05` | | **Hecha** — 06-10-2026 |
| `T-08` | `EndpointPermissionsIT`: lo sensible con `hasAuthority` simple; las suites con tokens reales contra rutas sensibles; contrato regenerado con la prosa releída; `security.md` §3.3 (interceptor, no filtro); `requirements.md` | `T-07` | Suite completa en verde | **Hecha** — 06-10-2026 |

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

## 3.1 Desviaciones respecto del plan

Ninguna de diseño. Cinco de detalle:

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | `SensitivePermissions` carga los códigos **al arrancar** | Los carga **la primera vez que se necesitan**, y los guarda | Al crearse el componente Flyway puede no haber migrado todavía: una lectura al arrancar encontraría la columna sin crear |
| 2 | `EndpointPermissionsIT` vigila que lo sensible se declare con un `hasAuthority` simple | **No hizo falta una prueba nueva** | `PreAuthorizePermission` —antes dentro de `RequiredPermissionCustomizer`— ya se niega a leer cualquier otra expresión, y el contrato no se genera si alguna operación la usa: la garantía existía antes de este requerimiento |
| 3 | — | Nace `RecentMfaWebConfig`, el `WebMvcConfigurer` que registra el interceptor sobre `/api/**` | El proyecto no tenía ninguno |
| 4 | — | `PermissionItem` conserva un constructor de seis campos que pone `requiresRecentMfa` en falso | Las pruebas que lo construyen a mano no tienen por qué saber del segundo factor |
| 5 | Las dieciocho operaciones sensibles llevan en el contrato el `403` y `x-requires-recent-mfa` | **Catorce**: las cuatro restantes son rutas de `RF-SP-074` a `RF-SP-077`, que todavía no existen | Su permiso ya está marcado en `V75`; la extensión aparecerá sola cuando nazca cada ruta |

**Pruebas**: `MfaStepUpIT` (14), `PermissionResponseTest` (siete campos). Suite completa: ver la fila de control de cambios.

## 4. Bloqueos

Ninguno, salvo `RF-SP-072`.

---

## 5. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los catorce criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `security.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-08` `Hecha`; cinco desviaciones de detalle en §3.1. | Responsable técnico |
