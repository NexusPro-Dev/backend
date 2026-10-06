# TASKS — `RF-SP-074` Regenerar los propios códigos de recuperación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-074` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-04` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `supersedeVigentes` y `RecoveryCodeRegenerationService` | `RF-SP-071` `T-05` | | **Hecha** — 06-10-2026 |
| `T-02` | `POST /users/me/mfa/recovery-codes` y `RecoveryCodesResponse` | `T-01` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 06-10-2026 |
| `T-03` | `RecoveryCodeRegenerationIT`: `CA-SP-853` a `CA-SP-858` | `T-02`, `RF-SP-073` | | **Hecha** — 06-10-2026 |
| `T-04` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md` | `T-03` | Suite completa en verde | **Hecha** — 06-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`. Va **después** de `RF-SP-073`, del que depende para `CA-SP-855`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-853` a `CA-SP-858` | `T-01`, `T-02`, `T-03` |

---

## 3.1 Desviaciones respecto del plan

**Ninguna de diseño.** `supersedeVigentes` del plan se llama `anularCodigosVigentes` y nació con `RF-SP-071`, en el mismo repositorio que los factores (`071` · `tasks.md` §3.1, desviación 1). `CA-SP-854` comprueba además que el código **ya usado** conserva su historia —`used_at` y no `superseded_at`—.

**Pruebas**: `RecoveryCodeRegenerationIT` (6). Suite completa: en verde, 561 unitarias y 2520 de integración (`./mvnw clean verify`, 06-10-2026).

## 4. Bloqueos

Ninguno, salvo `RF-SP-073`.

---

## 5. Definición de terminado

- [x] `./mvnw verify` en verde.
- [x] Los seis criterios de aceptación con prueba.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [x] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
| — | 06-10-2026 | `T-01` a `T-04` `Hecha`. | Responsable técnico |
