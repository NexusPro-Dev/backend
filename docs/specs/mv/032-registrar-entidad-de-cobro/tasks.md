# TASKS — `RF-MV-032` Registrar una entidad de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-032` |
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
| `T-01` | Migración de las cuentas de cobro (`plan.md` §2): las tres tablas con sus restricciones e índices, los ocho permisos y sus guardas | — | Aplicada sobre la base de desarrollo; las guardas pasan | Pendiente |
| `T-02` | Recuentos del catálogo 172 → 180 (`ADMIN` 170 → 178) en todas las suites que lo cuentan | `T-01` | `grep -rnE "\b172\b\|\b170\b" src/test` sin restos | Pendiente |
| `T-03` | `CountryCatalog` en `SP` y su implementación | — | Una prueba de la interfaz: existe, activo, inexistente | Pendiente |
| `T-04` | `PayoutInstitutionKind`, `PayoutInstitution` | — | Unitarias de `VAL-001` a `VAL-003` y de la normalización | Pendiente |
| `T-05` | `PayoutInstitutionRepository` (`insert`, `find`) | `T-01`, `T-04` | | Pendiente |
| `T-06` | `PayoutInstitutionService.register`, con la traducción de `uq_payout_institutions_code` | `T-03`, `T-05` | La validación va antes de tocar nada | Pendiente |
| `T-07` | `PayoutInstitutionController`: `POST /movements/payout-institutions` | `T-06` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-08` | `PayoutInstitutionsIT`: `CA-MV-358` a `CA-MV-365`; prueba de esquema de `payout_accounts` | `T-07` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-09` | `EndpointPermissionsIT`; contrato con la prosa releída; `architecture.md` §15.2 (`CountryCatalog`); `requirements.md`, `security.md` | `T-08` | | Pendiente |

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

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los ocho criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba** y no solo citado en un rango.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md`, `security.md` y `architecture.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
