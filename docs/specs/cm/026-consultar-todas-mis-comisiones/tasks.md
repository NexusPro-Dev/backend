# TASKS — `RF-CM-026` Consultar todas mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-026` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 07-10-2026 |
| Estado | **En revisión** — todas las tareas `Hecha` el 07-10-2026 |
| Issue | Pendiente de crear |
| Rama | `develop` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V81`**: el permiso a todo rol que porte `commission-batches:list-own`, `ix_commissions_user`, y las guardas; el catálogo 204 → 205 | — | Las suites de siembra y `PermissionIT` en verde | **Hecha** — 07-10-2026 |
| `T-02` | `searchOwn` y `countOwn` en el repositorio de consulta, reutilizando `COMISION` y `comision(f)` | `T-01` | — | **Hecha** — 07-10-2026 |
| `T-03` | `MyCommissionsRequest`, `MyCommissionItem` y `MyCommissionPageResponse`, con `@Schema(name)` | — | — | **Hecha** — 07-10-2026 |
| `T-04` | `CommissionBatchQueryService.listOwnCommissions`: los `400` juntos | `T-02`, `T-03` | — | **Hecha** — 07-10-2026 |
| `T-05` | `GET /mine/commissions`, en `PERMISO_DE_CADA_OPERACION` | `T-04` | `EndpointPermissionsIT` | **Hecha** — 07-10-2026 |
| `T-06` | `MyCommissionsIT`: `CA-CM-347` a `CA-CM-355` | `T-05` | — | **Hecha** — 07-10-2026 |
| `T-07` | Contrato OpenAPI (`api/index.md`); `security.md` (sembrado) y `requirements.md` | `T-06` | Diff del `json` | **Hecha** — 07-10-2026 |

### 1.1 Filtro por cliente — 07-10-2026

Enmienda de hecho, `spec.md` 0.2.0 y `plan.md` 0.2.0 **antes** del código.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-08` | `clientId` en `MyCommissionsRequest`, `OwnFilter` y `PROPIAS_DESDE`; la prosa de la operación | — | Sin `clientId`, la misma lista | **Hecha** — 07-10-2026 |
| `T-09` | `MyCommissionsIT`: `CA-CM-361` y `CA-CM-362`; contrato, `api/index.md`, `requirements/cm.md` y matriz | `T-08` | Solo altas | **Hecha** — 07-10-2026. La parte de `CA-CM-362` sobre `POR_AFFTRACK` se prueba en `AfftrackSettlementIT` (`lasMiasSinLote`), como la de `CA-CM-351` |

---

## 2. Orden de ejecución

`T-01` → `T-03` → `T-02` → `T-04` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-CM-347` a `CA-CM-354` | `T-02`, `T-04`, `T-06` |
| `CA-CM-355` | `T-01`, `T-05`, `T-06` |
| `CA-CM-361`, `CA-CM-362` | `T-08`, `T-09` — 07-10-2026 |

### 3.1 Lo que la construcción cambió respecto del plan

- **La fila `POR_AFFTRACK` de `CA-CM-351` se prueba en `AfftrackSettlementIT`** (`lasMiasSinLote`) y no en `MyCommissionsIT`: aquella suite ya sabe sembrar un escalón, sus FTD y el cierre que la paga. `MyCommissionsIT` prueba el resto del criterio.
- **El cliente de la venta es `movements.user_id`**, no `client_id`: `V12` lo renombró al pasar el sujeto a la venta. El plan no nombraba la columna; queda dicho aquí.

---

## 4. Bloqueos

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los nueve criterios de aceptación con prueba.
- [ ] Contrato, `security.md` y `requirements.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
