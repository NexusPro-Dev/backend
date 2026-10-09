# PLAN — `RF-IN-001` Consultar el resumen de ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-001` |
| Especificación | [`spec.md`](spec.md) v0.4.0 |
| `spec.md` aprobada el | 06-10-2026; enmienda del 09-10-2026 por decisión del responsable del proyecto |
| Versión | 0.4.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! warning "Enmendado el 06-10-2026 — sin fechas, todo; y cada indicador se puede partir en tramos (RN-IN-010)"

    Decisión del responsable del proyecto, 06-10-2026: «los indicadores se recogen en su totalidad a no ser que se les envíe una fecha en los filtros», y «tener la capacidad de pedir los indicadores por meses, por días y por semanas, y adicionalmente un filtro de inicio y fin; si van vacíos se consulta todo». **Cómo se construye.** `SalesFigures.Interval` admite **«desde» nulo** —sin límite inferior— y el predicado lo omite; «hasta» sin fecha es el comienzo de mañana en Bogotá, que es «hasta hoy». `SalesPeriodResolver` deja de poner el primero del mes y de comprobar los 366 días, y devuelve `from` nulo cuando no se pidió. **Los tramos** son una segunda sentencia, la de §4.4 agrupada además por `date_trunc` sobre la hora de Bogotá —la de `RF-IN-002`—, solo si se pide tramo; `IN` los cruza con el calendario de `SalesCalendar`, que **sin «desde» arranca en el tramo del primer dato devuelto** y llega al de «hasta» o al de hoy. En la respuesta, `granularity` y `buckets` son **nulos** sin tramo, para que el contrato de antes no cambie. `period.from` puede venir nulo.

!!! warning "Enmendado el 06-10-2026 — el total de ventas y las gratuitas"

    Por decisión del responsable del proyecto: el indicador de ventas tiene que dar **el número de ventas**, **el total por estado** y **cuántas fueron gratuitas**. El total por estado ya estaba; faltaban los otros dos, y entran **en este mismo resumen**, con su permiso y su ruta, en lugar de en un indicador nuevo. **Cómo se construye.** La sentencia de §4.4 gana una columna, `count(DISTINCT m.id) FILTER (WHERE m.payable_amount = 0)`, en el mismo `GROUP BY` por estado y moneda: la gratuidad se mira en la **cabecera** —lo que se cobra por la venta entera— y no en la suma de las líneas del alcance. `SalesFigures.Totals` gana `free`; `IN` suma el total en Java, como ya suma las ventas entre monedas, porque los tres estados no se solapan. En la respuesta, `total` es un bloque nuevo —`sales` y `free`— y cada bloque de estado gana `free`. **Sin cambio de esquema ni de permisos**; el contrato solo crece. Se mira `payable_amount` y no el método de pago porque el método vive en `payments`, uno por intento, y `RN-MV-022` ya garantiza que importe cero y `GRATIS` son lo mismo.

!!! warning "Enmendado el 09-10-2026 — `teamId`, la oficina guardada en la línea (RN-IN-014)"

    `spec.md` v0.4.0. **La columna la pone `MV`**: `movement_details.team_id`, con su clave foránea a `teams` y el índice parcial `(team_id, movement_id)` sobre `team_id IS NOT NULL`, nacen en `V95__mv_oficina_de_la_venta.sql` con `RN-MV-078` (tripletas de `RF-MV-001` y `RF-MV-058`); **este requerimiento no migra nada** y se construye **después** de esa columna. **`SalesFigures.LineFilter` gana un quinto componente, `UUID teamId`**, y `none()` lo deja nulo; el predicado vive donde viven los otros cuatro —`donde` y `enlazar` de `JpaSalesFigures`—: `AND d.team_id = :oficina`, sobre la línea y no sobre la cabecera, que es `RN-IN-003` entero: una venta con líneas de dos oficinas cuenta una vez en cada una con solo su parte (`count(DISTINCT m.id)` y la suma de las líneas filtradas). **`SalesIndicatorRequest` gana `teamId`**, y `SalesIndicatorsController` recibe `@RequestParam(required = false) UUID teamId` en `/sales/summary`. **`GetSalesSummaryService` deja de llamar a las lecturas sin filtro**: pasa `new LineFilter(null, null, null, null, teamId)` a las sobrecargas de `summary` y `summaryByBucket` que `RF-IN-006` publicó el 07-10-2026, junto al `SalesScope` de siempre. **El filtro se combina con el alcance y no pasa por `SalesScopeResolver`**: el alcance sigue siendo «sobre qué vendedores», y la oficina estrecha las líneas de esos vendedores; el corte a ceros sin consultar sigue siendo solo el del vendedor fuera del alcance. Un agente que pide otra oficina no necesita corte: sus líneas guardan la suya, y la sentencia da cero. **Lo sin vendedor sale solo**: con `everything()` las líneas sin vendedor entran, pero su `team_id` es nulo y `d.team_id = :oficina` las deja fuera, sin un caso aparte; igual la venta de un manager. **Una oficina inexistente da ceros por la sentencia**: `IN` no lee `teams` (`requirements/in.md` §1.4) y no hay `404`. **Un UUID mal formado** es el `400` de conversión de Spring, `VAL-001`, como `sellerId` (`tasks.md` §3). **Alternativas descartadas**: resolver la oficina **hoy** —recorrer `user_supervisors` y `team_members` en el momento de la consulta y convertirla en un conjunto de vendedores para `SalesScope`— contaría en la oficina nueva lo que un agente vendió en la vieja, que es justo lo que `RN-IN-014` prohíbe; y validar que la oficina existe obligaría a `IN` a consumir un puerto de `teams` para responder lo mismo que la sentencia, cero. **Pruebas** en `SalesSummaryIT`: dos equipos con un director cada uno, sembrados por SQL **antes** de registrar las ventas para que la línea nazca con oficina por el camino real de `RN-MV-078`; una venta con líneas de las dos; una línea sin vendedor; la venta de un manager; y el traslado —cerrar la pertenencia de un director y asignarlo a la otra después de vender—, para `CA-IN-098` a `CA-IN-100`. El coste no cambia: una sentencia por petición. **La respuesta no cambia**; el contrato solo gana el parámetro.

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Tablas, clases, endpoints, transacciones, pruebas.

    Lo que decide el negocio está en `spec.md` y no se repite aquí.

---

## 1. Enfoque

**Es el primer código del módulo `IN`, y por eso este plan construye lo que los cuatro indicadores comparten**: el paquete `modules/indicators`, la traducción del alcance, el periodo en días de Bogotá, la interfaz que `MV` publica y la migración que siembra **los cuatro** permisos. `RF-IN-002` a `RF-IN-004` añaden una consulta y una ruta cada uno, y heredan todo lo demás.

**La suma la escribe `MV`, y `IN` no lee ninguna tabla.** `movements` y `movement_details` son de `MV`, y `modules.md` §7 prohíbe leerlas desde otro módulo; la norma de [`architecture.md` §15.2](../../../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25) es que el dueño publique una interfaz de lectura en su capa `application`. **`MV` publica `SalesFigures`**: recibe un alcance **ya resuelto**, un intervalo y los filtros, y devuelve **cifras**. Es la forma de `CommissionableLines`, que `MV` ya publica para `CM`: `MV` sabe qué se vendió, a quién se atribuye y cuándo; no sabe qué es un indicador.

**El alcance lo resuelve `SP`, lo traduce `IN` y lo aplica `MV`.** `CommercialReach` (`RF-MV-015` · `T-02`) dice hasta dónde llega el actor; `IN` lo convierte en un `SalesScope` —todo, o un conjunto de vendedores—, aplicando el filtro de vendedor y cortando fuera del alcance; `MV` lo recibe como predicado. Así hay **una** definición de «mi red» (en `SP`) y **una** de «qué es una venta» (en `MV`), y `IN` no duplica ninguna.

**Lo que se prueba por HTTP es el reparto de líneas entre ramas**, y eso decide la semilla: el árbol de `SalesIT` —manager, dos directores, agentes bajo cada uno— con **importes distintos en cada línea** y **una venta con líneas de dos ramas**, para que un error de suma por venta en lugar de por línea dé un número que no coincide con ninguno plausible.

---

## 2. Cambios de esquema

**Ninguna tabla, columna ni índice.** El intervalo lo responde `ix_movements_occurred_at` (`V15`); el vendedor de la línea, `ix_movement_details_seller` (`V12`).

**Una migración de datos, `V74__in_permisos_de_indicadores.sql`** —o la siguiente libre al construir: el 06-10-2026 la sesión del doble factor de `SP` documenta en paralelo y puede tomar números antes—, que siembra **los cuatro** permisos del módulo, no solo el de este requerimiento: nacen juntos en `requirements/in.md` y sembrarlos de uno en uno serían cuatro migraciones para una decisión.

| Código | Literal |
|---|---|
| `indicators:read-sales-summary` | `…-9c4f-5e7ad8000001` |
| `indicators:read-sales-series` | `…-9c4f-5e7ad8000002` |
| `indicators:read-sales-by-product` | `…-9c4f-5e7ad8000003` |
| `indicators:read-sales-by-seller` | `…-9c4f-5e7ad8000004` |

**La serie `5e7ad8` es la de `IN`**, libre hasta hoy (Art. V.11); la marca v7 es la del día en que se construya, continuando la secuencia de la migración anterior. **Reparto por tipo de rol** (`requirements/in.md` §5.2.4): `FUNCIONARIO` y `VENDEDOR`, **no** `CONSUMIDOR`, con `ON CONFLICT`. **Guardas**: el catálogo en **189** —o en lo que dejen las migraciones que entren antes, más cuatro—, `SUPERADMIN` y `ADMIN` con los cuatro, ningún rol `CONSUMIDOR` con ninguno, y cero filas que rompan `RN-SEG-003`. La contención se sostiene porque los roles `VENDEDOR` sembrados cuelgan de `ADMIN` (`V8`), que es `FUNCIONARIO`; un rol vendedor creado a mano bajo un consumidor la rompería, y la guarda lo detecta en lugar de sembrar en silencio. Sin auditoría, como `V31` y `V32`.

---

## 3. Componentes afectados

### 3.1 En `MV` — lo que publica

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `SalesFigures` | **Nuevo** | La interfaz publicada. Este requerimiento declara `Summary summary(SalesScope, Interval, UUID currencyId)`; los otros tres la amplían con un método cada uno |
| `application` | `SalesFigures.SalesScope` | **Nuevo** | `everything()` o `sellers(Set<UUID>)`. **No tiene «vacío»**: el corte fuera del alcance es de `IN` y nunca llega aquí |
| `application` | `SalesFigures.Interval` | **Nuevo** | Dos `OffsetDateTime`, semiabierto. `MV` no sabe de días ni de zonas |
| `application` | `SalesFigures.Summary` | **Nuevo** | Por estado —`CONFIRMADA`, `PENDIENTE`, `ANULADA`—: ventas, líneas, unidades y una lista de `(currencyId, currencyCode, BigDecimal amount)` |
| `domain/repository` | `JpaSalesFigures` | **Nuevo** | **Una** sentencia nativa (§4.4), convertida de centésimas con `MinorUnits` al mapear |

**`SalesFigures` y no `SalesIndicators`**: `MV` publica cifras de ventas, y quién las llama indicadores es asunto de `IN`. Si mañana `CM` necesita lo mismo, la consume sin que el nombre mienta.

### 3.2 En `IN` — el módulo

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `SalesIndicatorRequest` | **Nuevo** | `from`, `to` (`LocalDate`), `currencyId`, `sellerId`. Lo comparten los cuatro indicadores |
| `application` | `SalesSummaryResponse` | **Nuevo** | §4.2 |
| `application` | `IndicatorPeriod` | **Nuevo** | `(LocalDate from, LocalDate to, String zone)`, la parte de la respuesta que dice qué se contó |
| `domain/service` | `SalesPeriodResolver` | **Nuevo** | Por defecto y validación del periodo (`VAL-001` a `VAL-003`), y su conversión a `Interval` con `BusinessCalendar.zona()` |
| `domain/service` | `SalesScopeResolver` | **Nuevo** | `Optional<SalesScope> resolve(UUID actor, UUID sellerId)`: `CommercialReach` → `SalesScope`, con el filtro de vendedor aplicado; **vacío = fuera del alcance** |
| `domain/service` | `GetSalesSummaryService` | **Nuevo** | Valida junto, resuelve periodo y alcance, **corta con ceros** si el alcance está vacío, consulta y mapea |
| `interfaces` | `SalesIndicatorsController` | **Nuevo** | `GET /api/v1/indicators/sales/summary` |

**`IN` no tiene `domain/repository` ni `domain/models`**, y no es un olvido: no es dueño de datos (`requirements/in.md` §1.4). Si alguna vez tiene una tabla, nacerán con ella.

**La traducción del alcance**:

| `Reach.kind` | Sin vendedor | Con vendedor |
|---|---|---|
| `EVERYTHING` | `everything()` | `sellers({vendedor})` — si no existe, cuenta cero sin que haya que comprobarlo |
| `NETWORK` | `sellers(red)` | `vendedor ∈ red` → `sellers({vendedor})`; si no, **vacío** |
| `OWN` | `sellers({actor})` | `vendedor = actor` → `sellers({actor})`; si no, **vacío** |

**`OWN` es `sellers({actor})` y no «sus compras»**, al revés que en `RF-MV-015`: aquel lista ventas y para el consumidor «las suyas» son las que compró; aquí se suma lo **vendido**, y lo único vendido que puede ser de alguien sin red es lo que vendió él.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/sales/summary` | `indicators:read-sales-summary` |

**La raíz es `/indicators`**, y bajo ella **un segmento por tanda** —`/sales` hoy, `/commissions` mañana— y uno por indicador. El frontend recorre la tanda con los permisos del perfil y pinta lo que puede pedir.

### 4.1 Parámetros

| Parámetro | Tipo | Nota |
|---|---|---|
| `from` | fecha ISO (`2026-09-01`) | Día de Bogotá, incluido. Por defecto, el primero del mes en curso —o el del mes de `to`, si viene solo `to`— |
| `to` | fecha ISO | Día de Bogotá, incluido entero. Por defecto, hoy |
| `currencyId` | UUID | Igualdad sobre la moneda de la venta; una inexistente da ceros |
| `sellerId` | UUID | §3.2; fuera del alcance, ceros **sin consultar** |
| `teamId` (09-10-2026) | UUID | Igualdad sobre la oficina guardada en la línea (`d.team_id`, `RN-IN-014`); se combina con el alcance; una inexistente da ceros |

`VAL-001`, `VAL-002` y `VAL-003` son `400` con el formato de problemas del sistema, devueltos juntos; un UUID mal formado es el `400` de conversión de siempre (`VAL-004`).

### 4.2 La respuesta

```json
{
  "period": { "from": "2026-09-01", "to": "2026-09-30", "zone": "America/Bogota" },
  "confirmed": {
    "sales": 12, "lines": 15, "units": 18,
    "amounts": [ { "currency": { "id": "…", "code": "USD" }, "amount": 1250.00 } ]
  },
  "pending": { "sales": 3, "amounts": [ … ] },
  "voided":  { "sales": 1, "amounts": [ … ] }
}
```

**`pending` y `voided` no llevan líneas ni unidades**: son contexto de lo vendido, no lo vendido, y un tablero que las pinte las pinta como «cuánto queda por cobrar». Los importes en **decimales**, como el resto de la API. `@Schema` con nombres propios —`SalesSummary`, `IndicatorPeriod`, `IndicatorAmount`, `IndicatorCurrency`— para que springdoc no los funda con los de `MV` (`MovementCurrency` ya existe).

### 4.3 Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` | El resumen, aunque sea de ceros — también con `sellerId` fuera del alcance |
| `400` | Fechas mal formadas, rango invertido, más de 366 días, UUID mal formado |
| `401` | Sin token |
| `403` | Sin `indicators:read-sales-summary`. **Ni `movements:list-sales` ni otro `indicators:` lo sustituyen** |

### 4.4 La sentencia

```sql
SELECT m.status, m.currency_id, c.code,
       count(DISTINCT m.id), count(*), sum(d.quantity), sum(d.line_amount)
  FROM movements m
  JOIN movement_types t ON t.id = m.movement_type_id AND t.code = 'VENTA'
  JOIN movement_details d ON d.movement_id = m.id
  JOIN currencies c ON c.id = m.currency_id
 WHERE m.occurred_at >= :desde AND m.occurred_at < :hasta
   AND m.status IN ('CONFIRMADA', 'PENDIENTE', 'ANULADA')
   [AND d.seller_id IN (:vendedores)]      -- sellers(…); con everything() no va, y entran las líneas sin vendedor
   [AND m.currency_id = :moneda]
   [AND d.team_id = :oficina]              -- 09-10-2026, RN-IN-014: la oficina guardada en la línea
 GROUP BY m.status, m.currency_id, c.code
```

**El `JOIN` es a las líneas y el filtro de vendedor va sobre la línea**, que es `RN-IN-003` entero: la venta cuenta una vez (`count(DISTINCT m.id)`) y el importe es la suma de **sus líneas del alcance**. Con `everything()` no hay predicado de vendedor, de modo que las líneas sin vendedor entran — y solo entonces. **Las ventas y las líneas se suman en Java entre monedas; los importes no** (`RN-IN-004`). El `JOIN` a `currencies` lo hace `MV` en otras lecturas (`JpaLedgerRepository`) y es lectura de una clave foránea declarada, no consumo de `SP`.

---

## 5. Autorización

**El permiso abre; el alcance decide qué se ve**, como en `RF-MV-015` §5. `@PreAuthorize("hasAuthority('indicators:read-sales-summary')")`, y la ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`.

---

## 6. Auditoría

Ninguna (`spec.md` §7).

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)` en el caso de uso: la resolución del alcance y la suma describen el mismo instante.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md` | §3 nombra `SalesFigures`; ficha a `Tasks en revisión` — **en este pase** |
| `requirements/mv.md` | §3 o §8: `MV` publica `SalesFigures` para `IN` — **al construir** |
| `architecture.md` | §15.2: `SalesFigures` en la tabla de interfaces publicadas — **al construir** |
| `security.md` | §4.4: los cuatro permisos pasan de declarados a sembrados — **al construir** |
| `requirements.md` | Fila e indicadores — **en este pase**, y al construir |
| `docs/api/index.md` | La ruta y su permiso — **al construir** |
| `LayerRulesTest` | Una regla para `IN`: no depende de `domain` de ningún otro módulo, y **ningún módulo depende de `IN`** — **al construir** |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Que `IN` lea `movements` con su propia sentencia | `modules.md` §7; y «qué es una venta» tendría dos definiciones |
| Sumar en `IN` lo que devuelve `RF-MV-015` | Suma por venta y no por línea (`spec.md` §2.1); y traería filas para sumarlas en memoria |
| Que `MV` reciba `CommercialReach.Reach` directamente | Ataría `SalesFigures` al tipo de `SP` y obligaría a `MV` a aplicar el filtro de vendedor y el corte, que son reglas de `IN` |
| Periodo en instantes, como los listados | Obliga a quien pregunta a calcular la medianoche de Bogotá en UTC; un indicador se pide en días (`spec.md` §6.1) |
| Un total convertido a una moneda | `RN-IN-004` |
| Una migración por indicador | Cuatro migraciones para una decisión que se tomó una vez |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Una red grande hace un `IN (…)` largo | El de `RF-MV-015` §10: hoy son decenas; el disparador de revisión es el mismo |
| El periodo de 366 días sobre el libro de un año entero, para `everything()` | Una sentencia agregada con índice por fecha; se mide en la prueba de costes y el tope se baja si hace falta |
| Que el resumen y el listado de mi red parezcan no cuadrar | Está decidido (`spec.md` §2.1) y se dice en la prosa de la ruta |
| Que otra sesión tome `V74` | El número se fija al construir; las guardas cuentan lo que haya más cuatro |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Funcionario, agente, director y manager sobre el árbol de `SalesIT`, con importes distintos por línea | Integración, `SalesSummaryIT` | `CA-IN-001` a `CA-IN-004`: la profundidad y la frontera entre ramas |
| Una venta con líneas de dos ramas | Integración | `CA-IN-005`, el que sostiene el módulo |
| Dos monedas; confirmada, pendiente y anulada; una compra de puntos; el alta gratuita | Integración | `CA-IN-006` a `CA-IN-008` |
| Una venta a las 20:00 de Bogotá del último día del mes | Integración | `CA-IN-009` |
| Sin fechas: el mes en curso, devuelto | Integración, con `BusinessCalendar` de reloj fijo | `CA-IN-010` |
| Vendedor dentro, fuera e inexistente; moneda inexistente | Integración | `CA-IN-011`: ceros y no error |
| Quien tiene `ended_at` deja de contar | Integración | `CA-IN-012` |
| Errores juntos | Integración | `CA-IN-013` |
| `403` sin permiso, con `movements:list-sales` y con otro `indicators:`; `401` | Integración | `CA-IN-014` |
| `SalesPeriodResolver`: por defecto, solo `to`, 366 y 367 días, bisiesto | Unitaria | Aritmética de fechas, sin base |
| `SalesScopeResolver`: la tabla de §3.2 entera | Unitaria, con `CommercialReach` doblado | Es la regla del corte |
| Coste: **una** sentencia de suma por petición, sea cual sea el tamaño de la red | Integración, estadísticas de Hibernate | `RNF-PERF` de `requirements/in.md` §7 |
| La migración: catálogo, reparto, `CONSUMIDOR` sin ninguno, contención | `PermissionsSeedIT`, `PermissionIT` y los recuentos del catálogo | Las guardas |
| `LayerRulesTest` con la regla de `IN` | Unitaria de arquitectura | La frontera la fija el código |
| Contrato regenerado, sin esquemas fundidos | `OpenApiContractIT` | Solo altas |
| Oficina: una venta de dos oficinas, lo sin vendedor y la venta de un manager fuera, el traslado que no mueve, el alcance combinado, inexistente y mal formada (09-10-2026) | Integración, `SalesSummaryIT` | `CA-IN-098` a `CA-IN-100` |
