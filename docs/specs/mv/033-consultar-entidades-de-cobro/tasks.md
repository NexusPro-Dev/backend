# TASKS — `RF-MV-033` Consultar las entidades de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-033` |
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
| `T-01` | `PayoutInstitutionRepository.list` con los tres filtros y el orden de `plan.md` §1 | `RF-MV-032` · `T-05` | Una sentencia | Pendiente |
| `T-02` | `PayoutInstitutionService.list`, con la validación de los filtros | `T-01` | `ACTIVA` por omisión | Pendiente |
| `T-03` | `GET /movements/payout-institutions` en `PayoutInstitutionController` | `T-02` | Documentado con los códigos de `plan.md` §4 | Pendiente |
| `T-04` | `ListPayoutInstitutionsIT`: `CA-MV-366` a `CA-MV-370` | `T-03` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-05` | `EndpointPermissionsIT`; contrato con la prosa releída; `requirements.md` | `T-04` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` → `T-04` → `T-05`. Después de `RF-MV-032`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-366` a `CA-MV-368` | `T-01`, `T-04` |
| `CA-MV-369` | `T-02`, `T-04` |
| `CA-MV-370` | `T-03`, `T-04` |

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los cinco criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
