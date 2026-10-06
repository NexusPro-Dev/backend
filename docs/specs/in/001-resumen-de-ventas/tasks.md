# TASKS — `RF-IN-001` Consultar el resumen de ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-001` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| Plan | [`plan.md`](plan.md) v0.2.0 |
| `plan.md` aprobado el | 06-10-2026 |
| Estado | **Aprobadas** — por el responsable del proyecto, 06-10-2026. `T-01` a `T-10` `Hecha` el mismo día |
| Issue | Pendiente de crear |
| Rama | `develop` (commits directos desde el 05-10-2026) |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **Migración de los cuatro permisos** (`V74` o la siguiente libre): serie `5e7ad8`, reparto a `FUNCIONARIO` y `VENDEDOR`, guardas del catálogo, de `SUPERADMIN` y `ADMIN`, de `CONSUMIDOR` sin ninguno y de contención | — | `PermissionsSeedIT`, `PermissionIT` y los recuentos del catálogo con el número nuevo; `IntegrationTestBase` repone los cuatro donde reponga los de tipo de rol | **Hecha** — 06-10-2026 |
| `T-02` | `MV` publica **`SalesFigures`** con `SalesScope`, `Interval` y `Summary`; javadoc con qué cuenta y qué no (solo `VENTA`, por línea, sin vendedor solo con `everything()`) | — | Compila; la interfaz no importa nada de `IN` ni de `SP` | **Hecha** — 06-10-2026 |
| `T-03` | `JpaSalesFigures.summary`: la sentencia de `plan.md` §4.4, de centésimas a decimales con `MinorUnits` al mapear | `T-02` | `SalesFiguresIT` en `MV`: por línea, una venta por `DISTINCT`, las tres situaciones, la moneda, el intervalo semiabierto | **Hecha** — 06-10-2026 |
| `T-04` | `SalesPeriodResolver`: por defecto, solo `from`, solo `to`, `VAL-001` a `VAL-003`, y el `Interval` en la zona de `BusinessCalendar` | — | `SalesPeriodResolverTest`: mes en curso, `to` solo, 366 sí y 367 no, bisiesto, día único, medianoche de Bogotá | **Hecha** — 06-10-2026 |
| `T-05` | `SalesScopeResolver`: la tabla de `plan.md` §3.2 | — | `SalesScopeResolverTest`, las nueve casillas, con `CommercialReach` doblado | **Hecha** — 06-10-2026 |
| `T-06` | `SalesIndicatorRequest`, `SalesSummaryResponse`, `IndicatorPeriod` con `@Schema` propios; `GetSalesSummaryService` con el corte a ceros sin consultar | `T-03`, `T-04`, `T-05` | El corte se ve por HTTP: ceros con `sellerId` ajeno | **Hecha** — 06-10-2026 |
| `T-07` | `SalesIndicatorsController`: `GET /api/v1/indicators/sales/summary`, documentado —qué ve cada tipo de rol, por qué puede no cuadrar con el listado, ceros fuera del alcance, un importe por moneda— | `T-06` | La ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT` | **Hecha** — 06-10-2026 |
| `T-08` | `SalesSummaryIT`: `CA-IN-001` a `CA-IN-014` sobre el árbol de `SalesIT` con importes distintos por línea y una venta de dos ramas | `T-01`, `T-07` | Cada nivel ve exactamente su suma; coste: una sentencia de suma con red de 2 y de 20 | **Hecha** — 06-10-2026 |
| `T-09` | `LayerRulesTest`: `IN` no depende de `domain` de otro módulo y nadie depende de `IN` | `T-07` | La regla falla si se rompe a propósito | **Hecha** — 06-10-2026 |
| `T-10` | Contrato OpenAPI regenerado y prosa releída; `docs/api/index.md`; `requirements/in.md` y `mv.md` (§3, `SalesFigures`); `architecture.md` §15.2; `security.md` §4.4 (sembrados); matriz | `T-08` | `openapi.json` declara la ruta con `x-required-permission` y ningún esquema fundido | **Hecha** — 06-10-2026 |

### 1.1 El total y las gratuitas — 06-10-2026

Enmienda de hecho (Art. I.7), `spec.md` 0.2.0 y `plan.md` 0.2.0 **antes** del código, por decisión del responsable del proyecto.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-11` | `SalesFigures.Totals` gana `free`; `JpaSalesFigures.summary` cuenta las gratuitas con `FILTER (WHERE m.payable_amount = 0)` | — | La serie de `RF-IN-002` sigue sumando lo confirmado | Pendiente |
| `T-12` | `SalesSummaryResponse` gana `total` (`sales`, `free`) y `free` en cada estado; `GetSalesSummaryService` suma el total | `T-11` | `@Schema` propio para `total` | Pendiente |
| `T-13` | `SalesSummaryIT`: `CA-IN-038` a `CA-IN-040`; contrato regenerado; `api/index.md`, matriz | `T-12` | Las cifras de antes no cambian | Pendiente |

---

## 2. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-IN-001` a `CA-IN-004` | `T-03`, `T-05`, `T-08` |
| `CA-IN-005`, `CA-IN-006` | `T-03`, `T-08` |
| `CA-IN-007`, `CA-IN-008` | `T-03`, `T-08` |
| `CA-IN-009`, `CA-IN-010` | `T-04`, `T-08` |
| `CA-IN-011`, `CA-IN-012` | `T-05`, `T-06`, `T-08` |
| `CA-IN-013` | `T-04`, `T-06`, `T-08` |
| `CA-IN-014` | `T-01`, `T-07`, `T-08` |
| `CA-IN-038` a `CA-IN-040` | `T-11` a `T-13` — 06-10-2026 |

---

## 3. Desviaciones respecto del plan

**`SalesFiguresIT` no existe: la suma se prueba por HTTP** (`T-03`). Cada caso que la tarea le asignaba —por línea, una venta por `DISTINCT`, las tres situaciones, la moneda, el intervalo semiabierto— lo cubre `SalesSummaryIT` con cifras que solo cuadran si la sentencia suma bien, y una segunda suite sobre el mismo árbol habría duplicado la semilla sin probar nada más. Es la misma decisión que `RF-MV-015` · `T-05`. `RF-IN-002` a `RF-IN-004` añadirán sus casos a sus propias suites HTTP.

**Una fecha o un identificador mal formados son `VAL-001`, y no se devuelven junto a los demás** (`spec.md` §11 los separaba en `VAL-001` y `VAL-004`). Los rechaza la conversión de Spring antes de llegar al caso de uso, con el manejador común del sistema (`GlobalExceptionHandler`), que es como responde toda ruta con parámetros tipados; reproducirlo en `IN` exigiría recibir texto y convertirlo a mano. `VAL-004` queda sin uso. Los dos de negocio —`VAL-002` y `VAL-003`— no pueden darse a la vez, de modo que «juntos» no tiene caso que probar.

**El reparto se comprueba en `SalesSummaryIT`** (`elRepartoDeV74`): solo `FUNCIONARIO` y `VENDEDOR` portan el permiso. Los recuentos del catálogo —189, `ADMIN` 187— suben en las seis suites que los cuentan, e `IntegrationTestBase` repone los cuatro en `reponerAlcancePropio` y los cuenta en `ALCANCE_PROPIO`.

---

## 4. Bloqueos declarados

1. ~~**El número de la migración**~~ — resuelto: es `V74`; el doble factor de `SP` toma `V75` y cuenta 189 más los suyos.
2. **La pregunta de §14 de la spec** —contar por confirmación y no por ocurrencia— no bloquea; se decide con el responsable si se pide.

---

## 5. Definición de terminado

- [x] `./mvnw clean verify` en verde — 540 unitarias y 2459 de integración; el único fallo de la primera pasada, `SystemRolesSeedIT`, fijaba el conjunto exacto de cada rol vendedor y gana los cuatro de `V74`.
- [x] Los catorce criterios de aceptación con prueba (`SalesSummaryIT`, 12).
- [x] Contrato OpenAPI regenerado, **con la prosa releída**: solo altas, ningún esquema fundido.
- [x] `requirements/in.md`, `requirements/mv.md`, `architecture.md`, `security.md`, `requirements.md` y `api/index.md` actualizados.
- [x] **`tasks.md` aprobadas por el responsable del proyecto** (06-10-2026).
