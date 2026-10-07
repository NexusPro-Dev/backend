# TASKS — `RF-CM-024` Revertir las comisiones de una línea cuyo vendedor se corrige

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-024` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 30-09-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 30-09-2026 |
| Enmendadas | 07-10-2026 — `T-07` a `T-11` porque **la cadena vieja se borra** (`RN-CM-047`) |
| Issue | Pendiente de crear |
| Rama | `feature/corregir-vendedor-y-mover-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CommissionedLineRelease` y `ReleaseOutcome` en `movements.application`, con su Javadoc | — | Compila | **Hecha** — 30-09-2026 |
| `T-02` | `hasCountedFtd`, `lockLiveCommissionsOf`, `revert` y `deleteOutcome` en el repositorio de desenlaces | `RF-CM-022` `T-01` | — | **Hecha** — 30-09-2026 |
| `T-03` | `ReleaseCommissionedLineService` implementa el puerto, `MANDATORY`, con el bloqueo de la línea y la auditoría | `T-01`, `T-02`, `RF-CM-023` `T-01` | — | **Hecha** — 30-09-2026 |
| `T-04` | La aserción de ArchUnit: nada de `movements` depende de `commissions` | — | La suite de arquitectura en verde | **Hecha** — 30-09-2026 |
| `T-05` | `ReleaseCommissionedLineIT`: `CA-CM-290` a `CA-CM-299`, por la ruta de `RF-MV-016` | `T-03`, `RF-MV-016` `T-14` | `CA-CM-298` con dos hilos | **Hecha** — 30-09-2026 |
| `T-06` | `requirements.md` | `T-05` | | **Hecha** — 30-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-022` y `RF-CM-023`**: `T-01` → `T-02` → `T-03`; entonces **`RF-MV-016` `T-13` y `T-14`**, que invocan el puerto; y `T-04`, `T-05`, `T-06`. **`T-01` es de `MV` y vive aquí** porque el puerto existe para este requerimiento, igual que `RF-CM-013` escribió `CommissionableLinesEvent`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-290`, `CA-CM-292`, `CA-CM-295`, `CA-CM-296`, `CA-CM-299` | `T-02`, `T-03`, `T-05` |
| `CA-CM-291` | `T-03`, `T-05`, `RF-CM-013` `T-18` |
| `CA-CM-293`, `CA-CM-294`, `CA-CM-297` | `T-03`, `T-05`, `RF-MV-016` `T-13` |
| `CA-CM-298` | `T-03`, `T-05` |

---

## 3.1 Desviaciones respecto del plan

- **`CA-CM-302` a `CA-CM-305`**, de las enmiendas de `RF-CM-010`, `RF-CM-012` y `RF-CM-013`, **viven en `ReleaseCommissionedLineIT`**, junto a `CA-MV-351` a `CA-MV-356`: todos necesitan una línea corregida, y el escenario de dos agentes con un director común está aquí.
- **`CA-CM-294` prueba la mitad del «aún no contado»**: que la corrección prospera. Que el siguiente cierre cuente el FTD para el vendedor nuevo **no se prueba**: `RF-CM-020` lee el vendedor de la línea al liquidar, y montar una escala afftrack entera para ello duplicaría `AfftrackSettlementIT`.
- **Las ventas FTD de la prueba se confirman por SQL** (`status` y `confirmed_at`): confirmar un upgrade por la API concede la membresía, y aquí solo importa que la venta esté `CONFIRMADA`.
- **La auditoría usa `ChangeAction.UPDATE` sobre `commission_accruals`**, como decía el plan; el desenlace se borra, pero la constancia es de la línea, que sigue existiendo.
## 4. Bloqueos

**`RF-CM-022`** (el esquema) y **`RF-MV-016`** (la ruta por la que se prueba).

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los diez criterios de aceptación con prueba.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

---

## 6. La cadena vieja se borra — enmienda del 07-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `V80`: borra las revertidas, retira `reverted_at`, `reverted_by`, su `CHECK` y su clave, y rehace `uq_commissions_detail_user` como restricción (`plan.md` §12) | — | Flyway aplica sobre la base de la suite | Pendiente |
| `T-08` | `delete` en el repositorio de desenlaces; `ReleaseCommissionedLineService` borra, con `deleted_commissions` y `deleted_by` en la auditoría | `T-07` | — | Pendiente |
| `T-09` | Fuera la marca de lotes, cierre, pago, retirar, devolver, detalle y listado, y la prosa de las `@Operation` (`plan.md` §12) | `T-07` | Compila | Pendiente |
| `T-10` | `ReleaseCommissionedLineIT`: `CA-CM-340` a `CA-CM-346` y `CA-MV-700`; `CA-CM-305` borrando por SQL; `WithdrawCommissionIT`, `ReturnCommissionIT` y `PayCommissionBatchesIT` sin la marca | `T-08`, `T-09` | Las cuatro suites en verde | Pendiente |
| `T-11` | Contrato regenerado y `requirements.md` | `T-10` | `./mvnw clean verify` en verde | Pendiente |

Rama: `develop`.
