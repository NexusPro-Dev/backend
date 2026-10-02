# TASKS — `RF-MV-034` Editar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-034` |
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
| `T-01` | `PayoutInstitution.editar` | `RF-MV-032` · `T-04` | Unitarias: cambia, no cambia, nombre inválido | **Hecha** — 01-10-2026 |
| `T-02` | `PayoutInstitutionRepository.lock` y `update` | `RF-MV-032` · `T-05` | | **Hecha** — 01-10-2026 |
| `T-03` | `PayoutInstitutionService.update`, con `FA-001` y la auditoría del valor anterior | `T-01`, `T-02` | | **Hecha** — 01-10-2026 |
| `T-04` | `PATCH /movements/payout-institutions/{id}` y `PayoutInstitutionRequests.Update` | `T-03` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 01-10-2026 |
| `T-05` | `EditPayoutInstitutionIT`: `CA-MV-371` a `CA-MV-377` | `T-04` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-06` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-05` | | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

`T-01` y `T-02` en paralelo → `T-03` → `T-04` → `T-05` → `T-06`. Después de `RF-MV-032`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-371`, `CA-MV-372`, `CA-MV-375` | `T-01`, `T-03`, `T-05` |
| `CA-MV-373`, `CA-MV-374` | `T-03`, `T-05` |
| `CA-MV-376`, `CA-MV-377` | `T-04`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

**Un controlador y no tres.** Las ocho rutas viven en `PayoutController`, y las cuatro peticiones con cuerpo en `PayoutRequests`; no hay `PayoutInstitutionController`, `PayoutAccountController`, `PayoutInstitutionRequests` ni `PayoutAccountRequests`. **Sin modelos de dominio `PayoutInstitution` ni `PayoutAccount`**: la validación vive en `PayoutInstitutionService` y `PayoutAccountService`, apoyada en `PayoutInstitutionKind`, `PayoutAccountType` y `PayoutAccountNumber`; por eso no hay unitarias, y cada `VAL-` se prueba por integración. La suite es `PayoutInstitutionsIT`, y no `EditPayoutInstitutionIT`. `CA-MV-374` no espera a la enmienda de `RF-MV-019`: inserta la copia directamente, como el plan preveía hasta entonces.

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los siete criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
