# TASKS — `RF-IN-005` Consultar el resumen de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-005` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-06` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **`V76`**: `indicators:read-points-summary` (`…-5e7ad8000005`) a `FUNCIONARIO` y `VENDEDOR` por tipo; guardas 197 / 197 / 195 / ningún `CONSUMIDOR` / contención | — | Recuentos del catálogo a 197; `SystemRolesSeedIT` y `IntegrationTestBase` con el permiso | **Hecha** — 06-10-2026 |
| `T-02` | `MV` publica `PointsFigures`; `JpaPointsFigures` con las dos sentencias de `plan.md` §4.4 y el mapeo que falla ante un evento desconocido | — | Compila; no importa nada de `IN` | **Hecha** — 06-10-2026 |
| `T-03` | `PointsSummaryResponse` con `@Schema` propios; `GetPointsSummaryService` con el corte a ceros y la unión por moneda | `T-02` | Monedas por código; todo en positivo | **Hecha** — 06-10-2026 |
| `T-04` | `PointsIndicatorsController`: `GET /api/v1/indicators/points/summary`, documentado | `T-03` | La ruta en `PERMISO_DE_CADA_OPERACION` | **Hecha** — 06-10-2026 |
| `T-05` | `PointsSummaryIT`: `CA-IN-041` a `CA-IN-049`, con compras, pagos y ajustes por las rutas de `MV` | `T-01`, `T-04` | `CA-IN-044` cuadra | **Hecha** — 06-10-2026 |
| `T-06` | Contrato regenerado y prosa releída; `api/index.md`, `requirements/mv.md` §3, `architecture.md` §15.2, `security.md` (sembrado), `requirements/in.md` y matriz | `T-05` | Solo altas en el contrato | **Hecha** — 06-10-2026 |

### 1.1 Sin fechas, todo; y los tramos — 06-10-2026 (`RN-IN-010`)

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-07` | `PointsFigures.flowsByBucket`; `GetPointsSummaryService` con el periodo abierto, `granularity` y `buckets` | `RF-IN-001` · `T-14` | El saldo solo en el total | **Hecha** — 06-10-2026 |
| `T-08` | `PointsSummaryIT`: `CA-IN-057` y `CA-IN-058`; contrato y documentos | `T-07` | | **Hecha** — 06-10-2026 |
| `T-09` | **El saldo al cierre del periodo** (`spec.md` 0.3.0): `PointsFigures.balances` recibe el `Interval` y suma los asientos hasta su fin; `GetPointsSummaryService` lo pasa; prosa del controlador | `T-07` | `CA-IN-044` sigue cuadrando | **Hecha** — 07-10-2026 |
| `T-10` | `PointsSummaryIT`: `CA-IN-071` y el `CA-IN-044` enmendado | `T-09` | Suite en verde | **Hecha** — 07-10-2026 |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-041` a `CA-IN-045` | `T-02`, `T-05` |
| `CA-IN-046`, `CA-IN-047` | `T-03`, `T-05` |
| `CA-IN-048` | `T-02`, `T-05` |
| `CA-IN-049` | `T-01`, `T-04`, `T-05` |

---

## 3. Desviaciones respecto del plan

**Fuera del alcance, o sin nada que contar, la lista de monedas sale vacía**, y no una moneda con ceros: «ceros» de la spec (§9) es eso, porque sin moneda no hay de qué dar ceros.

**`PointsFigures` devuelve los puntos de cada clase con su signo quitado y el número de movimientos**, y un evento desconocido sobre `PUNTOS` lanza un fallo en el mapeo (`plan.md` §4.4). El parámetro de persona es **`userId`**, como dice el plan.

**`PointsSummaryIT` hace cada movimiento por su ruta de `MV`** —comprar, confirmar, rechazar, pagar por el enlace con `POINTS`, ajustar—, y solo toca la base para mover en el tiempo los asientos de una compra (`CA-IN-048`).

---

## 4. Bloqueos declarados

Ninguno.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde — 561 unitarias y 2525 de integración (06-10-2026).
- [x] Los nueve criterios de aceptación con prueba (`PointsSummaryIT`).
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas.
- [x] `requirements/in.md`, `requirements/mv.md`, `architecture.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (06-10-2026).
| — | 07-10-2026 | `T-09` y `T-10`, el saldo al cierre del periodo, pedidos y aprobados por el responsable del proyecto («aplica que el saldo también dependa del periodo»). | Responsable técnico |
| — | 07-10-2026 | `T-09` y `T-10` `Hecha`: el saldo suma los asientos hasta el fin del intervalo; `PointsSummaryIT.periodo` prueba `CA-IN-071`. Suite completa en verde (567 unitarias, 2582 de integración). | Responsable técnico |
