# TASKS — `RF-SP-071` Activar el segundo factor con una app autenticadora

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-071` |
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
| `T-01` | `V75__sp_segundo_factor.sql`: las tres tablas, las tres columnas, los siete permisos con su reparto, las dieciocho marcas sensibles, `SUPERADMIN` y `ADMIN` obligados, los veintiocho eventos | — | Prueba de esquema: cada `CHECK` y cada único parcial rechaza lo que debe; la raíz no admite `requires_mfa` en falso | Pendiente |
| `T-02` | `shared/crypto/AesGcmCipher` extraído; `AesGcmShopSecrets` lo usa | — | `AesGcmCipherTest`; las pruebas de la tienda de PayRetailers siguen en verde | Pendiente |
| `T-03` | `Totp` | — | `TotpTest` con los vectores de RFC 6238 Apéndice B | Pendiente |
| `T-04` | `MfaSecrets` con `MFA_ENCRYPTION_KEY`; el contexto no arranca sin ella; la variable en `.env.example`, `docker-compose.yml`, `IntegrationTestBase` y [`deployment.md`](../../../deployment.md) | `T-02` | Un contexto sin la llave falla al arrancar | Pendiente |
| `T-05` | `MfaFactor`, `RecoveryCode`, sus repositorios, `RecoveryCodeIssuer` | `T-01` | | Pendiente |
| `T-06` | `MfaEnrollmentService` y `RecentMfa` (la lectura del claim `mfa`, compartida con `RF-SP-073`) | `T-03`, `T-04`, `T-05` | | Pendiente |
| `T-07` | `MfaController` con las dos rutas; `SecurityEventType` gana los siete; el enmascarador, sus cuatro campos | `T-06` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-08` | `MfaEnrollmentIT`: `CA-SP-807` a `CA-SP-819` | `T-07` | | Pendiente |
| `T-09` | Recuentos del catálogo (189 → 196), `EndpointPermissionsIT`, `reponerAlcancePropio` con los cinco; contrato regenerado con la prosa releída; `requirements.md` | `T-08` | Suite completa en verde | Pendiente |

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

## 4. Bloqueos

Ninguno. `V74` es de `IN`; si su migración no hubiera entrado cuando empiece `T-01`, **se numera igual `V75`** y el catálogo se cuenta con lo que haya.

---

## 5. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los trece criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md`, `security.md` y `deployment.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
