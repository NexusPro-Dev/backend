# TASKS — `RF-MV-038` Dar de baja una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-038` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/cuentas-de-cobro` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PayoutAccountRepository.softDelete` y `promoteOldest` | `RF-MV-037` · `T-02` | Quitar la marca antes de ponerla | Pendiente |
| `T-02` | `PayoutAccountService.delete`, con el bloqueo por persona | `T-01` | | Pendiente |
| `T-03` | `DELETE /movements/mine/payout-accounts/{id}` | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `DeletePayoutAccountIT`: `CA-MV-404` a `CA-MV-409` | `T-03`, `RF-MV-019` (enmienda) | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05`. Después de `RF-MV-037`; `CA-MV-406` y `CA-MV-407`, después de la enmienda de `RF-MV-019`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-404` a `CA-MV-406` | `T-01`, `T-02`, `T-04` |
| `CA-MV-407`, `CA-MV-408` | `T-02`, `T-04` |
| `CA-MV-409` | `T-03`, `T-04` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los seis criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
