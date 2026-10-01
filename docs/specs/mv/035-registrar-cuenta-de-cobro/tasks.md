# TASKS — `RF-MV-035` Registrar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-035` |
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
| `T-01` | `PayoutHolderLookup` en `SP` y su implementación | — | Prueba de la interfaz: con documento, sin documento, inexistente, eliminada | **Hecha** — 01-10-2026 |
| `T-02` | `PayoutAccountType`, `PayoutAccountNumber`, `PayoutAccount` | `RF-MV-032` · `T-04` | Unitarias de `VAL-002` y `VAL-004` | **Hecha** — 01-10-2026 |
| `T-03` | `PayoutAccountRepository`: `lockOwner`, `liveOf`, `insert`, `unmarkPrincipal` | `RF-MV-032` · `T-01` | | **Hecha** — 01-10-2026 |
| `T-04` | `PayoutAccountService.register`, con el bloqueo por persona, la principal y la traducción de `uq_payout_accounts_numero` | `T-01` a `T-03` | La validación va antes de tocar nada | **Hecha** — 01-10-2026 |
| `T-05` | `PayoutAccountController`: `POST /movements/mine/payout-accounts` | `T-04` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 01-10-2026 |
| `T-06` | `RegisterPayoutAccountIT`: `CA-MV-378` a `CA-MV-389` | `T-05` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-07` | `EndpointPermissionsIT`; contrato con la prosa releída; `architecture.md` §15.2 (`PayoutHolderLookup`); `requirements.md` | `T-06` | | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

`T-01` y `T-02` en paralelo → `T-03` → `T-04` → `T-05` → `T-06` → `T-07`. Después de `RF-MV-032`. **`RF-MV-036` a `RF-MV-039` y la enmienda de `RF-MV-019` dependen de `T-01` y `T-03`.**

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-378` a `CA-MV-381` | `T-02`, `T-04`, `T-06` |
| `CA-MV-382`, `CA-MV-383`, `CA-MV-387` | `T-03`, `T-04`, `T-06` |
| `CA-MV-384` a `CA-MV-386` | `T-01`, `T-04`, `T-06` |
| `CA-MV-388`, `CA-MV-389` | `T-05`, `T-06` |

---

## 3.1 Desviaciones respecto del plan

**Un controlador y no tres.** Las ocho rutas viven en `PayoutController`, y las cuatro peticiones con cuerpo en `PayoutRequests`; no hay `PayoutInstitutionController`, `PayoutAccountController`, `PayoutInstitutionRequests` ni `PayoutAccountRequests`. **Sin modelos de dominio `PayoutInstitution` ni `PayoutAccount`**: la validación vive en `PayoutInstitutionService` y `PayoutAccountService`, apoyada en `PayoutInstitutionKind`, `PayoutAccountType` y `PayoutAccountNumber`; por eso no hay unitarias, y cada `VAL-` se prueba por integración. **`PayoutHolderLookup` no tiene prueba propia** en `SP`: la ejercen `CA-MV-378` (con documento) y `CA-MV-386` (sin él). **La cuenta repetida se comprueba bajo el bloqueo por persona** (`existsLive`) y no traduciendo `uq_payout_accounts_numero`, que queda como segunda defensa. El espacio del bloqueo consultivo es `4321`. La suite es `PayoutAccountsIT`, compartida con `RF-MV-036` a `RF-MV-039`.

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `architecture.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
