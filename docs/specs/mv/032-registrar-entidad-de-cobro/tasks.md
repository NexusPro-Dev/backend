# TASKS — `RF-MV-032` Registrar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-032` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** — `T-01` a `T-09` `Hecha` el 01-10-2026; `T-10` `Hecha` el 05-10-2026 |
| Issue | [#157](https://github.com/NexusPro-Dev/backend/issues/157) |
| Rama | `feature/cuentas-de-cobro` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración de las cuentas de cobro (`plan.md` §2): las tres tablas con sus restricciones e índices, los ocho permisos y sus guardas | — | Aplicada sobre la base de desarrollo; las guardas pasan | **Hecha** — 01-10-2026 |
| `T-02` | Recuentos del catálogo 172 → 180 (`ADMIN` 170 → 178) en todas las suites que lo cuentan | `T-01` | `grep -rnE "\b172\b\|\b170\b" src/test` sin restos | **Hecha** — 01-10-2026 |
| `T-03` | `CountryCatalog` en `SP` y su implementación | — | Una prueba de la interfaz: existe, activo, inexistente | **Hecha** — 01-10-2026 |
| `T-04` | `PayoutInstitutionKind`, `PayoutInstitution` | — | Unitarias de `VAL-001` a `VAL-003` y de la normalización | **Hecha** — 01-10-2026 |
| `T-05` | `PayoutInstitutionRepository` (`insert`, `find`) | `T-01`, `T-04` | | **Hecha** — 01-10-2026 |
| `T-06` | `PayoutInstitutionService.register`, con la traducción de `uq_payout_institutions_code` | `T-03`, `T-05` | La validación va antes de tocar nada | **Hecha** — 01-10-2026 |
| `T-07` | `PayoutInstitutionController`: `POST /movements/payout-institutions` | `T-06` | Documentado con los códigos de `plan.md` §4 | **Hecha** — 01-10-2026 |
| `T-08` | `PayoutInstitutionsIT`: `CA-MV-358` a `CA-MV-365`; prueba de esquema de `payout_accounts` | `T-07` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-09` | `EndpointPermissionsIT`; contrato con la prosa releída; `architecture.md` §15.2 (`CountryCatalog`); `requirements.md`, `security.md` | `T-08` | | **Hecha** — 01-10-2026 |
| `T-10` | **El código puede empezar por dígito** (05-10-2026): `V66` recrea `ck_payout_institutions_code` con `^[A-Z0-9][A-Z0-9_]{1,29}$`, y el patrón y el mensaje de `VAL-001` en `PayoutInstitutionService` cambian igual | `T-06` | `CA-MV-547` en `PayoutInstitutionsIT`; `CA-MV-362` sigue rechazando `1-mal` por el guion | **Hecha el 05-10-2026** |

---

## 2. Orden de ejecución

`T-01` → `T-02`; `T-03` y `T-04` en paralelo; → `T-05` → `T-06` → `T-07` → `T-08` → `T-09`. **Es el primero de las cuentas de cobro**: `RF-MV-033` a `RF-MV-039` y la enmienda de `RF-MV-019` dependen de `T-01`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-358`, `CA-MV-359`, `CA-MV-362` | `T-04`, `T-06`, `T-08` |
| `CA-MV-360`, `CA-MV-361` | `T-01`, `T-06`, `T-08` |
| `CA-MV-363` | `T-03`, `T-06`, `T-08` |
| `CA-MV-364`, `CA-MV-365` | `T-07`, `T-08` |
| `CA-MV-547` | `T-10` |

---

## 3.1 Desviaciones respecto del plan

**Un controlador y no tres.** Las ocho rutas viven en `PayoutController`, y las cuatro peticiones con cuerpo en `PayoutRequests`; no hay `PayoutInstitutionController`, `PayoutAccountController`, `PayoutInstitutionRequests` ni `PayoutAccountRequests`. **Sin modelos de dominio `PayoutInstitution` ni `PayoutAccount`**: la validación vive en `PayoutInstitutionService` y `PayoutAccountService`, apoyada en `PayoutInstitutionKind`, `PayoutAccountType` y `PayoutAccountNumber`; por eso no hay unitarias, y cada `VAL-` se prueba por integración. **`CountryCatalog` no tiene prueba propia** en `SP`: la ejercen `CA-MV-363` (inexistente e inactivo) y todo registro válido. La unicidad del código es `INSERT … ON CONFLICT (code) DO NOTHING`, que no aborta la transacción, en vez de traducir la violación del índice. La suite es `PayoutInstitutionsIT`, compartida con `RF-MV-033` y `RF-MV-034`.

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los ocho criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba** y no solo citado en un rango.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md`, `security.md` y `architecture.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
