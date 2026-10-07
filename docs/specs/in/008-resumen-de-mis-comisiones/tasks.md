# TASKS — `RF-IN-008` Consultar el resumen de mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-008` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 07-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 07-10-2026. `T-01` a `T-06` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V83`**: `indicators:read-own-commissions-summary` (`…-5e7ad8000008`) a todo rol que porte `commission-batches:list-own`; guardas 206 / los mismos roles / contención. Los recuentos del catálogo, `SystemRolesSeedIT` y `RoleDetailIT` | — | Recuento a 206 | **Hecha** — 07-10-2026 |
| `T-02` | `CommissionBatchFigures.commissionsByStatus`, `CommissionFilter` y `CommissionTotals`; `JpaCommissionBatchFigures` con la sentencia de `plan.md` §4.3 | — | La persona es obligatoria | **Hecha** — 07-10-2026 |
| `T-03` | `OwnCommissionsSummaryResponse`; `GetOwnCommissionsSummaryService` —periodo, persona del token, los tres estados siempre, un estado desconocido al total—; `GetOwnCommissionsSummaryServiceTest` | `T-02` | Los tres bloques presentes con ceros | **Hecha** — 07-10-2026 |
| `T-04` | `CommissionIndicatorsController`: `GET /api/v1/indicators/commissions/mine/summary`, documentado | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | **Hecha** — 07-10-2026 |
| `T-05` | `OwnCommissionsSummaryIT`: `CA-IN-090` a `CA-IN-096`, limpiando lo que siembra | `T-01`, `T-04` | Una sentencia por lectura | **Hecha** — 07-10-2026 |
| `T-06` | Contrato regenerado; `api/index.md`, `requirements/cm.md` §3, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas | **Hecha** — 07-10-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-090`, `CA-IN-092` | `T-02`, `T-03`, `T-05` |
| `CA-IN-091` | `T-03`, `T-05` |
| `CA-IN-093` a `CA-IN-095` | `T-02`, `T-05` |
| `CA-IN-096` | `T-01`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

**`OwnCommissionsSummaryIT` vive en `commissions/interfaces` y no en `indicators/interfaces`** (`T-05`): las comisiones nacen confirmando ventas por la API de `MV`, con `SettlementFixtures` y `CommissionFixtures`, que son de ese paquete y no públicos. Sembrar comisiones por SQL habría obligado a sembrar líneas de venta y tasas a mano para probar una suma.

**`CA-IN-095` fija las fechas de nacimiento por SQL**: el devengo las pone en el instante de la confirmación, y la prueba necesita dos días distintos y los bordes de Bogotá.

---

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde — **solo las suites afectadas** (07-10-2026): `OwnCommissionsSummaryIT` (7), los recuentos del catálogo, los de roles, `EndpointPermissionsIT`, `OpenApiContractIT`, `CommissionBatchesSummaryIT` y `MyCommissionsIT`; 129 de integración y 5 unitarias.
- [x] Los siete criterios de aceptación con prueba (`OwnCommissionsSummaryIT`, 7).
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas.
- [x] `requirements/in.md`, `requirements/cm.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (07-10-2026).
