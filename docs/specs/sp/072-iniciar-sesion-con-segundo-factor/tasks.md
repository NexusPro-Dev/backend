# TASKS — `RF-SP-072` Iniciar sesión con el segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-072` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CredentialFailures` extraído de `LoginService` | — | Las pruebas de `RF-SP-034` siguen en verde **sin tocarse** | Pendiente |
| `T-02` | `MfaChallenge` y su repositorio; `RefreshToken` con `mfaVerifiedAt`; `AuthUser` con las dos marcas | `RF-SP-071` `T-01` | | Pendiente |
| `T-03` | `AccessTokenIssuer` con `mer` y `mfa`; `SessionService.refresh` los recalcula y copia | `T-02` | | Pendiente |
| `T-04` | `SecondFactorVerifier` | `RF-SP-071` `T-05` | Unitarias: TOTP, recuperación, ambos ausentes | Pendiente |
| `T-05` | `LoginService` emite el desafío; `LoginResponse`; `SessionResponse` con sus dos miembros | `T-01`, `T-03` | | Pendiente |
| `T-06` | `MfaLoginService` y `POST /auth/login/mfa`; ruta pública; cota `login-mfa` | `T-04`, `T-05` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-07` | `MfaEnrollmentFilter` y `ProblemKind.ACTIVACION_DE_SEGUNDO_FACTOR_REQUERIDA` | `T-03` | | Pendiente |
| `T-08` | `MfaLoginIT` (`CA-SP-820` a `CA-SP-833`) y `MfaEnrollmentRetentionIT` (`CA-SP-834` a `CA-SP-838`) | `T-06`, `T-07` | | Pendiente |
| `T-09` | `sinSegundoFactorObligatorio` en `IntegrationTestBase` y las once suites que entran por la API; `EndpointPermissionsIT` con quince públicas; contrato regenerado con la prosa releída; aviso en `deployment.md`; `requirements.md` | `T-08` | Suite completa en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05` → `T-06` → `T-07` → `T-08` → `T-09`. Va **después** de `RF-SP-071` entero.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-820`, `CA-SP-821` | `T-05`, `T-08` |
| `CA-SP-822` a `CA-SP-832` | `T-04`, `T-06`, `T-08` |
| `CA-SP-833` | `T-03`, `T-08` |
| `CA-SP-834` a `CA-SP-838` | `T-03`, `T-07`, `T-08` |

---

## 4. Bloqueos

Ninguno, salvo `RF-SP-071`.

---

## 5. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los diecinueve criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**: `POST /auth/login` cambia de esquema.
- [ ] `requirements.md`, `security.md` y `deployment.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
