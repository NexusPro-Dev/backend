# TASKS — `RF-MV-035` Registrar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-035` |
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
| `T-01` | `PayoutHolderLookup` en `SP` y su implementación | — | Prueba de la interfaz: con documento, sin documento, inexistente, eliminada | Pendiente |
| `T-02` | `PayoutAccountType`, `PayoutAccountNumber`, `PayoutAccount` | `RF-MV-032` · `T-04` | Unitarias de `VAL-002` y `VAL-004` | Pendiente |
| `T-03` | `PayoutAccountRepository`: `lockOwner`, `liveOf`, `insert`, `unmarkPrincipal` | `RF-MV-032` · `T-01` | | Pendiente |
| `T-04` | `PayoutAccountService.register`, con el bloqueo por persona, la principal y la traducción de `uq_payout_accounts_numero` | `T-01` a `T-03` | La validación va antes de tocar nada | Pendiente |
| `T-05` | `PayoutAccountController`: `POST /movements/mine/payout-accounts` | `T-04` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-06` | `RegisterPayoutAccountIT`: `CA-MV-378` a `CA-MV-389` | `T-05` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-07` | `EndpointPermissionsIT`; contrato con la prosa releída; `architecture.md` §15.2 (`PayoutHolderLookup`); `requirements.md` | `T-06` | | Pendiente |

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

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `architecture.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
