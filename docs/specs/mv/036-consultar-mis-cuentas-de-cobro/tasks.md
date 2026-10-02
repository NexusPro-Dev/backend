# TASKS — `RF-MV-036` Consultar mis cuentas de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-036` |
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
| `T-01` | `PayoutAccountRepository.liveOf` con la entidad y el orden de `plan.md` §1 | `RF-MV-035` · `T-03` | Una sentencia | **Hecha** — 01-10-2026 |
| `T-02` | `PayoutAccountService.listMine` y `usable` en `PayoutAccountResponse` | `T-01` | | **Hecha** — 01-10-2026 |
| `T-03` | `GET /movements/mine/payout-accounts` | `T-02` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 01-10-2026 |
| `T-04` | `ListMyPayoutAccountsIT`: `CA-MV-390` a `CA-MV-394`, y la cuenta de sentencias | `T-03` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05`. Después de `RF-MV-035`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-390`, `CA-MV-391` | `T-01`, `T-04` |
| `CA-MV-392`, `CA-MV-393` | `T-02`, `T-04` |
| `CA-MV-394` | `T-03`, `T-04` |

---

## 3.1 Desviaciones respecto del plan

**Un controlador y no tres.** Las ocho rutas viven en `PayoutController`, y las cuatro peticiones con cuerpo en `PayoutRequests`; no hay `PayoutInstitutionController`, `PayoutAccountController`, `PayoutInstitutionRequests` ni `PayoutAccountRequests`. **Sin modelos de dominio `PayoutInstitution` ni `PayoutAccount`**: la validación vive en `PayoutInstitutionService` y `PayoutAccountService`, apoyada en `PayoutInstitutionKind`, `PayoutAccountType` y `PayoutAccountNumber`; por eso no hay unitarias, y cada `VAL-` se prueba por integración. La suite es `PayoutAccountsIT`; la cuenta de sentencias va en `CA-MV-394`.

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los cinco criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
