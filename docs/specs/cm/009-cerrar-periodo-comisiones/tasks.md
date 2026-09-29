# TASKS — `RF-CM-009` Cerrar el periodo de comisiones, y consultar los cierres

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-009` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 28-09-2026 |
| Enmendadas | 29-09-2026 — `T-08` por **la liquidación afftrack** (`RN-CM-043`) |
| Issue | Pendiente de crear |
| Rama | `feature/devengo-de-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Propiedades `nexus.commissions.closing.*` en `application.yml` y `.env.example` | — | Arranque | **Hecha** — 28-09-2026 |
| `T-02` | `ClosingOrigin`; `CommissionClosingRepository` y adaptador | `RF-CM-013` `T-01` | Integración del repositorio: dos aperturas del mismo turno, una fila; el bloqueo tomado hace fallar el `try` | **Hecha** — 28-09-2026 |
| `T-03` | `CloseCommissionPeriodService`: apertura, barrido, cierre, contadores, auditoría | `T-02`, `RF-CM-013` `T-10` | — | **Hecha** — 28-09-2026 |
| `T-04` | `CommissionClosingJob` y `SchedulingConfig` con dos hilos | `T-01`, `T-03` | `CommissionClosingJobIT` | **Hecha** — 28-09-2026 |
| `T-05` | `POST /commission-batches/closing`, `GET /commission-closings`, sus `record`s con `@Schema(name)`, y las dos rutas en `PERMISO_DE_CADA_OPERACION` | `T-03` | `EndpointPermissionsIT` en verde | **Hecha** — 28-09-2026 |
| `T-06` | `CloseCommissionPeriodIT` y `ListCommissionClosingsIT`: `CA-CM-170` a `CA-CM-178` y `CA-CM-180` | `T-05` | `CA-CM-174` y `CA-CM-178` con dos hilos | **Hecha** — 28-09-2026 |
| `T-07` | Contrato OpenAPI —esquema y prosa de las `@Operation`—; `requirements.md` | `T-06` | Diff del `json` sin esquemas fundidos | **Hecha** — 28-09-2026 |

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

## 3.1 Desviaciones respecto del plan

- **Los dos hilos del planificador van por propiedad** (`spring.task.scheduling.pool.size: 2`) y no con un `ThreadPoolTaskScheduler` propio, como decía `plan.md` §3: declarar ese bean **apaga el `applicationTaskExecutor`** que Spring configura solo, y la recuperación de contraseña depende de él. Se probó, y el contexto no arrancaba.
- **El cierre de los lotes es un solo `UPDATE`**, y no un `SELECT … FOR UPDATE` seguido de otro `UPDATE`: el `UPDATE` toma cada fila con el mismo bloqueo, de modo que la frontera con el devengo (`CA-CM-178`) es la misma con una sentencia menos. **Solo cierra los lotes nacidos antes del instante del cierre** (`period_start < :at`): uno que naciera en ese mismo instante tendría un periodo vacío, que `ck_commission_batches_periodo` rechaza.
- **La siembra común vive en `SettlementFixtures`**, en el paquete de pruebas de `CM`: la usarán también `RF-CM-010` a `RF-CM-012` y `RF-CM-014`.
- **Las respuestas de error del contrato repiten el esquema de la de éxito**, como en todos los controladores del sistema: es como springdoc las pinta sin un `@Content` explícito, y cambiarlo aquí solo lo haría distinto del resto.

## 4. Bloqueos

**`RF-CM-013`**: el esquema, el devengo y `CommissionableLines`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los once criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. La liquidación afftrack — enmienda del 29-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-08` | El paso afftrack y el instante del cierre posterior al corte en `CloseCommissionPeriodService` (`plan.md` §12) | `RF-CM-020` `T-07` | `CloseCommissionPeriodIT` en verde sin cambiar sus criterios | **Hecha** — 29-09-2026 |

Rama: `feature/comision-afftrack`. **Es la misma tarea que `RF-CM-020` `T-07`**, vista desde aquí.
