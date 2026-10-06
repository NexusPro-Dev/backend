# TASKS — `RF-SP-071` Activar el segundo factor con una app autenticadora

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-071` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-09` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V75__sp_segundo_factor.sql`: las tres tablas, las tres columnas, los siete permisos con su reparto, las dieciocho marcas sensibles, `SUPERADMIN` y `ADMIN` obligados, los veintiocho eventos | — | Prueba de esquema: cada `CHECK` y cada único parcial rechaza lo que debe; la raíz no admite `requires_mfa` en falso | **Hecha** — 06-10-2026 |
| `T-02` | `shared/crypto/AesGcmCipher` extraído; `AesGcmShopSecrets` lo usa | — | `AesGcmCipherTest`; las pruebas de la tienda de PayRetailers siguen en verde | **Hecha** — 06-10-2026 |
| `T-03` | `Totp` | — | `TotpTest` con los vectores de RFC 6238 Apéndice B | **Hecha** — 06-10-2026 |
| `T-04` | `MfaSecrets` con `MFA_ENCRYPTION_KEY`; el contexto no arranca sin ella; la variable en `.env.example`, `docker-compose.yml`, `IntegrationTestBase` y [`deployment.md`](../../../deployment.md) | `T-02` | Un contexto sin la llave falla al arrancar | **Hecha** — 06-10-2026 |
| `T-05` | `MfaFactor`, `RecoveryCode`, sus repositorios, `RecoveryCodeIssuer` | `T-01` | | **Hecha** — 06-10-2026 |
| `T-06` | `MfaEnrollmentService` y `RecentMfa` (la lectura del claim `mfa`, compartida con `RF-SP-073`) | `T-03`, `T-04`, `T-05` | | **Hecha** — 06-10-2026 |
| `T-07` | `MfaController` con las dos rutas; `SecurityEventType` gana los siete; el enmascarador, sus cuatro campos | `T-06` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-08` | `MfaEnrollmentIT`: `CA-SP-807` a `CA-SP-819` | `T-07` | | **Hecha** — 06-10-2026 |
| `T-09` | Recuentos del catálogo (189 → 196), `EndpointPermissionsIT`, `reponerAlcancePropio` con los cinco; contrato regenerado con la prosa releída; `requirements.md` | `T-08` | Suite completa en verde | **Hecha** — 06-10-2026 |

---

## 2. Orden de ejecución

`T-01`, `T-02` y `T-03` en paralelo → `T-04` → `T-05` → `T-06` → `T-07` → `T-08` → `T-09`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-807` a `CA-SP-809` | `T-04`, `T-06`, `T-08` |
| `CA-SP-810` a `CA-SP-816` | `T-03`, `T-06`, `T-08` |
| `CA-SP-817` | `T-01`, `T-08` |
| `CA-SP-818`, `CA-SP-819` | `T-07`, `T-08` |

---

## 3.1 Desviaciones respecto del plan

Ninguna de diseño. Seis de detalle, todas por lo que el código ya tenía:

| # | Plan | Construido | Por qué |
|---|---|---|---|
| 1 | `MfaFactorRepository` y `RecoveryCodeRepository` | **Uno**, `MfaFactorRepository`, con los códigos dentro | Los códigos cuelgan del factor y siempre se leen con él bloqueado; dos repositorios serían dos sitios para la misma transacción |
| 2 | El enmascarador gana `secret`, `otpauthUri`, `recoveryCodes` y `code` | **Nada que enmascarar** | `request_log` no guarda cuerpos (`RequestLogFilter`): ni petición ni respuesta. Lo que sí se prueba es que ni el secreto ni los códigos llegan a la auditoría (`CA-SP-818`) |
| 3 | `ProblemKind.REVERIFICACION_REQUERIDA` nace en `RF-SP-073` | **Nace aquí**, con `RecentMfaRequiredException` y el código `AUTH-003` | `EX-003` ya lo necesita para el cambio de teléfono |
| 4 | Los claims `mfa` y `mer` llegan con `RF-SP-072` | `AccessTokenIssuer` gana **ya** la sobrecarga con los dos; el inicio de sesión todavía no los usa | `CA-SP-814` necesita un token con la prueba reciente; `mer` va en falso hasta `RF-SP-072` |
| 5 | — | `V75` da `ON DELETE CASCADE` a las FK hacia `users`, al revés que `refresh_tokens` | Lo decía el plan; se anota porque contradice la costumbre del esquema, y el motivo es que las suites borran a sus personas de prueba |
| 6 | `CA-SP-816`, que el código de la confirmación no sirva para entrar | Se prueba que `last_used_step` queda en ese periodo, y que ese periodo ya no casa | Entrar con código es `RF-SP-072`, que todavía no existe; la regla la prueba entera `TotpTest.unSoloUso` |

**Pruebas**: `TotpTest` (11, con los vectores de RFC 6238), `AesGcmCipherTest` (6), `MfaEnrollmentIT` (14). Suite completa en verde: 557 unitarias y 2473 de integración (`./mvnw clean verify`, 06-10-2026).

## 4. Bloqueos

Ninguno. `V74` es de `IN`; si su migración no hubiera entrado cuando empiece `T-01`, **se numera igual `V75`** y el catálogo se cuenta con lo que haya.

---

## 5. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los trece criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md`, `security.md` y `deployment.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-09` `Hecha`; seis desviaciones de detalle en §3.1. | Responsable técnico |
