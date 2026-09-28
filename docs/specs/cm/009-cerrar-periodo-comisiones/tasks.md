# TASKS — `RF-CM-009` Cerrar el periodo de comisiones, y consultar los cierres

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-009` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/devengo-de-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Propiedades `nexus.commissions.closing.*` en `application.yml` y `.env.example` | — | Arranque | Pendiente |
| `T-02` | `ClosingOrigin`; `CommissionClosingRepository` y adaptador | `RF-CM-013` `T-01` | Integración del repositorio: dos aperturas del mismo turno, una fila; el bloqueo tomado hace fallar el `try` | Pendiente |
| `T-03` | `CloseCommissionPeriodService`: apertura, barrido, cierre, contadores, auditoría | `T-02`, `RF-CM-013` `T-10` | — | Pendiente |
| `T-04` | `CommissionClosingJob` y `SchedulingConfig` con dos hilos | `T-01`, `T-03` | `CommissionClosingJobIT` | Pendiente |
| `T-05` | `POST /commission-batches/closing`, `GET /commission-closings`, sus `record`s con `@Schema(name)`, y las dos rutas en `PERMISO_DE_CADA_OPERACION` | `T-03` | `EndpointPermissionsIT` en verde | Pendiente |
| `T-06` | `CloseCommissionPeriodIT` y `ListCommissionClosingsIT`: `CA-CM-170` a `CA-CM-178` y `CA-CM-180` | `T-05` | `CA-CM-174` y `CA-CM-178` con dos hilos | Pendiente |
| `T-07` | Contrato OpenAPI —esquema y prosa de las `@Operation`—; `requirements.md` | `T-06` | Diff del `json` sin esquemas fundidos | Pendiente |

---

## 2. Orden de ejecución

**Después de `RF-CM-013`**: `T-01` → `T-02` → `T-03` → `T-04` y `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-170` a `CA-CM-175`, `CA-CM-178` | `T-03`, `T-06` |
| `CA-CM-176`, `CA-CM-177` | `T-05`, `T-06` |
| `CA-CM-179` | `T-04` |
| `CA-CM-180` | `T-05`, `T-06` |

---

## 4. Bloqueos

**`RF-CM-013`**: el esquema, el devengo y `CommissionableLines`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
