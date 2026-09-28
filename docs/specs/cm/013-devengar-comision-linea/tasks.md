# TASKS — `RF-CM-013` Devengar las comisiones de una línea de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-013` |
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
| `T-01` | `V51__cm_devengo_de_comisiones.sql`: las cuatro tablas con sus restricciones, `ix_commission_batches_abierto`, y los ocho permisos con sus concesiones (`plan.md` §2) | — | `mvn clean verify` aplica la migración desde cero | Pendiente |
| `T-02` | Los seis recuentos del catálogo, 145 → 153 —`PermissionIT` con `153L`— y la suite de siembra de `CM` con los ocho códigos y quién los recibe | `T-01` | Las seis en verde | Pendiente |
| `T-03` | Ayudante común de limpieza de prueba que borra `commission_accruals`, `commissions`, `commission_batches` y `commission_closings` **antes** de `movements`; usarlo en las 21 suites que hacen `DELETE FROM movements` | `T-01` | `mvn clean verify` completo en verde, con el orden alfabético | Pendiente |
| `T-04` | `BusinessCalendar` en `shared/time`, con `nexus.business.zone` | — | Unitarias con el reloj a las `01:00Z` | Pendiente |
| `T-05` | `ResolveCommissionService`: «hoy» por `BusinessCalendar`; la operación interna que devuelve la tasa con su identidad | `T-04` | La suite de `RF-CM-005` sigue en verde; `CA-CM-159` | Pendiente |
| `T-06` | `SupervisorChain` en `SP` y su adaptador | — | `SupervisorChainIT` | Pendiente |
| `T-07` | `CommissionableLines` y `CommissionableLinesEvent` en `MV`; `ConfirmSaleService` y `AssignSellersService` publican | — | Las suites de `RF-MV-003` y `RF-MV-016` siguen en verde | Pendiente |
| `T-08` | `ChainCommissionCalculator` | — | Unitarias: `CA-CM-160`, `CA-CM-161`, casos límite de `spec.md` §13 | Pendiente |
| `T-09` | `AccrualOutcome`, `BatchStatus`; `CommissionAccrualRepository`, `CommissionBatchRepository` y adaptadores, con la secuencia del lote abierto (`plan.md` §4) | `T-01` | Integración del repositorio: dos aperturas simultáneas dejan un solo lote | Pendiente |
| `T-10` | `CommissionAccrualService`: bloqueo, relectura, cadena, cálculo, desenlace, lote, auditoría; una transacción por línea; `retryRejected()` | `T-05` a `T-09` | — | Pendiente |
| `T-11` | `CommissionableLinesListener` | `T-07`, `T-10` | — | Pendiente |
| `T-12` | `CommissionAccrualIT`: `CA-CM-154` a `CA-CM-169`, por la API de `MV` | `T-03`, `T-11` | `CA-CM-164` y `CA-CM-165` con dos hilos | Pendiente |
| `T-13` | `requirements.md` —`RF-CM-013` a **En desarrollo**—; `tasks.md` de `RF-MV-003` y `RF-MV-016` con la desviación del evento | `T-12` | | Pendiente |

---

## 2. Orden de ejecución

`T-01` → `T-02` → `T-03` primero: **la migración rompe las suites ajenas antes de que exista una línea de código de `CM`**, y conviene verlo en verde con el esquema solo. Después `T-04` a `T-09` en cualquier orden, `T-10`, `T-11`, `T-12` y `T-13`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-154` a `CA-CM-158` | `T-06`, `T-10`, `T-12` |
| `CA-CM-159` | `T-04`, `T-05`, `T-12` |
| `CA-CM-160`, `CA-CM-161` | `T-08`, `T-12` |
| `CA-CM-162`, `CA-CM-163` | `T-09`, `T-10`, `T-12` |
| `CA-CM-164`, `CA-CM-165` | `T-09`, `T-12` |
| `CA-CM-166` | `T-10`, `T-11`, `T-12` |
| `CA-CM-167`, `CA-CM-168` | `T-10`, `T-12` |
| `CA-CM-169` | `T-07`, `T-12` |

---

## 4. Bloqueos

**Ninguno.** `RF-MV-024` (`CommissionPayout`) ya está construido, pero no lo usa este requerimiento sino `RF-CM-011`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde, **completo**.
- [ ] Los dieciséis criterios de aceptación con prueba.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
