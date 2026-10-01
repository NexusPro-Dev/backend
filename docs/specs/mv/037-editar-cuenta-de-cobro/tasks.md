# TASKS — `RF-MV-037` Editar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-037` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 01-10-2026 |
| Issue | [#157](https://github.com/NexusPro-Dev/backend/issues/157) |
| Rama | `feature/cuentas-de-cobro` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `PayoutAccount.editar` | `RF-MV-035` · `T-02` | Unitarias: cambia, no cambia, «principal: no», tipo en billetera | **Hecha** — 01-10-2026 |
| `T-02` | `PayoutAccountRepository.findLiveOwn` y `update` | `RF-MV-035` · `T-03` | | **Hecha** — 01-10-2026 |
| `T-03` | `PayoutAccountService.update`, con el bloqueo por persona, la principal y `FA-001` | `T-01`, `T-02` | | **Hecha** — 01-10-2026 |
| `T-04` | `PATCH /movements/mine/payout-accounts/{id}` y `PayoutAccountRequests.Update` | `T-03` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 01-10-2026 |
| `T-05` | `EditPayoutAccountIT`: `CA-MV-395` a `CA-MV-403` | `T-04`, `RF-MV-019` (enmienda) | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-06` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-05` | | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

`T-01` y `T-02` en paralelo → `T-03` → `T-04` → `T-05` → `T-06`. Después de `RF-MV-035`; `CA-MV-402`, después de la enmienda de `RF-MV-019`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-395` a `CA-MV-398` | `T-01`, `T-03`, `T-05` |
| `CA-MV-399` a `CA-MV-402` | `T-02`, `T-03`, `T-05` |
| `CA-MV-403` | `T-04`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

**Un controlador y no tres.** Las ocho rutas viven en `PayoutController`, y las cuatro peticiones con cuerpo en `PayoutRequests`; no hay `PayoutInstitutionController`, `PayoutAccountController`, `PayoutInstitutionRequests` ni `PayoutAccountRequests`. **Sin modelos de dominio `PayoutInstitution` ni `PayoutAccount`**: la validación vive en `PayoutInstitutionService` y `PayoutAccountService`, apoyada en `PayoutInstitutionKind`, `PayoutAccountType` y `PayoutAccountNumber`; por eso no hay unitarias, y cada `VAL-` se prueba por integración. La suite es `PayoutAccountsIT`; **`CA-MV-402` vive en `WithdrawalDestinationIT`**, que es donde hay retiros con destino.

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los nueve criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
