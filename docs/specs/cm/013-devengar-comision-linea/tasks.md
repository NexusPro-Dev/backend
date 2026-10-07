# TASKS — `RF-CM-013` Devengar las comisiones de una línea de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-013` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 28-09-2026 |
| Estado | **En revisión** — `T-01` a `T-12` `Hecha` el 28-09-2026; `T-13` en curso |
| Enmendadas | 29-09-2026 — `T-14` por **las líneas FTD fuera del devengo** (`RN-CM-022`) |
| Enmendadas | 29-09-2026 — `T-15` a `T-17` por **la comisión por venta directa** (`RN-CM-045`) |
| Enmendadas | 30-09-2026 — `T-18` por **la línea revertida** (`RN-CM-047`) |
| Enmendadas | 07-10-2026 — `T-23` y `T-24` por **la línea reatribuida al lote más reciente sin pagar** (`RN-CM-051`) |
| Enmendadas | 05-10-2026 — `T-20` a `T-22` porque **la comisión se guarda en centésimas** (`ADR-006`) |
| Enmendadas | 07-10-2026 — la cadena vieja se borra: lo prueba `RF-CM-024` `T-10` (`RN-CM-047`) |
| Issue | **Hecha** — 05-10-2026 de crear |
| Rama | `feature/devengo-de-comisiones` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `V51__cm_devengo_de_comisiones.sql`: las cuatro tablas con sus restricciones, `ix_commission_batches_abierto`, y los ocho permisos con sus concesiones (`plan.md` §2) | — | `mvn clean verify` aplica la migración desde cero | **Hecha** — 28-09-2026 |
| `T-02` | Los seis recuentos del catálogo, 145 → 153 —`PermissionIT` con `153L`— y la suite de siembra de `CM` con los ocho códigos y quién los recibe | `T-01` | Las seis en verde | **Hecha** — 28-09-2026 |
| `T-03` | Ayudante común de limpieza de prueba que borra `commission_accruals`, `commissions`, `commission_batches` y `commission_closings` **antes** de `movements`; usarlo en las 21 suites que hacen `DELETE FROM movements` | `T-01` | `mvn clean verify` completo en verde, con el orden alfabético | **Hecha** — 28-09-2026 |
| `T-04` | `BusinessCalendar` en `shared/time`, con `nexus.business.zone` | — | Unitarias con el reloj a las `01:00Z` | **Hecha** — 28-09-2026 |
| `T-05` | `ResolveCommissionService`: «hoy» por `BusinessCalendar`; la operación interna que devuelve la tasa con su identidad | `T-04` | La suite de `RF-CM-005` sigue en verde; `CA-CM-159` | **Hecha** — 28-09-2026 |
| `T-06` | `SupervisorChain` en `SP` y su adaptador | — | `SupervisorChainIT` | **Hecha** — 28-09-2026 |
| `T-07` | `CommissionableLines` y `CommissionableLinesEvent` en `MV`; `ConfirmSaleService` y `AssignSellersService` publican | — | Las suites de `RF-MV-003` y `RF-MV-016` siguen en verde | **Hecha** — 28-09-2026 |
| `T-08` | `ChainCommissionCalculator` | — | Unitarias: `CA-CM-160`, `CA-CM-161`, casos límite de `spec.md` §13 | **Hecha** — 28-09-2026 |
| `T-09` | `AccrualOutcome`, `BatchStatus`; `CommissionAccrualRepository`, `CommissionBatchRepository` y adaptadores, con la secuencia del lote abierto (`plan.md` §4) | `T-01` | Integración del repositorio: dos aperturas simultáneas dejan un solo lote | **Hecha** — 28-09-2026 |
| `T-10` | `CommissionAccrualService`: bloqueo, relectura, cadena, cálculo, desenlace, lote, auditoría; una transacción por línea; `retryRejected()` | `T-05` a `T-09` | — | **Hecha** — 28-09-2026 |
| `T-11` | `CommissionableLinesListener` | `T-07`, `T-10` | — | **Hecha** — 28-09-2026 |
| `T-12` | `CommissionAccrualIT`: `CA-CM-154` a `CA-CM-169`, por la API de `MV` | `T-03`, `T-11` | `CA-CM-164` y `CA-CM-165` con dos hilos | **Hecha** — 28-09-2026 |
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

## 3.1 Desviaciones respecto del plan

- **`T-14`**: `CA-CM-253` se prueba en `AfftrackSettlementIT`. `CommissionAccrualService` pide `ftdProductIds()` una vez por llamada a `accrue` o `retryRejected`, no por línea.

- **`lockOpenBatch` devuelve el lote y el inicio de su periodo**, y el lote nuevo empieza **como pronto en el fin del último cerrado** (`GREATEST`). Un devengo que tomó su instante justo antes de un cierre y lo esperó en el bloqueo abriría, si no, un lote que empieza **dentro** del cerrado, y `ex_commission_batches_solape` lo rechazaría tres veces seguidas. La comisión toma ese mismo instante como `accrued_at`: es cuándo entró en su lote. No estaba en `plan.md` §4.
- **`ConfirmSaleService` publica todas las líneas de la venta**, y no solo las que tienen vendedor: cuáles comisionan lo decide `CM` al releerlas (`plan.md` §1, «se relee, no se confía en el aviso»). Así `MV` no repite el predicado de `RN-CM-022`.
- **La auditoría usa `ChangeAction.CREATE`**, no `INSERT` como decía `plan.md` §7: es el valor que el enum del sistema tiene.
- **`CA-CM-166` se provoca con datos y no con un doble**: una línea de cien mil millones al 10 % cuya comisión no cabe en `numeric(14,4)`. Un `@MockitoSpyBean` —lo primero que se probó— crea un contexto de Spring más, y con su pool agotó las conexiones de Postgres de la suite (`too many clients already` en `RateLimitIT`, otra suite). Tampoco sirve una persona con dos roles vendedores: `uq_user_roles_vendedor` ya lo impide en el esquema, de modo que `EX-002` de `spec.md` **no puede ocurrir** hoy; se deja escrita por si el índice se retira.
- **Dos suites de roles cambian**, además de las seis del catálogo: `SystemRolesSeedIT` y `RoleDetailIT` afirman el conjunto **exacto** de permisos de cada rol vendedor, y ganan `commission-batches:list-own` y `read-own`.
- **`CommissionCleanup` vive en `src/test/.../testing`**, junto a `ConcurrencyHarness`, y lo usan las 21 suites que limpian `movements`.

## 4. Bloqueos

**Ninguno.** `RF-MV-024` (`CommissionPayout`) ya está construido, pero no lo usa este requerimiento sino `RF-CM-011`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde, **completo**.
- [ ] Los dieciséis criterios de aceptación con prueba.
- [ ] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**

## 6. Las líneas FTD, fuera — enmienda del 29-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-14` | El descarte por `ftdProductIds()` en `CommissionAccrualService` y `CA-CM-253` en `CommissionAccrualIT` (`plan.md` §12) | `RF-CM-015` `T-02`, `T-04` | `CommissionAccrualIT` y `CloseCommissionPeriodIT` en verde | **Hecha** — 29-09-2026 |

Rama: `feature/comision-afftrack`. **Es la misma tarea que `RF-CM-020` `T-02`**, vista desde aquí.

## 7. La comisión por venta directa — enmienda del 29-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-15` | `LastLinkRoles` en `SP` con su implementación en `PublishedUserCatalog` | — | `LastLinkRolesIT` | Hecha |
| `T-16` | `ProductCatalog.directCommissionOf` en `PM` | `RF-PM-001` `T-47` | Prueba del adaptador: directa de cada forma, y vacío en un FTD | Hecha |
| `T-17` | `RateSource.DIRECTA`, la sustitución en el nivel `0` de `CommissionAccrualService` y la prosa de la `@Operation` de `RF-CM-005` | `T-15`, `T-16` | `CommissionAccrualIT`: `CA-CM-264` a `CA-CM-270` | Hecha |

Rama: `feature/comision-venta-directa`.

**`T-16` no tiene prueba de adaptador propia**: `directCommissionOf` se ejerce en `CA-CM-264` (porcentaje) y `CA-CM-267` (fijo), y un producto sin directa —sembrado por SQL, como en el resto de la suite— deja la tasa resuelta como estaba (29-09-2026).

## 8. La línea revertida — enmienda del 30-09-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-18` | `CA-CM-304` y `CA-CM-305` en `CommissionAccrualIT` (`plan.md` §14) | `RF-CM-024` `T-03`, `RF-MV-016` `T-13` | `CommissionAccrualIT` en verde | **Hecha** — 30-09-2026 |

Rama: `feature/corregir-vendedor-y-mover-comisiones`.

## 9. La directa de la tasa de rol — enmienda del 05-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-19` | `ventaPropia` lee la directa de la tasa de rol del vendedor (`plan.md` §15) | `RF-CM-001` `T-34` | `CommissionAccrualIT`: `CA-CM-264` a `CA-CM-270` reescritas sobre la tasa, y `CA-CM-328` a `CA-CM-330` | Pendiente |

Rama: `develop`.

## 10. La comisión y el lote en centésimas — enmienda del 05-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-20` | `@Convert` en `commissions` (`fixed_amount`, `unit_price`, `commission_amount`) y `commission_batches.total_amount`. **La fila se construye con el importe redondeado a dos con `HALF_UP`**, después de comprobar `RN-CM-019` con los cuatro. Revisión del SQL nativo de comisiones, lotes, cierre y resolución: se vincula y se mapea en centésimas (`plan.md` §16) | `RF-MV-001` `T-40` (el convertidor) y `T-41` (`V65`); `T-19` | Compila; unitaria del redondeo: `0.005` → `0.01`, `0.0049` → `0.00` | **Hecha el 05-10-2026** |
| `T-21` | Pruebas: `CA-CM-336` y `CA-CM-337` en `CommissionAccrualIT`. Las suites que siembran comisiones o lotes por SQL (`CommissionCleanup` y sus usuarias) escriben centésimas | `T-20` | `./mvnw verify` en verde | **Hecha el 05-10-2026** |
| `T-22` | **Rehacer la provocación de `CA-CM-166`**, que ya no falla con el dato de §3.1 (ver abajo) | `T-20` | `CA-CM-166` vuelve a fallar a propósito en la segunda línea, y la suite deja el esquema como lo encontró | **Hecha el 05-10-2026** |

**`CA-CM-166` se provocaba con un dato que ya no provoca nada.** Era una línea de cien mil millones al 10 %, cuya comisión, diez mil millones, no cabía en `numeric(14,4)`. Con `bigint` en centésimas el techo es de unos 9,2·10¹⁶ unidades de dinero, y **ningún dato admitido llega a él**. Por porcentaje, la comisión no pasa del importe de la línea (`RN-CM-019`), y la línea desborda antes. Por fijo, `CA-CM-161` rechaza la cadena que pasa del importe de la línea. En afftrack, el valor por FTD tiene diez cifras enteras como mucho y el límite hay que alcanzarlo en FTD contados. **La prueba dejaría de fallar, y pasaría por la razón equivocada.**

**Propuesta, sin decidir aquí si hace falta código de producción:** que la suite añada al empezar una restricción **solo de prueba** sobre `commissions` (por ejemplo `ALTER TABLE commissions ADD CONSTRAINT ck_prueba_fallo CHECK (commission_amount <> <importe testigo>) NOT VALID`), que la provoque con ese importe exacto y que la retire en un `finally` y en la limpieza **al terminar**, no solo al empezar. Con `NOT VALID` no se revisan las filas existentes, y la restricción no sale de la suite. Se descarta `@MockitoSpyBean` por lo que ya se dijo: un contexto de Spring más agota las conexiones de la suite. Si se prefiere que el fallo nazca en producción —un techo de dominio para la comisión, con su `EX`—, eso es una regla de negocio nueva y pasa antes por `cm.md`. **`AfftrackSettlementIT` (`RF-CM-020`) tiene el mismo problema con `CA-CM-252`, y conviene resolver los dos de la misma forma.**

Rama: `develop`.

## 11. La línea reatribuida va al lote más reciente sin pagar — enmienda del 07-10-2026

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-23` | `lockLatestUnpaidBatch` en `CommissionBatchRepository`; `CommissionAccrualService` elige el lote por la marca y la borra (`plan.md` §18) | `RF-CM-024` `T-13` | — | Pendiente |
| `T-24` | `CA-CM-359` en `ReleaseCommissionedLineIT` | `T-23` | La suite de `CM` en verde | Pendiente |
