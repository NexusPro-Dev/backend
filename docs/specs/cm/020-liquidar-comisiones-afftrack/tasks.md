# TASKS — `RF-CM-020` Liquidar las comisiones afftrack en el cierre

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-020` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 29-09-2026 |
| Estado | **En revisión** — `T-01` a `T-09` `Hecha` el 29-09-2026; `T-10` y `T-11` `Hecha` el 05-10-2026; `T-12` **Pendiente** (`RN-MV-075`) |
| Enmendadas | 05-10-2026 — `T-10` y `T-11` porque **los importes se guardan en centésimas** (`ADR-006`) |
| Issue | Pendiente de crear |
| Rama | `feature/comision-afftrack` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | El enumerado `CommissionKind` sobre la columna que `RF-CM-015` `T-02` ya escribe; `CommissionKindSchemaIT` (`CA-CM-254`) | `RF-CM-015` `T-02` | | **Hecha** — 29-09-2026 |
| `T-02` | Enmienda de `RF-CM-013`: excluir las líneas de `ftdProductIds()` del devengo y del barrido (`CA-CM-253`) | `RF-CM-015` `T-04` | `CommissionAccrualIT` | **Hecha** — 29-09-2026 |
| `T-03` | `AfftrackTierPicker` y sus unitarias | — | Unitarias | **Hecha** — 29-09-2026 |
| `T-04` | `AfftrackScale`, con las dos fuentes en bloque y el predicado de vigencia de `RF-CM-019` | `RF-CM-015` `T-05`, `RF-CM-019` `T-01` | | **Hecha** — 29-09-2026 |
| `T-05` | `AfftrackSettlementRepository`: FTD nuevos, remanentes, escrituras | `T-01` | | **Hecha** — 29-09-2026 |
| `T-06` | Inserción `POR_AFFTRACK` en `CommissionBatchRepository` sobre el lote abierto | `T-01` | | **Hecha** — 29-09-2026 |
| `T-07` | `AfftrackSettlementService` (`MANDATORY`) y el paso en `CloseCommissionPeriodService`, con el instante del cierre posterior al corte | `T-02`–`T-06` | `CloseCommissionPeriodIT` sigue en verde | **Hecha** — 29-09-2026 |
| `T-08` | `AfftrackSettlementIT`: `CA-CM-241` a `CA-CM-252` | `T-07` | Cerrando por la API | **Hecha** — 29-09-2026 |
| `T-09` | `requirements.md` y `cm.md` si la construcción cambia algo | `T-08` | | **Hecha** — 29-09-2026 |

---

## 2. Orden de ejecución

**Después de `RF-CM-015` y `RF-CM-019`**: `T-01`, `T-02`, `T-03` → `T-04`, `T-05`, `T-06` → `T-07` → `T-08` → `T-09`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-241` a `CA-CM-252` | `T-03`–`T-08` |
| `CA-CM-253` | `T-02` |
| `CA-CM-254` | `T-01` |
| `CA-CM-339` | `T-12` |

---

## 3.1 Desviaciones respecto del plan

- **`AfftrackScale` no es una clase propia**: la escala se resuelve en `AfftrackSettlementRepository.scales`, dos sentencias para todas las personas del cierre —la de persona con `AfftrackSql.VIGENTE_EN` y la del rol por `user_roles.role_type`—, y la de rol solo entra donde no hay propia.
- **La comisión `POR_AFFTRACK` la escribe `AfftrackSettlementRepository.insertCommission`**, no `CommissionBatchRepository`: el lote abierto y la suma al total sí salen de él (`lockOpenBatch`, `addToTotal`).
- **Las ventas FTD se siembran por SQL ya confirmadas y entregadas**, no activándolas por la API de `MV`: la activación ya la prueba `RF-MV-010`, y aquí lo que importa es el cierre, que sí se lanza por la API.
- **`CA-CM-253` y `CA-CM-254` viven en `AfftrackSettlementIT`**, no en `CommissionAccrualIT` y `CommissionKindSchemaIT`: necesitan una línea FTD y un cierre, que esta suite ya siembra.
- **`CA-CM-252` se provoca con un escalón cuyo `límite × valor` desborda `numeric(14,4)`**, como pedía el plan; el cierre a mano responde `500` y no queda nada.
- **`AfftrackTierPickerTest`** cubre las cuatro filas de `spec.md` §2.1, el límite exacto, el escalón de cero y el mayor que paga menos.

---

## 4. Bloqueos

**`RF-CM-015`** —migración y `ftdProductIds`— y **`RF-CM-019`** —el predicado de vigencia—.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los catorce criterios de aceptación con prueba.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. El valor por FTD y la liquidación en centésimas — enmienda del 05-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-10` | `@Convert` en `afftrack_rates.amount_per_ftd` y `user_afftrack_rates.amount_per_ftd`. Revisión del SQL nativo de las escalas y de la liquidación (`plan.md` §12). **Rehacer la provocación de `CA-CM-252`** (ver abajo) | `RF-MV-001` `T-40` (el convertidor) y `T-41` (`V65`); `RF-CM-001` `T-36`; `RF-CM-013` `T-20` | Compila; `CA-CM-252` vuelve a fallar a propósito y la suite deja el esquema como lo encontró | **Hecha el 05-10-2026** |
| `T-11` | Pruebas: `CA-CM-338` en `AfftrackSettlementIT` y en las suites de alta de las dos escalas; las que siembran escalones por SQL escriben centésimas | `T-10` | `./mvnw verify` en verde | **Hecha el 05-10-2026** |
| `T-12` | **Prueba de `CA-CM-339`** en `AfftrackSettlementIT`: una venta del alta confirmada con la línea `PENDIENTE` no cuenta en un cierre; tras entregarla con `delivered_at` posterior a ese cierre, cuenta en el siguiente. **Sin código de producción** (05-10-2026) | `RF-MV-001` `T-46` | `CA-CM-339` | **Hecha el 05-10-2026** |

**`CA-CM-252` se provocaba con un dato que ya no provoca nada.** Era un escalón cuyo `límite × valor` no cabía en `numeric(14,4)` al escribir la comisión. Con `bigint` en centésimas el techo es de unos 9,2·10¹⁶ unidades de dinero, y **ningún dato admitido llega a él**. Por porcentaje, la comisión no pasa del importe de la línea (`RN-CM-019`), y la línea desborda antes. Por fijo, `CA-CM-161` rechaza la cadena que pasa del importe de la línea. En afftrack, el valor por FTD tiene diez cifras enteras como mucho y el límite hay que alcanzarlo en FTD contados. **La prueba dejaría de fallar, y pasaría por la razón equivocada.**

**Propuesta, sin decidir aquí si hace falta código de producción:** que la suite añada al empezar una restricción **solo de prueba** sobre `commissions` (por ejemplo `ALTER TABLE commissions ADD CONSTRAINT ck_prueba_fallo CHECK (commission_amount <> <importe testigo>) NOT VALID`), que la provoque con ese importe exacto y que la retire en un `finally` y en la limpieza **al terminar**, no solo al empezar. Con `NOT VALID` no se revisan las filas existentes, y la restricción no sale de la suite. Se descarta `@MockitoSpyBean` por lo que ya se dijo: un contexto de Spring más agota las conexiones de la suite. Si se prefiere que el fallo nazca en producción —un techo de dominio para la comisión, con su `EX`—, eso es una regla de negocio nueva y pasa antes por `cm.md`. **Conviene resolverlo igual que `CA-CM-166` (`RF-CM-013` `T-22`).**
