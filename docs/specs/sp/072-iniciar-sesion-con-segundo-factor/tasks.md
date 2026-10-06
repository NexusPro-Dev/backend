# TASKS — `RF-SP-072` Iniciar sesión con el segundo factor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-072` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-09` `Hecha` el mismo día, más `T-10`, adelantada de `RF-SP-077` |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CredentialFailures` extraído de `LoginService` | — | Las pruebas de `RF-SP-034` siguen en verde **sin tocarse** | **Hecha** — 06-10-2026 |
| `T-02` | `MfaChallenge` y su repositorio; `RefreshToken` con `mfaVerifiedAt`; `AuthUser` con las dos marcas | `RF-SP-071` `T-01` | | **Hecha** — 06-10-2026 |
| `T-03` | `AccessTokenIssuer` con `mer` y `mfa`; `SessionService.refresh` los recalcula y copia | `T-02` | | **Hecha** — 06-10-2026 |
| `T-04` | `SecondFactorVerifier` | `RF-SP-071` `T-05` | Unitarias: TOTP, recuperación, ambos ausentes | **Hecha** — 06-10-2026 |
| `T-05` | `LoginService` emite el desafío; `LoginResponse`; `SessionResponse` con sus dos miembros | `T-01`, `T-03` | | **Hecha** — 06-10-2026 |
| `T-06` | `MfaLoginService` y `POST /auth/login/mfa`; ruta pública; cota `login-mfa` | `T-04`, `T-05` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-07` | `MfaEnrollmentFilter` y `ProblemKind.ACTIVACION_DE_SEGUNDO_FACTOR_REQUERIDA` | `T-03` | | **Hecha** — 06-10-2026 |
| `T-08` | `MfaLoginIT` (`CA-SP-820` a `CA-SP-833`) y `MfaEnrollmentRetentionIT` (`CA-SP-834` a `CA-SP-838`) | `T-06`, `T-07` | | **Hecha** — 06-10-2026 |
| `T-09` | `sinSegundoFactorObligatorio` en `IntegrationTestBase` y las once suites que entran por la API; `EndpointPermissionsIT` con quince públicas; contrato regenerado con la prosa releída; aviso en `deployment.md`; `requirements.md` | `T-08` | Suite completa en verde | **Hecha** — 06-10-2026 |
| `T-10` | **Adelantada de `RF-SP-077` (`T-05`), a petición del responsable del proyecto el 06-10-2026**: el propio perfil publica `mfa` —`enabled`, `enabledAt`, `required`—, para saber sin mirar la base si el factor está activo | — | `CA-SP-892` en `MfaLoginIT` | **Hecha** — 06-10-2026 |

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

## 3.1 Desviaciones respecto del plan

Ninguna de diseño. Seis de detalle:

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | `LoginService` abre la sesión y `MfaLoginService` también | **`SessionOpener`**, un componente que abre la sesión para los dos | Abrir una sesión —registrar el acceso, el refresh token, el evento, las tres marcas del token— escrito dos veces divergiría en el sitio menos visible |
| 2 | Diecinueve criterios en dos suites, `MfaLoginIT` y `MfaEnrollmentRetentionIT` | **Una**, `MfaLoginIT` (15), más `CA-SP-832` en `RateLimitIT` | La retención necesita la misma persona y los mismos pasos; partirla duplicaba la preparación. El límite de tasa solo se enciende en `RateLimitIT` |
| 3 | `CA-SP-827`: un periodo a cada lado vale y dos no, con el reloj real | Con el reloj real se prueba **el uso único**; la tolerancia ±1 y el rechazo de ±2, en `TotpTest` con reloj fijo | Un desplazamiento de dos periodos puede cruzar el borde de los treinta segundos a mitad de la prueba y volverse uno: la prueba fallaría al azar |
| 4 | Once suites que entran por la API como `SUPERADMIN` o `ADMIN` | **Tres** necesitaron `sinSegundoFactorObligatorio`: `AuthIT` (un caso), `PasswordRecoveryIT` y `AccessRevocationIT` | Las demás no dan `ADMIN` a su persona o solo usan rutas de sesión, que la retención deja pasar. Nadie entra por la API como el superadministrador |
| 5 | — | `LOGIN_SUCCESS` gana `"mfa"` en su detalle siempre, y `"recoveryCode": true` cuando se entró con uno | Lo decía `plan.md` §6; se anota porque cambia el detalle de un evento existente |
| 6 | — | `T-10`: el estado del factor en el perfil, que era de `RF-SP-077` | A petición del responsable del proyecto: sin ella, saber si alguien tiene el factor activo exigía consultar la base |

**Pruebas**: `MfaLoginIT` (15), `RateLimitIT` (+1). Suite completa en verde: 561 unitarias y 2498 de integración (`./mvnw clean verify`, 06-10-2026).

## 4. Bloqueos

Ninguno, salvo `RF-SP-071`.

---

## 5. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los diecinueve criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: `POST /auth/login` cambia de esquema.
- [x] `requirements.md`, `security.md` y `deployment.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-10` `Hecha`; seis desviaciones de detalle en §3.1. | Responsable técnico |
