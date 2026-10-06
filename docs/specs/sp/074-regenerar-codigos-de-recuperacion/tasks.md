# TASKS — `RF-SP-074` Regenerar los propios códigos de recuperación

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-074` |
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
| `T-01` | `supersedeVigentes` y `RecoveryCodeRegenerationService` | `RF-SP-071` `T-05` | | Pendiente |
| `T-02` | `POST /users/me/mfa/recovery-codes` y `RecoveryCodesResponse` | `T-01` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-03` | `RecoveryCodeRegenerationIT`: `CA-SP-853` a `CA-SP-858` | `T-02`, `RF-SP-073` | | Pendiente |
| `T-04` | `EndpointPermissionsIT`; contrato regenerado con la prosa releída; `requirements.md` | `T-03` | Suite completa en verde | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04`. Va **después** de `RF-SP-073`, del que depende para `CA-SP-855`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-SP-853` a `CA-SP-858` | `T-01`, `T-02`, `T-03` |

---

## 4. Bloqueos

Ninguno, salvo `RF-SP-073`.

---

## 5. Definición de terminado

- [ ] `./mvnw verify` en verde.
- [ ] Los seis criterios de aceptación con prueba.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| — | 06-10-2026 | Aprobadas por el responsable del proyecto. | Responsable técnico |
