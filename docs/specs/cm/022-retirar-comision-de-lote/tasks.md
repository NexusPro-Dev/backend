# TASKS — `RF-CM-022` Retirar una comisión de un lote pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-022` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 30-09-2026 |
| Enmendadas | 07-10-2026 — sin la comisión revertida: lo hace `RF-CM-024` `T-09` y `T-10` (`RN-CM-047`) |
| Enmendadas | 07-10-2026 — `T-08` a `T-12` porque **el lote que se vacía se borra** (`RN-CM-052`) |
| Enmendadas | 08-10-2026 — `T-13` y `T-14` porque **retirar ya no borra**: lo hace `RF-CM-027` (`RN-CM-052` enmendada) |
| Issue | Pendiente de crear |
| Rama | `feature/corregir-vendedor-y-mover-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V59`**: las tres columnas de `commissions`, sus dos `CHECK` y dos claves, `uq_commissions_detail_user` como índice único parcial con el mismo nombre, `ix_commissions_withdrawn_from`, y los dos permisos a `SUPERADMIN` y `ADMIN` (`plan.md` §2) | — | `mvn clean verify` aplica la migración desde cero | **Hecha** — 30-09-2026 |
| `T-02` | Los recuentos del catálogo, 169 → 171 y `ADMIN` 167 → 169 (`plan.md` §10) | `T-01` | Las suites de siembra en verde | **Hecha** — 30-09-2026 |
| `T-03` | `lockCommission`, `moveCommission` y `adjustTotal` en el repositorio de lotes | `T-01` | — | **Hecha** — 30-09-2026 |
| `T-04` | `WithdrawCommissionService`, con el orden de bloqueos de `plan.md` §1 y la auditoría | `T-03` | — | **Hecha** — 30-09-2026 |
| `T-05` | `POST /{id}/commissions/{commissionId}/withdrawal`, en `PERMISO_DE_CADA_OPERACION` | `T-04`, `RF-CM-010` `T-09` | `EndpointPermissionsIT` | **Hecha** — 30-09-2026 |
| `T-06` | `WithdrawCommissionIT`: `CA-CM-273` a `CA-CM-281` | `T-05` | `CA-CM-280` con dos hilos | **Hecha** — 30-09-2026 |
| `T-07` | Contrato OpenAPI —esquema y prosa de la `@Operation`—; `requirements.md` | `T-06` | Diff del `json` | **Hecha** — 30-09-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02` primero: **la migración cambia la unicidad que usa el devengo**, y conviene ver `CommissionAccrualIT` en verde con el esquema solo. Después `T-03` → `T-04` → `T-05` → `T-06` → `T-07`. **`V59` es la primera tarea de las tres tripletas del cambio**: `RF-CM-023`, `RF-CM-024` y las enmiendas la dan por hecha.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-273` a `CA-CM-279` | `T-04`, `T-05`, `T-06` |
| `CA-CM-280` | `T-04`, `T-06` |
| `CA-CM-281` | `T-02`, `T-05`, `T-06` |

---

## 3.1 Desviaciones respecto del plan

- **`CA-CM-301`** (`RF-CM-011`) **se prueba aquí**, en el mismo caso que `CA-CM-279`: retirar todas y pagar es exactamente lo que ese criterio pide.
- **`CA-CM-275` siembra la comisión afftrack por SQL** —una liquidación y su fila `POR_AFFTRACK` en el pendiente— en lugar de liquidar un cierre con FTD reales: lo que se prueba es el retiro, y la liquidación ya tiene su suite.
- **El `404` de `EX-001` y `EX-002` no lleva `errors[]`**: la forma de un no encontrado es `detail`, y la prueba mira el texto.
- **Los recuentos del catálogo tocaron seis suites** (`PermissionIT`, `PermissionsSeedIT` —con los dos códigos en la lista aprobada—, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `TeamsPermissionsSeedIT`, `SaleLinesPermissionSeedIT`) y **`CommissionSettlementPermissionsSeedIT`**, que lista los permisos del recurso `commission-batches` y gana los dos.
## 4. Bloqueos

**Ninguno.** Toda la liquidación está construida.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. El lote que se vacía se borra — enmienda del 07-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-08` | **`V84`**: `fk_commissions_withdrawn_from` con `ON DELETE SET NULL` (`plan.md` §13) | — | Flyway aplica | **Hecha** — 07-10-2026 |
| `T-09` | `deleteIfEmpty` en el repositorio de lotes; `EmptyBatchRemoval` con `recordDeletion` `PHYSICAL`; `lockLatestUnpaidBatch` vuelve a buscar si el candidato desapareció | `T-08` | Compila | **Hecha** — 07-10-2026 |
| `T-10` | `WithdrawCommissionService` borra el pendiente vacío y responde el abierto; prosa de la `@Operation` | `T-09` | — | **Hecha** — 07-10-2026 |
| `T-11` | `WithdrawCommissionIT`: `CA-CM-363` a `CA-CM-365`; `CA-CM-301` con el pendiente vaciado por SQL | `T-10` | La suite en verde | **Hecha** — 07-10-2026 |
| `T-12` | Contrato regenerado, `api/index.md` y `requirements.md` | `T-11`, `RF-CM-023` `T-06`, `RF-CM-024` `T-16` | Las suites de `CM` y `OpenApiContractIT` en verde | **Hecha** — 07-10-2026 |

Orden: `T-08` → `T-09` → `T-10` → `T-11`; después `RF-CM-023` §6 y `RF-CM-024` §8, y `T-12` al final. Rama: `develop`.

## 7. Retirar ya no borra — enmienda del 08-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-13` | `WithdrawCommissionService` deja de llamar a `EmptyBatchRemoval` y responde siempre el pendiente; prosa de la `@Operation` (`plan.md` §14) | — | Compila | Pendiente |
| `T-14` | `WithdrawCommissionIT`: vuelve `CA-CM-279`, salen `CA-CM-363` a `CA-CM-365`, `CA-CM-301` retirando; `MyCommissionsIT` con una venta | `T-13` | La suite en verde | Pendiente |

Después, `RF-CM-027` `T-05`. Rama: `develop`.
