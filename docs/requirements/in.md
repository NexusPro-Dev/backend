# Requerimientos del Módulo — `IN` Indicadores

| Campo | Valor |
|---|---|
| Módulo | `IN` — Indicadores |
| Paquete | `modules/indicators` |
| Prefijo de permisos | `indicators:` |
| Versión | 0.19.0 |
| Estado | **Borrador** |
| Responsable | Bonilla Diaz William Steven |
| Fecha de creación | 06-10-2026 |
| Última actualización | 07-10-2026 |

!!! info "Qué va en este documento"

    El catálogo de requerimientos del módulo: qué debe hacer, bajo qué reglas y con qué permisos.

    El comportamiento detallado de cada requerimiento —flujos, validaciones, criterios de aceptación y casos límite— vive en su tripleta, en `docs/specs/in/`. Aquí no se repite.

!!! warning "Documento en Borrador: tres cosas lo condicionan"

    1. **El código `IN`.** Un código, en cuanto aparece en un identificador, no se cambia jamás ([`modules.md` §2.1](../modules.md#21-regla-de-decision)). Se fija por decisión del responsable del proyecto del 06-10-2026, como con `PM`, `CM`, `MV` y `AC`, y [`modules.md` §5.6](../modules.md#56-in-indicadores) deja escrito el riesgo que se asume.
    2. **No es dueño de ninguna tabla** (§1.4). Es la primera vez que un módulo se incorpora sin cumplir la primera condición de [`modules.md` §2.1](../modules.md#21-regla-de-decision), y se hace a sabiendas.
    3. **Una interfaz que `MV` no publica todavía**: las ventas **agregadas** por un alcance (§3), **`SalesFigures`** desde las tripletas del 06-10-2026. La publica `RF-IN-001` · `T-02`, que es el primero que la necesita, por el mismo reparto con el que `RF-AC-008` pidió la suya a `SP`.

---

## 1. Información del módulo

### 1.1 Descripción

`IN` reúne **las cifras agregadas de la plataforma** —cuánto se vendió, de qué, quién lo vendió, cómo evoluciona— y decide **quién ve cada una**. No guarda hechos propios: los cuenta sobre los que otros módulos ya registran y los publica como **indicadores**, cada uno con su ruta y su permiso.

### 1.2 Objetivo

Que **cada rol vea los indicadores que le corresponden**, y que el reparto lo decida quien administra roles y no el código. Lo pidió el responsable del proyecto el 06-10-2026: «un módulo para indicadores, para repartir qué indicadores se pueden ver por roles».

### 1.3 Alcance

**Incluye**

- **El catálogo de indicadores**, cada uno con **su propio permiso** (`RN-IN-001`).
- **El alcance de cada cifra** según quién la mira: administración, todo; un vendedor, lo suyo y lo de su red; nadie más ve cifras ajenas (`RN-IN-002`).
- **La primera tanda, ventas**: el resumen de un periodo, su evolución en el tiempo, las ventas por producto y las ventas por vendedor (`RF-IN-001` a `RF-IN-004`).
- **La segunda tanda, puntos** (06-10-2026): cuántos se compraron, cuántos se redimieron, los ajustes a mano y el saldo del periodo (`RF-IN-005`, enmendado el 07-10-2026).
- **La tercera tanda, comisiones** (07-10-2026), empezando por **los lotes**: cuántos hay hoy en cada estado y por cuánto (`RF-IN-007`).

**No incluye**

- **Una tabla que diga qué rol ve qué indicador.** Lo dicen los permisos, que ya se asignan a los roles por `RF-SP-005` y que el frontend ya lee de `GET /users/me` (`RN-IN-001`, §5.2.1).
- **El tablero**: qué indicadores van juntos, en qué orden y con qué gráfica. Es del frontend, que sabe qué puede pedir por los permisos de quien entra.
- **Los indicadores de la red comercial de `RF-SP-058`** —el FTD por nodo—. Siguen en `SP`, con `broker-accounts:read-indicators`. Si se mudan aquí, será por un requerimiento propio que lo decida, y `RF-SP-058` conservará su identificador.
- **Retiros y academia**, y de comisiones **todo lo que no sea el resumen de lotes**. Son las tandas siguientes, declaradas y sin escribir; cada una pedirá a su módulo dueño la lectura agregada que necesite.
- **Convertir entre monedas.** Las cifras de dinero van separadas por moneda (`RN-IN-004`).
- **Guardar fotos de las cifras.** Cada lectura cuenta sobre los datos vivos (`RN-IN-006`).
- **Exportar** a hoja de cálculo o a PDF.

### 1.4 La frontera, y por qué `IN` es un módulo sin tablas

[`modules.md` §2.1](../modules.md#21-regla-de-decision) define un módulo por **dos** condiciones: es dueño de tablas propias y otros módulos lo consumen. **`IN` no cumple ninguna de las dos el día que nace**: los indicadores de ventas se cuentan sobre `movements` y `movement_details`, que son de `MV`, y ningún módulo necesita consumir un indicador.

**Se incorpora igual, por decisión del responsable del proyecto, y la razón está en lo que sí posee: el catálogo de indicadores y su reparto.** Las otras dos salidas eran peores:

| Salida | A favor | En contra |
|---|---|---|
| **Módulo `IN` propio** (la elegida) | Un solo sitio donde vive «qué indicadores existen y qué permiso abre cada uno». Una ruta raíz (`/api/v1/indicators`) que el frontend recorre para montar el tablero. Los indicadores que crucen módulos —ventas contra comisiones— tienen dónde vivir sin crear un ciclo | No es dueño de tablas: incumple §2.1 y queda escrito |
| Cada módulo publica sus indicadores (`RF-MV-NNN`, `RF-CM-NNN`, …) | Cumple §2.1 sin excepción: la cifra vive con su dueño | El catálogo queda repartido en cinco documentos y cinco prefijos de ruta, y un indicador que cruce dos módulos no tiene dueño: ponerlo en uno obliga a ese uno a consumir al otro |
| Submódulo de `SP` | `SP` ya resuelve el alcance (`CommercialReach`) y ya tiene un indicador (`RF-SP-058`) | `SP` tendría que consumir a `MV` y a `CM`, y `MV` y `CM` ya consumen a `SP`: el ciclo que [`modules.md` §7](../modules.md#7-reglas-de-dependencia) prohíbe |

**Lo que la excepción no autoriza**: leer las tablas de otro módulo. `IN` cuenta **por las interfaces que publica el dueño de los datos** ([`architecture.md` §15.2](../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25)), y la consulta agregada —el `SUM` y el `GROUP BY` sobre `movement_details`— **la escribe `MV`**, que es quien conoce sus columnas y sus estados. `IN` decide **qué** se pide, **para quién** y **con qué permiso**; el dueño decide **cómo** se cuenta.

**Cuándo deja de ser excepción.** En cuanto `IN` necesite una tabla propia —fotos diarias de las cifras para no contarlas en cada lectura (`RN-IN-006`), metas por vendedor, umbrales de alerta—, cumplirá la primera condición, y esa tabla será suya y de nadie más.

---

## 2. Submódulos

Según [`modules.md` §5.6](../modules.md#56-in-indicadores).

| Submódulo | Responsabilidad | Requerimientos |
|---|---|---|
| Ventas | Las cifras de lo vendido: resumen, evolución, por producto y por vendedor; y, para administración, el resumen de líneas de venta (06-10-2026) | `RF-IN-001` a `RF-IN-004`, `RF-IN-006` |
| Puntos | Lo comprado, lo redimido, los ajustes y el saldo de los puntos (06-10-2026) | `RF-IN-005` |
| Comisiones | Los lotes de comisiones por estado, para administración, y las comisiones propias por estado de su lote, para cada persona (07-10-2026) | `RF-IN-007`, `RF-IN-008` |

---

## 3. Dependencias

| Módulo | Tipo | Para qué |
|---|---|---|
| `SP` | Consume | **El alcance comercial** de quien pregunta (`RN-IN-002`): `CommercialReach.reachOf`, la misma interfaz que consume `RF-MV-015`. **Ya publicada** |
| `SP` | Consume | **La identidad** de cada vendedor —nombre de usuario y nombre completo— para la fila de `RF-IN-004`. `UserCatalog`, ya publicada |
| `MV` | Consume | **Las ventas agregadas** por periodo, moneda, estado, producto o vendedor, **acotadas a un alcance** (`RN-IN-003`): **`SalesFigures`**. **No existe todavía**: la publica `RF-IN-001` y la amplían los otros tres, un método cada uno |
| `MV` | Consume | **Los movimientos y el saldo de los puntos** por titular y moneda (`RN-IN-009`): **`PointsFigures`**, que publica `RF-IN-005`. Sumas sobre los asientos de las cuentas `PUNTOS`, nunca filas |
| `CM` | Consume | **Los lotes de comisiones agregados** por estado y moneda (`RF-IN-007`, 07-10-2026): **`CommissionBatchFigures`**, que publica `RF-IN-007` · `T-02`. Sin alcance. Y **las comisiones de una persona** por el estado de su lote y su moneda (`RF-IN-008`, 07-10-2026), una segunda lectura en la misma interfaz |

La dependencia es **acíclica**: `IN` → `SP`, `IN` → `MV` e `IN` → `CM`. Ninguno de los tres consume a `IN`, y no lo harán: un indicador se lee, no se usa para decidir nada.

!!! danger "Lo que `MV` tiene que publicar"

    Una interfaz de lectura en la capa `application` de `MV` —**`SalesFigures`**, [`RF-IN-001` · `plan.md`](../specs/in/001-resumen-de-ventas/plan.md) §3.1— que reciba **un alcance ya resuelto** (todo, o un conjunto de vendedores), un periodo y los filtros, y devuelva **cifras**, no filas. **`MV` no resuelve el alcance**: lo recibe, igual que en `RF-MV-015`, para que haya **una** definición de «mi red» y viva en `SP`.

    **No se reutiliza `RF-MV-015`**, aunque recorra las mismas ventas: aquel devuelve ventas enteras —una venta se ve si **alguna** de sus líneas cae en mi red (`RN-MV-031`)— y aquí se suman **líneas** (`RN-IN-003`). Sumar el `payable_amount` de las ventas que lista `RF-MV-015` le contaría a un director el importe de las líneas que vendió alguien de fuera de su red.

---

## 4. Actores

| Actor | Rol en el módulo | Permisos típicos |
|---|---|---|
| Administración (rol de tipo `FUNCIONARIO`) | Ve las cifras de **toda** la plataforma, incluidas las líneas que aún no tienen vendedor | `indicators:read-sales-summary`, `-series`, `-by-product`, `-by-seller` |
| Vendedor (rol de tipo `VENDEDOR`) | Ve **lo suyo y lo de su red**, en toda la profundidad: el agente lo suyo, el director lo suyo y lo de sus agentes, el manager lo de sus directores y por tanto lo de los agentes | Los mismos cuatro |
| Consumidor (rol de tipo `CONSUMIDOR`) | **Ninguno en esta tanda**: los indicadores de ventas miden lo que se vende, y el consumidor compra | — |

**El permiso decide si se entra; el alcance decide qué se ve** (`RN-IN-002`). Un agente y el administrador piden la misma ruta con el mismo permiso, y reciben cifras distintas.

---

## 5. Reglas de negocio

### 5.1 Catálogo

| ID | Regla | Cuándo aplica | Qué debe ocurrir | Prioridad |
|---|---|---|---|---|
| `RN-IN-001` | **Un indicador, un permiso**, y ese permiso es el reparto | Siempre | Cada indicador se publica en **su propia ruta** con **su propio permiso** `indicators:read-<indicador>` (`RN-SEG-014`, `RN-SEG-015`). **Qué rol ve qué indicador lo decide quien administra roles** asignando o revocando ese permiso (`RF-SP-005`, `RF-SP-006`), y el frontend sabe qué mostrar por los permisos efectivos de `GET /users/me` (`RF-SP-039`). No existe una tabla rol → indicador. Decisión del responsable del proyecto, 06-10-2026 (§5.2.1) | **Crítica** |
| `RN-IN-002` | **Las cifras se acotan al alcance de quien mira** | Al calcular cualquier indicador | El alcance lo resuelve `SP` (`CommercialReach`, precedencia de `RN-MV-031`): **`FUNCIONARIO`**, todo; **`VENDEDOR`**, él y quienes cuelgan de él en la estructura de mando **vigente**, en toda la profundidad; **cualquier otro**, solo él. Un filtro por vendedor (`sellerId`) **dentro** del alcance lo estrecha; **fuera** de él responde **cifras en cero o colección vacía**, nunca `403` ni `404`: el indicador no es un oráculo de quién cuelga de quién. Decisión del responsable del proyecto, 06-10-2026 | **Crítica** |
| `RN-IN-003` | **Las ventas se cuentan por línea**, y cada línea es de su vendedor | Al calcular un indicador de ventas | El importe es la suma de `line_amount` de las líneas **cuyo vendedor está en el alcance** (`RN-MV-003`: el vendedor es de la línea). Una venta **cuenta como una** si al menos una de sus líneas está en el alcance, y su importe es **solo el de esas líneas**. Las líneas **sin vendedor** —ventas en `VALIDAR_COMISIONES` (`RN-MV-034`)— solo entran en el alcance **total**, y por vendedor se agrupan como «sin asignar» | **Crítica** |
| `RN-IN-004` | **El dinero se agrupa por moneda y nunca se suma entre monedas** | Siempre que un indicador dé un importe | Cada importe va con su moneda (`movements.currency_id`) y **en decimales, como el resto de la API**: la base guarda centésimas y la suma se hace en centésimas, que se convierten **una vez, al mapear** (`MinorUnits`, ADR-006: nunca se divide en SQL). Sumar pesos con dólares exige escoger una tasa —¿la de hoy?, ¿la de cada venta?— y esa decisión no es de un indicador: el día que se quiera un total en una moneda será un requerimiento propio que diga con qué tasa. Las **cantidades** —ventas, líneas, unidades— sí se suman entre monedas | **Alta** |
| `RN-IN-005` | **Qué es una venta para los indicadores** | Al calcular un indicador de ventas | Solo movimientos de tipo **`VENTA`**: la compra de puntos (`COMPRA_PUNTOS`) no vende un producto y no comisiona. El periodo se aplica a **`occurred_at`** —cuándo ocurrió la venta, no cuándo se registró—, **semiabierto** (`[from, to)`), como los listados de auditoría y de movimientos; **sin fechas, todas** (`RN-IN-010`, 06-10-2026). **«Vendido» es `CONFIRMADA`**; `PENDIENTE` y `ANULADA` se informan aparte, cada una con su cantidad y su importe, y nunca se suman a lo vendido. La venta del **alta gratuita** (`RN-SP-043`) es una `VENTA` confirmada de importe cero: **cuenta** como venta y no mueve el importe | **Alta** |
| `RN-IN-006` | **Los indicadores no guardan nada** | Siempre | Cada lectura cuenta sobre los datos vivos de su dueño. No hay fotos, ni caché, ni tablas de `IN`. Si el volumen llega a exigirlo, las fotos serán tablas **de `IN`** y un requerimiento propio dirá cada cuánto se toman y qué pasa con lo que cambia después —una venta pendiente que se confirma, una línea que gana vendedor— | Media |
| `RN-IN-007` | **Los días son los de Bogotá** | Al agrupar por día, semana o mes, y al interpretar una fecha sin hora | El corte de cada día, semana —de lunes a domingo— y mes se hace en la zona **`America/Bogota`**, la misma con la que `CM` cierra sus periodos. Agrupar en UTC pondría las ventas de las siete de la noche en el día siguiente | Media |
| `RN-IN-008` | **Gratuita es la venta de importe cero** | Al contar las ventas gratuitas (`RF-IN-001`) | Una venta es gratuita si **lo que se cobra por ella entera** es cero —el importe a pagar de la venta, no el de las líneas del alcance—, que es lo mismo que decir que se registró con el método `GRATIS` (`RN-MV-022`); hoy, la del alta por enlace (`RN-MV-075`). **Sigue contando como venta** en su estado, con importe cero, y además se cuenta aparte como gratuita. Decisión del responsable del proyecto, 06-10-2026 | Media |
| `RN-IN-009` | **Qué es comprado, redimido y ajuste de puntos, y cuándo cuenta** | Al contar los puntos (`RF-IN-005`) | Cada cambio en los puntos de una persona es un asiento de su cuenta `PUNTOS`, y **el evento del asiento dice la clase**: una compra **cobrada** es lo **comprado**; un pago de una venta con puntos, lo **redimido**; un ajuste a mano, lo **sumado** o lo **restado** según su signo. **El periodo mira cuándo se movieron los puntos** —una compra cuenta el día en que se cobró—, y **el saldo es el del periodo, calculado con esas cuatro cifras**: comprado − redimido + sumado − restado; sin fechas, toda la historia, es decir, los puntos que hay hoy (enmienda del 07-10-2026; hasta entonces era siempre el de hoy, leído aparte). Todo **por moneda** y **en positivo**. Decisión del responsable del proyecto, 06-10-2026 | Alta |
| `RN-IN-010` | **Sin fechas, todo; y cada indicador se parte en tramos si se pide** | Al pedir cualquier indicador | **Sin `from` ni `to`, el indicador cuenta toda la historia** hasta hoy. **Con una sola fecha, la otra queda abierta**: solo «desde», hasta hoy; solo «hasta», desde el principio. **No hay tope de días**. Y **todo indicador acepta un tramo** —día, semana de lunes o mes, del calendario de Bogotá—: con él, la respuesta trae además **sus mismas cifras partidas por tramo**, todos los tramos presentes, del primero con datos —o del de «desde»— al de hoy —o al de «hasta»—; la suma de los tramos es el total. **El saldo de puntos también va por tramo**, con las cuatro cifras de cada uno (07-10-2026). Decisión del responsable del proyecto, 06-10-2026 | Alta |
| `RN-IN-011` | **Un indicador de administración no se acota por alcance** | Al calcular el resumen de líneas de venta (`RF-IN-006`) y el de lotes de comisiones (`RF-IN-007`, 07-10-2026) | Excepción declarada a `RN-IN-002`: **quien porta el permiso ve las cifras enteras**, sea cual sea su tipo de rol. Lo justifica lo que cuenta: **lo que no tiene vendedor no está en el alcance de ningún vendedor**, y acotarlo lo dejaría en cero para todos menos para administración. El permiso se siembra solo a `SUPERADMIN` y `ADMIN`; si administración se lo da a otro rol, ese rol lo ve todo. Decisión del responsable del proyecto, 06-10-2026 | Alta |
| `RN-IN-013` | **Un indicador personal cuenta solo lo de quien pregunta** | Al calcular el resumen de mis comisiones (`RF-IN-008`) | Excepción declarada a `RN-IN-002`: **la persona la pone la sesión**, no hay filtro de persona y **no se suma la red**. Lo justifica lo que cuenta: **cada nivel de la cadena tiene su propia comisión** (`RN-CM-011`), y lo que un director cobra por la venta de su agente ya es una comisión suya; sumarle las de sus agentes le mostraría dinero que no es suyo. Es de lo propio (`RN-SEG-015`), como `RF-CM-012` y `RF-CM-026`. Decisión del responsable del proyecto, 07-10-2026 | Alta |
| `RN-IN-012` | **Un indicador de estado cuenta el estado de hoy** | Al calcular el resumen de lotes de comisiones (`RF-IN-007`) | Cuando la pregunta es **dónde está algo hoy** —en qué estado están los lotes de comisiones— y no **cuánto pasó en un periodo**, el indicador cuenta **el estado del instante de la consulta**, sin tramos. Decisión del responsable del proyecto, 07-10-2026. **Enmendada el mismo día**, también por él: **las fechas eligen qué lotes se cuentan** —los que su periodo de comisiones toca el rango, con `RN-IN-010` para los días—, **no en qué estado estaban entonces**. Sin fechas, todos | Alta |

### 5.2 Decisiones que definen el módulo — 06-10-2026

#### 5.2.1 El reparto es el permiso, y no una tabla

**El responsable del proyecto pidió el módulo «para repartir qué indicadores se pueden ver por roles».** Ese reparto **ya existe en el sistema**, y con forma de permiso: desde `RN-SEG-015` cada ruta tiene el suyo, el administrador los asigna a los roles desde su pantalla, y el frontend ya decide qué vista mostrar leyendo los permisos efectivos del perfil propio. Un indicador es una lectura más, y su permiso es su reparto.

La alternativa era una tabla `role_indicators` con su propia pantalla de asignación. Se descartó porque **duplicaba lo que el permiso ya hace**, y dos mecanismos para la misma pregunta acaban contestándola distinto: un rol con el indicador asignado en la tabla y sin el permiso de la ruta vería la tarjeta en el tablero y un `403` al abrirla. Lo único que la tabla daba y el permiso no —el **orden** y la **posición** de cada indicador en el tablero de cada rol— es presentación, y es del frontend.

**El precio, escrito**: un rol creado a mano nace sin indicadores, como nace sin cualquier otro permiso (`security.md` §4.4, `RN-SEG-015`), y alguien tiene que concedérselos.

#### 5.2.2 El mismo indicador da cifras distintas según quién lo mira

**Decisión del responsable del proyecto, 06-10-2026.** La alternativa era un indicador por nivel —«mis ventas», «las ventas de mi red», «las ventas totales»—, cada uno con su permiso. Se descartó porque **triplicaba el catálogo sin dar nada**: el alcance ya lo resuelve `CommercialReach`, con la precedencia de `RN-MV-031`, y `RF-MV-015` ya lo aplica así a las ventas. Una pantalla de indicadores, como una de ventas, **es la misma para todos y enseña lo que a cada uno le toca**.

#### 5.2.3 La primera tanda es de ventas

**Decisión del responsable del proyecto, 06-10-2026.** Son los cuatro cortes con los que se mira una venta: **cuánto** (`RF-IN-001`), **cuándo** (`RF-IN-002`), **qué** (`RF-IN-003`) y **quién** (`RF-IN-004`). Cada uno es una ruta y un permiso, de modo que se puede dar el resumen a un agente sin darle el ranking de su red.

#### 5.2.4 Reparto inicial: administración y vendedores, no consumidores

La migración que los siembre los da **por tipo de rol**, como los demás permisos que se ven por alcance (`RN-SEG-015`): a todo rol **`FUNCIONARIO`** —con `SUPERADMIN` y `ADMIN` dentro, por la obligación de [`security.md` §4.4](../security.md#44-catalogo-de-permisos)— y a todo rol **`VENDEDOR`**. **A `CONSUMIDOR` no**: con su alcance vería solo lo que él vendió, que es nada. Lo que compró ya lo tiene en sus compras (`RF-MV-008`, `RF-MV-014`).

### 5.3 Reglas de otros documentos que este módulo aplica

| ID | Regla | Origen | Aplica a |
|---|---|---|---|
| `RN-SEG-014` | Un permiso, una operación | `security.md` §4.4 | `RN-IN-001` |
| `RN-SEG-015` | Autenticarse no autoriza nada | `security.md` §4.4 | `RN-IN-001`, §5.2.4 |
| `RN-MV-003` | El vendedor es de la línea | `requirements/mv.md` §5.1 | `RN-IN-003` |
| `RN-MV-005` | Los estados de la venta | `requirements/mv.md` §5.1 | `RN-IN-005` |
| `RN-MV-031` | Las ventas se ven por quién se es | `requirements/mv.md` §5.1 | `RN-IN-002` |
| `RN-MV-034` | La venta nace `VALIDAR_COMISIONES` si quien compra tiene varios vendedores | `requirements/mv.md` §5.1 | `RN-IN-003` |

---

## 6. Requerimientos funcionales

### 6.1 Resumen

| ID | Nombre | Submódulo | Prioridad | Permiso | Estado |
|---|---|---|---|---|---|
| `RF-IN-001` | Consultar el resumen de ventas | Ventas | Alta | `indicators:read-sales-summary` | **En desarrollo** (06-10-2026) |
| `RF-IN-002` | Consultar la evolución de las ventas | Ventas | Alta | `indicators:read-sales-series` | **En desarrollo** (06-10-2026) |
| `RF-IN-003` | Consultar las ventas por producto | Ventas | Media | `indicators:read-sales-by-product` | **Tasks aprobadas** (06-10-2026) |
| `RF-IN-004` | Consultar las ventas por vendedor | Ventas | Media | `indicators:read-sales-by-seller` | **Tasks aprobadas** (06-10-2026) |
| `RF-IN-005` | Consultar el resumen de puntos | Puntos | Alta | `indicators:read-points-summary` | **En desarrollo** (06-10-2026) |
| `RF-IN-006` | Consultar el resumen de líneas de venta | Ventas | Alta | `indicators:read-sale-lines-summary` | **En desarrollo** (06-10-2026) |
| `RF-IN-007` | Consultar el resumen de lotes de comisiones | Comisiones | Alta | `indicators:read-commission-batches-summary` | **En desarrollo** (07-10-2026) |
| `RF-IN-008` | Consultar el resumen de mis comisiones | Comisiones | Alta | `indicators:read-own-commissions-summary` | **En desarrollo** (07-10-2026) |

**Prioridades:** Crítica · Alta · Media · Baja.
**Estados:** los de [`requirements.md` §4](../requirements.md#4-matriz-de-trazabilidad).

### 6.2 Fichas

#### `RF-IN-001` — Consultar el resumen de ventas

| Campo | Valor |
|---|---|
| Objetivo | Saber **cuánto se vendió en un periodo** dentro de mi alcance |
| Actor | Cualquier persona con el permiso; **lo que ve lo decide su alcance** (`RN-IN-002`) |
| Permiso requerido | `indicators:read-sales-summary` |
| Prioridad | Alta |
| Reglas aplicables | `RN-IN-001` a `RN-IN-005`, `RN-IN-007` |
| Depende de | `SP` publica `CommercialReach`; **`MV` publica las ventas agregadas** (§3) |
| Tripleta | [`docs/specs/in/001-resumen-de-ventas/`](../specs/in/001-resumen-de-ventas/spec.md) |
| Estado | **En desarrollo** — construido el 06-10-2026, con `SalesFigures` y `V74` |

Para un periodo y, opcionalmente, una moneda o un vendedor de mi alcance: **cuántas ventas hubo en total**, sea cual sea su estado (06-10-2026); **cuántas** se confirmaron, cuántas líneas y cuántas unidades, y **por cuánto** en cada moneda; y aparte, con su cantidad y su importe, las que siguen **pendientes** y las **anuladas**. **Cada una de esas cifras dice además cuántas fueron gratuitas** (`RN-IN-008`, 06-10-2026). Es el primero en construirse porque es el que estrena la interfaz de `MV`.

#### `RF-IN-002` — Consultar la evolución de las ventas

| Campo | Valor |
|---|---|
| Objetivo | Ver **cómo cambian** las ventas a lo largo de un periodo |
| Actor | Cualquier persona con el permiso; lo que ve lo decide su alcance (`RN-IN-002`) |
| Permiso requerido | `indicators:read-sales-series` |
| Prioridad | Alta |
| Reglas aplicables | `RN-IN-001` a `RN-IN-005`, `RN-IN-007` |
| Depende de | `RF-IN-001` |
| Tripleta | [`docs/specs/in/002-evolucion-de-ventas/`](../specs/in/002-evolucion-de-ventas/spec.md) |
| Estado | **En desarrollo** — construido el 06-10-2026 |

Las mismas cifras de lo **confirmado** que `RF-IN-001`, partidas en **días, semanas o meses** de Bogotá. **Cada tramo del periodo aparece aunque no tenga ventas**, con ceros: una serie con huecos se dibuja como una línea que une dos puntos lejanos y miente sobre lo que pasó entre ellos. El número de tramos tiene tope, que fija la spec.

#### `RF-IN-003` — Consultar las ventas por producto

| Campo | Valor |
|---|---|
| Objetivo | Saber **qué se vende más** en mi alcance |
| Actor | Cualquier persona con el permiso; lo que ve lo decide su alcance (`RN-IN-002`) |
| Permiso requerido | `indicators:read-sales-by-product` |
| Prioridad | Media |
| Reglas aplicables | `RN-IN-001` a `RN-IN-005` |
| Depende de | `RF-IN-001` |
| Tripleta | [`docs/specs/in/003-ventas-por-producto/`](../specs/in/003-ventas-por-producto/spec.md) |
| Estado | **Tasks aprobadas** — 06-10-2026; se construye sobre `RF-IN-001` |

Lo **confirmado** en el periodo agrupado por producto —unidades, líneas e importe por moneda—, de más a menos, con un límite de filas. **El producto es el de la línea**, con el nombre congelado en la venta (`movement_details.product_name`): un producto renombrado o retirado sigue apareciendo con el nombre con que se vendió. Un **paquete** aporta una línea por cada producto que lleva (`RN-MV-028`), de modo que aquí se ven los productos y no el paquete.

#### `RF-IN-004` — Consultar las ventas por vendedor

| Campo | Valor |
|---|---|
| Objetivo | Saber **quién vende cuánto** en mi alcance |
| Actor | Cualquier persona con el permiso; lo que ve lo decide su alcance (`RN-IN-002`) |
| Permiso requerido | `indicators:read-sales-by-seller` |
| Prioridad | Media |
| Reglas aplicables | `RN-IN-001` a `RN-IN-005` |
| Depende de | `RF-IN-001`; `SP` publica la identidad del vendedor (`UserCatalog`) |
| Tripleta | [`docs/specs/in/004-ventas-por-vendedor/`](../specs/in/004-ventas-por-vendedor/spec.md) |
| Estado | **Tasks aprobadas** — 06-10-2026; se construye sobre `RF-IN-001` |

Lo **confirmado** en el periodo agrupado por **el vendedor de la línea**, de más a menos. Para un vendedor, **cada persona de su red y él mismo**, cada una con **lo que vendió ella** —no lo de su red: el acumulado por rama es de `RF-SP-058`, que lo hace para el FTD, y si se quiere para las ventas será otro requerimiento—. Para administración, todos los vendedores y una fila **«sin asignar»** con las líneas que aún no tienen vendedor (`RN-IN-003`).

#### `RF-IN-005` — Consultar el resumen de puntos

| Campo | Valor |
|---|---|
| Objetivo | Saber **cuántos puntos se compraron, cuántos se redimieron y cuántos hay** en mi alcance |
| Actor | Cualquier persona con el permiso; lo que ve lo decide su alcance (`RN-IN-002`), sobre **los titulares** de los puntos |
| Permiso requerido | `indicators:read-points-summary` |
| Prioridad | Alta |
| Reglas aplicables | `RN-IN-001`, `RN-IN-002`, `RN-IN-004`, `RN-IN-007`, `RN-IN-009` |
| Depende de | `SP` publica `CommercialReach`; **`MV` publica `PointsFigures`** |
| Tripleta | [`docs/specs/in/005-resumen-de-puntos/`](../specs/in/005-resumen-de-puntos/spec.md) |
| Estado | **En desarrollo** — construido el 06-10-2026, con `PointsFigures` y `V76` |

**Nace el 06-10-2026 a petición del responsable del proyecto** —«el siguiente indicador es para saber la información de los puntos: cuántos se han comprado, cuántos se han redimido y el balance»—, con tres decisiones suyas: **según el alcance**, como los de ventas; **los ajustes a mano aparte**, lo sumado y lo restado, para que el saldo se explique; y **el saldo de hoy**, con lo comprado, lo redimido y los ajustes **del periodo**. **Enmendado el 07-10-2026**, también a petición suya —«que el saldo también dependa del periodo»—: **el saldo se calcula con las cifras de los puntos del periodo** —comprados − redimidos + sumados − restados— y no aparte: «la idea es que calcules el balance con los datos de los puntos, no que calcules el balance por aparte». Por moneda, porque los puntos se compran y se gastan en una. **Con un periodo que lo cubre todo, el saldo es comprados − redimidos + sumados − restados**, y es la prueba de que no se pierde nada.

#### `RF-IN-006` — Consultar el resumen de líneas de venta

| Campo | Valor |
|---|---|
| Objetivo | Saber, sobre **todas** las líneas de venta y **por tipo de producto**, cuántos productos —unidades— se vendieron y cuántas ventas y líneas siguen **sin vendedor** (enmendado el 07-10-2026) |
| Actor | Administración; **quien porte el permiso lo ve entero** (`RN-IN-011`) |
| Permiso requerido | `indicators:read-sale-lines-summary` |
| Prioridad | Alta |
| Reglas aplicables | `RN-IN-001`, `RN-IN-003`, `RN-IN-004`, `RN-IN-005`, `RN-IN-007`, `RN-IN-010`, `RN-IN-011` |
| Depende de | **`MV` amplía `SalesFigures`** con lo sin vendedor |
| Tripleta | [`docs/specs/in/006-resumen-de-lineas-de-venta/`](../specs/in/006-resumen-de-lineas-de-venta/spec.md) |
| Estado | **En desarrollo** — construido el 06-10-2026, con `V78` y lo sin vendedor en `SalesFigures` |

**Nace el 06-10-2026 a petición del responsable del proyecto** —«el siguiente indicador será para las líneas de ventas: total productos vendidos, total de ventas, ventas sin vendedor»—. Por estado de la venta: ventas, líneas, **unidades** e importe por moneda, y el total; y aparte **lo sin vendedor** —ventas con alguna línea sin vendedor, esas líneas, sus unidades y su importe—, sin las anuladas, porque dice lo que **falta por atribuir**. Sus cifras por estado son las del resumen de ventas de administración.

**Desde el 07-10-2026 se filtra**, a petición del responsable del proyecto, **por vendedor, cliente, producto y comprobante** (`CA-IN-080` a `CA-IN-085`): filtros opcionales y combinables que estrechan los dos bloques sin cambiar su forma. **No son alcance** (`RN-IN-011` sigue en pie): los elige quien pregunta, como la moneda. Con vendedor, lo sin vendedor sale en cero.

#### `RF-IN-007` — Consultar el resumen de lotes de comisiones

| Campo | Valor |
|---|---|
| Objetivo | Saber, **hoy**, cuántos lotes de comisiones hay **abiertos, pendientes de pago y pagados**, y por cuánto en cada moneda; desde el 07-10-2026, de un vendedor o de un rango de fechas |
| Actor | Administración; **quien porte el permiso lo ve entero** (`RN-IN-011`) |
| Permiso requerido | `indicators:read-commission-batches-summary` |
| Prioridad | Alta |
| Reglas aplicables | `RN-IN-001`, `RN-IN-004`, `RN-IN-006`, `RN-IN-011`, `RN-IN-012` |
| Depende de | **`CM` publica `CommissionBatchFigures`** (§3) |
| Tripleta | [`docs/specs/in/007-resumen-de-lotes-de-comisiones/`](../specs/in/007-resumen-de-lotes-de-comisiones/spec.md) |
| Estado | **En desarrollo** — construido el 07-10-2026, con `CommissionBatchFigures` y `V79` |

**Nace el 07-10-2026 a petición del responsable del proyecto** —«el de lotes de comisiones: por estado, valor total, total de lotes»—, con dos decisiones suyas: **solo administración y sin alcance**, como `RF-IN-006`, y **sin periodo: una foto de hoy** (`RN-IN-012`). Por estado y en total, **cuántos lotes** y **su valor por moneda**, que es el total que cada lote tiene hoy. Abre la tanda de comisiones.

**Desde el 07-10-2026 se filtra por vendedor y por fechas** (`CA-IN-086` a `CA-IN-089`), a petición del responsable del proyecto: las fechas eligen los lotes **cuyo periodo toca el rango**, y el estado sigue siendo el de hoy (`RN-IN-012`, enmendada). El vendedor **no es alcance** (`RN-IN-011`).

#### `RF-IN-008` — Consultar el resumen de mis comisiones

| Campo | Valor |
|---|---|
| Objetivo | Saber, de **mis** comisiones, cuántas tengo y cuánto suman, en total y según su lote esté **abierto, pendiente de pago o pagado** |
| Actor | Cualquier persona con el permiso; **ve solo lo suyo** (`RN-IN-013`) |
| Permiso requerido | `indicators:read-own-commissions-summary` |
| Prioridad | Alta |
| Reglas aplicables | `RN-IN-001`, `RN-IN-004`, `RN-IN-006`, `RN-IN-007`, `RN-IN-010`, `RN-IN-012`, `RN-IN-013` |
| Depende de | **`CM` amplía `CommissionBatchFigures`** con las comisiones de una persona (§3) |
| Tripleta | [`docs/specs/in/008-resumen-de-mis-comisiones/`](../specs/in/008-resumen-de-mis-comisiones/spec.md) |
| Estado | **En desarrollo** — tripleta del 07-10-2026 con `tasks.md` aprobadas; construido el mismo día, con `V83` |

**Nace el 07-10-2026 a petición del responsable del proyecto** —«un indicador nuevo para las comisiones personales: el total de comisiones, cuántas están en el lote abierto, pendiente y pagados»—, con tres decisiones suyas: **solo lo mío** (`RN-IN-013`, nace aquí), **el estado de hoy con fechas opcionales** sobre el nacimiento de la comisión —la misma fecha que filtra `RF-CM-026`— y **cuántas y cuánto**. La cara personal de `RF-IN-007`: aquel cuenta lotes de todos para administración; este, comisiones de quien pregunta. **Desde el mismo día se filtra por cliente** (`CA-IN-097`), igual que la lista de `RF-CM-026`.

---

## 7. Requerimientos no funcionales

| ID | Requerimiento | Detalle en |
|---|---|---|
| `RNF-PERF-*` | Cada indicador se resuelve con **un número fijo de sentencias**, sea cual sea el tamaño de la red o del periodo —el alcance se resuelve una vez y se aplica como predicado—. Las pruebas lo cuentan con las estadísticas de Hibernate | `plan.md` de cada tripleta |
| `RNF-SEG-*` | Fuera del alcance no se distingue «no existe» de «no es tuyo» (`RN-IN-002`) | `security.md` §5 |

---

## 8. Integraciones

| Sistema o módulo | Tipo | Dirección | Descripción |
|---|---|---|---|
| `SP` | Interfaz de aplicación | Entrada | `CommercialReach`, `UserCatalog` |
| `MV` | Interfaz de aplicación | Entrada | Las ventas agregadas por alcance (§3) |
| `CM` | Interfaz de aplicación | Entrada | Los lotes de comisiones agregados por estado y moneda (§3) |

---

## 9. API

| Método | Ruta | Requerimiento | Permiso |
|---|---|---|---|
| `GET` | `/api/v1/indicators/sales/summary` | `RF-IN-001` | `indicators:read-sales-summary` |
| `GET` | `/api/v1/indicators/sales/series` | `RF-IN-002` | `indicators:read-sales-series` |
| `GET` | `/api/v1/indicators/sales/by-product` | `RF-IN-003` | `indicators:read-sales-by-product` |
| `GET` | `/api/v1/indicators/sales/by-seller` | `RF-IN-004` | `indicators:read-sales-by-seller` |
| `GET` | `/api/v1/indicators/points/summary` | `RF-IN-005` | `indicators:read-points-summary` |
| `GET` | `/api/v1/indicators/sales/lines/summary` | `RF-IN-006` | `indicators:read-sale-lines-summary` |
| `GET` | `/api/v1/indicators/commissions/batches/summary` | `RF-IN-007` | `indicators:read-commission-batches-summary` |
| `GET` | `/api/v1/indicators/commissions/mine/summary` | `RF-IN-008` | `indicators:read-own-commissions-summary` |

El contrato detallado de cada endpoint —parámetros, valores por defecto del periodo, topes— se define en el `plan.md` de su tripleta.

---

## 10. Persistencia

**Ninguna.** `IN` no es dueño de tablas (§1.4, `RN-IN-006`) y no tiene migraciones de esquema; la única que tendrá es la que **siembre sus cuatro permisos** (§5.2.4).

---

## 11. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | **Creación del módulo `IN` — Indicadores**, por decisión del responsable del proyecto: «un módulo para indicadores, para repartir qué indicadores se pueden ver por roles». Tres decisiones lo definen: **el reparto es el permiso** —uno por indicador, asignado desde la administración de roles, sin tabla propia— (`RN-IN-001`, §5.2.1); **las cifras dependen de quién mira** —`CommercialReach`, como `RF-MV-015`— (`RN-IN-002`, §5.2.2); y **la primera tanda es de ventas**, `RF-IN-001` a `RF-IN-004`, contadas **por línea** (`RN-IN-003`) y **separadas por moneda** (`RN-IN-004`). Se incorpora **sin tablas propias**, a sabiendas de `modules.md` §2.1 (§1.4). Los cuatro permisos quedan declarados y sin sembrar (§5.2.4). | Bonilla Diaz William Steven |
| 0.2.0 | 06-10-2026 | **Las cuatro tripletas de ventas están escritas** —`spec.md` y `plan.md` aprobados, `tasks.md` en revisión—, en `docs/specs/in/001` a `004`, con los criterios `CA-IN-001` a `CA-IN-037`. Fijan lo que este documento dejaba abierto: `MV` publica **`SalesFigures`** (§3); el periodo se pide **en días de Bogotá**, con el último incluido, por defecto el mes en curso y con un **tope de 366 días**; el corte fuera del alcance responde **ceros sin consultar**; los rankings se ordenan **por importe con moneda y por unidades sin ella**; y lo **sin asignar** va aparte, solo para administración. La migración de los cuatro permisos la construye `RF-IN-001` · `T-01`. Se corrige además `RN-IN-004`: los importes van **en decimales**, como el resto de la API, y no en centésimas. | Bonilla Diaz William Steven |
| 0.3.0 | 06-10-2026 | **Las cuatro `tasks.md` aprobadas** por el responsable del proyecto, y **`RF-IN-001` construido**: `V74` siembra los cuatro permisos (catálogo 189), `MV` publica `SalesFigures` y `GET /indicators/sales/summary` responde. Dos desviaciones, en sus tareas: la suma se prueba por HTTP y no con una suite propia de `MV`, y una fecha o un identificador mal formados son `VAL-001` del manejador común. | Bonilla Diaz William Steven |
| 0.4.0 | 06-10-2026 | **`RF-IN-002` construido**: `GET /indicators/sales/series`, lo confirmado por día, semana —de lunes— o mes de Bogotá, con todos los tramos y todas las monedas del periodo en cada uno. `SalesFigures` gana `confirmedByBucket`; el predicado de `MV` queda en un solo sitio para que la serie sume exactamente el resumen. | Bonilla Diaz William Steven |
| 0.5.0 | 06-10-2026 | **El resumen de ventas gana el total y las gratuitas** (`RF-IN-001` enmendado; Art. I.7), por decisión del responsable del proyecto: el número de ventas sea cual sea su estado, y en cada estado y en el total cuántas fueron **gratuitas**. Nace **`RN-IN-008`**: gratuita es la venta de importe cero, la del método `GRATIS`, y sigue contando como venta. Mismo permiso y misma ruta. | Bonilla Diaz William Steven |
| 0.6.0 | 06-10-2026 | **Nace la tanda de puntos: `RF-IN-005`, el resumen de puntos**, a petición del responsable del proyecto, con su tripleta (`CA-IN-041` a `CA-IN-049`) y el submódulo Puntos. Nace **`RN-IN-009`**: comprado, redimido y ajuste son el evento del asiento de la cuenta `PUNTOS`; el periodo mira cuándo se movieron los puntos y el saldo es el de hoy. Permiso propio, `indicators:read-points-summary`, que sembrará `V76` por tipo de rol a `FUNCIONARIO` y `VENDEDOR`. `MV` publicará `PointsFigures`. | Bonilla Diaz William Steven |
| 0.7.0 | 06-10-2026 | **`RF-IN-005` construido**: `V76` siembra `indicators:read-points-summary` (catálogo 197), `MV` publica `PointsFigures` y `GET /indicators/points/summary` responde. | Bonilla Diaz William Steven |
| 0.8.0 | 06-10-2026 | **Nace `RN-IN-010`: sin fechas, todo; sin tope; y cada indicador se parte en tramos si se pide**, por decisión del responsable del proyecto. Enmienda `RF-IN-001`, `RF-IN-002` y `RF-IN-005` (Art. I.7): el periodo por defecto deja de ser el mes en curso, `VAL-003` se retira, y el resumen de ventas y el de puntos ganan `granularity`, que añade a la respuesta sus cifras por tramo sin cambiar los totales. `CA-IN-050` a `CA-IN-058`. | Bonilla Diaz William Steven |
| 0.9.0 | 06-10-2026 | **Nace `RF-IN-006`, el resumen de líneas de venta**, a petición del responsable del proyecto, con su tripleta (`CA-IN-059` a `CA-IN-066`): unidades vendidas, ventas por estado y lo sin vendedor, sobre todo el libro. Nace **`RN-IN-011`**, la primera excepción a `RN-IN-002`: este indicador no se acota por alcance, y su permiso, `indicators:read-sale-lines-summary`, se sembrará por `V78` solo a `SUPERADMIN` y `ADMIN`. | Bonilla Diaz William Steven |
| 0.10.0 | 06-10-2026 | **`RF-IN-006` construido**: `V78` siembra `indicators:read-sale-lines-summary` a `SUPERADMIN` y `ADMIN` (catálogo 203), `SalesFigures` gana lo sin vendedor y `GET /indicators/sales/lines/summary` responde, sin alcance. | Bonilla Diaz William Steven |
| 0.11.0 | 07-10-2026 | **`RF-IN-006` se agrupa por tipo de producto** (Art. I.7), por decisión del responsable del proyecto: **lo vendido** —solo lo confirmado— por tipo y en total, y **lo sin vendedor** también por tipo y en total. Desaparecen los bloques de pendientes y anuladas. `CA-IN-067` a `CA-IN-070`. | Bonilla Diaz William Steven |
| 0.12.0 | 07-10-2026 | **El saldo de puntos pasa a ser el del cierre del periodo** (`RF-IN-005`, `RN-IN-009`, `RN-IN-010`), a petición del responsable del proyecto: la suma de los asientos de las cuentas `PUNTOS` del alcance **hasta el final del día `to`**, en hora de Bogotá; **sin `to`, el de hoy**, como antes. Con `from`, el saldo sigue siendo acumulado —todo lo anterior a `to`—, no lo del periodo. Sigue sin partirse por tramo. Sin migración ni permisos. | Responsable del proyecto |
| 0.13.0 | 07-10-2026 | **El saldo de puntos se calcula con las cuatro cifras del periodo** (`RF-IN-005` 0.4.0), corrigiendo la 0.12.0 por indicación del responsable del proyecto —«calcula el balance con los datos de los puntos, no por aparte»—: `balance = comprados − redimidos + sumados − restados` **del periodo**, sin lectura de saldo aparte; puede ser negativo; sin fechas son los puntos de hoy. Va también **por tramo**. Una moneda aparece solo si tuvo movimiento en el periodo. | Responsable del proyecto |
| 0.14.0 | 07-10-2026 | **Nace la tanda de comisiones con `RF-IN-007`, el resumen de lotes de comisiones**, a petición del responsable del proyecto: por estado —abiertos, pendientes, pagados— y en total, cuántos lotes y su valor por moneda. Tripleta el mismo día (`CA-IN-072` a `CA-IN-079`), `tasks.md` en revisión. **Administración y sin alcance** (`RN-IN-011`, ampliada) y **sin periodo** —nace **`RN-IN-012`**, excepción a `RN-IN-010`: un indicador de estado es una foto de hoy—. Nace el submódulo Comisiones e **`IN` pasa a depender de `CM`**, que publicará `CommissionBatchFigures`. Permiso propio, `indicators:read-commission-batches-summary`, que sembrará `V79` solo a `SUPERADMIN` y `ADMIN`. | Responsable técnico |
| 0.15.0 | 07-10-2026 | **`RF-IN-007` construido**, con sus `tasks.md` aprobadas por el responsable del proyecto: `V79` siembra `indicators:read-commission-batches-summary` a `SUPERADMIN` y `ADMIN` (catálogo 204), `CM` publica `CommissionBatchFigures` y `GET /indicators/commissions/batches/summary` responde, sin alcance y sin periodo. | Responsable técnico |
| 0.16.0 | 07-10-2026 | **`RF-IN-006` se filtra por vendedor, cliente, producto y comprobante** (`spec.md` 0.3.0, `CA-IN-080` a `CA-IN-085`), a petición del responsable del proyecto: estrechan lo vendido y lo sin vendedor, en total, por tipo y por tramo, sin cambiar la respuesta. No son alcance. `SalesFigures` gana `LineFilter`. Sin migración ni permisos. | Responsable técnico |
| 0.17.0 | 07-10-2026 | **`RF-IN-007` se filtra por vendedor y por fechas** (`spec.md` 0.2.0, `CA-IN-086` a `CA-IN-089`), a petición del responsable del proyecto: las fechas eligen los lotes cuyo periodo de comisiones toca el rango, y el estado sigue siendo el de hoy. **`RN-IN-012` enmendada**: deja de prohibir fechas. La respuesta gana `period`. `CommissionBatchFigures` gana `BatchFilter`. Sin migración ni permisos. | Responsable técnico |
| 0.18.0 | 07-10-2026 | **Nace `RF-IN-008`, el resumen de mis comisiones**, a petición del responsable del proyecto: de las comisiones propias, cuántas y cuánto por estado del lote —abierto, pendiente, pagado— y en total. Tripleta el mismo día (`CA-IN-090` a `CA-IN-096`), `tasks.md` aprobadas, y **construido**: `V83` siembra el permiso (catálogo 206), `CM` amplía `CommissionBatchFigures` y `GET /indicators/commissions/mine/summary` responde. **Nace `RN-IN-013`**, excepción a `RN-IN-002`: un indicador personal cuenta solo lo de quien pregunta. El estado es el de hoy y las fechas, opcionales, son las del nacimiento de la comisión (`RN-IN-012`). Permiso propio, `indicators:read-own-commissions-summary`, a todo rol que porte `commission-batches:list-own`. | Responsable técnico |
| 0.19.0 | 07-10-2026 | **`RF-IN-008` se filtra por cliente** (`spec.md` 0.2.0, `CA-IN-097`), a petición del responsable del proyecto, igual que `RF-CM-026` 0.2.0: solo las comisiones de ventas a nombre de esa persona. Sin migración ni permisos. | Responsable técnico |
