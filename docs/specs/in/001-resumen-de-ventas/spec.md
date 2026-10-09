# SPEC — `RF-IN-001` Consultar el resumen de ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-001` |
| Módulo | `IN` — Indicadores |
| Versión | 0.4.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! warning "Enmendado el 06-10-2026 — sin fechas, todo; y cada indicador se puede partir en tramos (RN-IN-010)"

    Decisión del responsable del proyecto, 06-10-2026: «los indicadores se recogen en su totalidad a no ser que se les envíe una fecha en los filtros», y «tener la capacidad de pedir los indicadores por meses, por días y por semanas, y adicionalmente un filtro de inicio y fin; si van vacíos se consulta todo». **El periodo**: sin «desde» ni «hasta», **todas las ventas de la historia**; con una sola fecha, la otra queda abierta; y **ya no hay tope de 366 días** (`VAL-003` se retira). **Los tramos**: con un tramo pedido —día, semana o mes—, el resumen trae además, **por cada tramo**, los mismos bloques —total, confirmadas, pendientes, anuladas, con sus gratuitas—; todos los tramos aparecen, la suma de los tramos es el total, y sin tramo la respuesta es la de siempre. Los tramos los decide `RF-IN-002` §2.2: del calendario de Bogotá, la semana de lunes, recortados al periodo.

!!! warning "Enmendado el 06-10-2026 — el total de ventas y las gratuitas"

    Por decisión del responsable del proyecto: el indicador de ventas tiene que dar **el número de ventas**, **el total por estado** y **cuántas fueron gratuitas**. El total por estado ya estaba; faltaban los otros dos, y entran **en este mismo resumen**, con su permiso y su ruta, en lugar de en un indicador nuevo. **El total** es el número de ventas del periodo y del alcance **sea cual sea su estado**: la suma de las confirmadas, las pendientes y las anuladas, que no se solapan porque cada venta está en un solo estado. **Las gratuitas** se cuentan **en cada estado y en el total** (`RN-IN-008`): una venta es gratuita si su importe a pagar es cero —la del alta por enlace—, y **sigue contando dentro de su estado**, de modo que «las pagadas» son las confirmadas menos las gratuitas confirmadas. La gratuidad es **de la venta entera**: una venta cobrada que tenga una línea a cero no es gratuita.

!!! warning "Enmendado el 09-10-2026 — se filtra por oficina (RN-IN-014)"

    Decisión del responsable del proyecto, 09-10-2026: cada línea de venta guarda **la oficina donde se vendió** —el equipo del director de la cadena de su vendedor, **vigente el día de la venta**— y esa oficina **no se mueve** cuando alguien se traslada (`RN-MV-078` de [`requirements/mv.md`](../../../requirements/mv.md)). **El resumen gana un filtro opcional por oficina**, que cuenta **solo las líneas cuya oficina guardada es esa**: no la del equipo al que pertenece hoy el vendedor, de modo que trasladar a un agente **no se lleva sus cifras** a la otra oficina. **Es un filtro y no alcance**, como el de vendedor: **se combina con el alcance** de quien pregunta —dentro de él lo estrecha; un agente que pide otra oficina recibe **ceros**, porque ninguna de sus líneas es de ella— y con el vendedor, la moneda, el periodo y el tramo. **Se suma por línea** como siempre (`RN-IN-003`): una venta con líneas de dos oficinas cuenta **una vez** en cada una, con solo su parte. **Las líneas sin vendedor no tienen oficina**, de modo que con oficina el funcionario deja de verlas; **tampoco la tiene la venta de un manager** ni la de un vendedor sin director con equipo. **Una oficina que no existe da ceros**, no un error, como un vendedor inexistente; un identificador mal formado es `400`. **No cambia**: el alcance, las cifras sin el filtro, la forma de la respuesta. `CA-IN-098` a `CA-IN-100`.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien tenga el permiso sepa **cuánto se vendió en un periodo dentro de su alcance**: cuántas ventas se confirmaron, cuántas líneas y unidades, y por cuánto en cada moneda; y aparte, cuánto sigue **pendiente** de cobro y cuánto se **anuló**.

---

## 2. Contexto

**Es el primer indicador del módulo `IN`** ([`requirements/in.md`](../../../requirements/in.md) §1.2), que nació el 06-10-2026 para que el reparto de indicadores por rol lo decida quien administra roles. Es el más simple de los cuatro de ventas y por eso el primero: **fija qué es una venta para un indicador, de quién es y en qué moneda se cuenta**, y los otros tres —la evolución, por producto y por vendedor— **heredan esas definiciones** y solo cambian cómo agrupan.

**La misma pantalla, cifras distintas.** Un agente, su director y el administrador piden el mismo resumen y reciben números distintos: el agente lo que vendió él, el director lo suyo y lo de sus agentes, el administrador todo (`RN-IN-002`). Es la decisión que `RF-MV-015` tomó para el listado de ventas, aplicada a sus sumas.

### 2.1 Por qué no se suma lo que `RF-MV-015` ya lista

`RF-MV-015` enseña una venta **entera** si **alguna** de sus líneas es de mi red. Para un listado es lo correcto —la venta es una— y para una suma es un error: si una venta tiene una línea de mi agente y otra de un agente ajeno, sumar su total le contaría a mi director lo que vendió alguien que no es suyo. **Aquí se suma por línea** (`RN-IN-003`): la venta **cuenta como una** si alguna de sus líneas es de mi alcance, y **su importe es solo el de esas líneas**.

La consecuencia, que conviene ver antes de que alguien la reporte como defecto: **el importe del resumen de un vendedor puede ser menor que la suma de los totales de las ventas que ve en su listado.** Las dos cifras son correctas; responden preguntas distintas.

### 2.2 Por qué el dinero no se suma entre monedas

Una venta en pesos y otra en dólares no tienen un total sin una tasa, y **escoger la tasa es una decisión de negocio** —¿la de hoy, la del día de cada venta, la de cobro o la de retiro?— que un indicador no puede tomar a escondidas (`RN-IN-004`). Se devuelve **un importe por moneda**. Las **cantidades** —ventas, líneas, unidades— no tienen moneda y sí se suman.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien porta un rol de tipo **funcionario** | Ve el resumen de **toda** la plataforma, con las líneas que aún no tienen vendedor dentro |
| Quien porta un rol de tipo **vendedor** | Ve el resumen de **lo que vendió él y su red**, en toda la profundidad |
| Cualquier otro con el permiso | Ve **lo que vendió él**, que normalmente es nada |

**Todos entran por el mismo permiso**, que abre el indicador; lo que cada uno ve lo decide su alcance (`RN-IN-002`).

---

## 4. Alcance

### 4.1 Incluye

- Para un periodo: las ventas **confirmadas** —cuántas, cuántas líneas, cuántas unidades y el importe por moneda—, las **pendientes** y las **anuladas** —cuántas y el importe por moneda—.
- Acotar a **una moneda** y a **un vendedor** de mi alcance.
- Acotar a **una oficina**: la guardada en cada línea el día de la venta (09-10-2026, `RN-IN-014`).
- El periodo **por defecto**: el mes en curso, hasta hoy.

### 4.2 No incluye

- **Comparar con el periodo anterior** («+12 % frente a agosto»). Se obtiene pidiendo los dos periodos; un campo de variación obligaría a decidir qué es «el anterior» de un rango arbitrario.
- **Un total en una sola moneda** (§2.2).
- **Las compras de puntos**, que no venden un producto (`RN-IN-005`).
- **Lo que el actor compró**: un vendedor que compra no se cuenta aquí como vendedor de nada.
- **Comisiones**: son de la tanda de `CM`.
- La evolución, el desglose por producto y el desglose por vendedor → `RF-IN-002`, `RF-IN-003`, `RF-IN-004`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-IN-001`** | El indicador tiene su permiso propio; ningún otro lo abre |
| **`RN-IN-002`** | El alcance lo fija quién pregunta: funcionario, todo; vendedor, él y su red vigente en profundidad; cualquier otro, él. Un vendedor fuera del alcance da **ceros**, no un error |
| **`RN-IN-003`** | Se cuenta por línea: el importe es el de las líneas del alcance; una venta cuenta una vez si alguna de sus líneas está dentro; las líneas sin vendedor solo para el funcionario |
| **`RN-IN-004`** | Un importe por moneda, nunca sumados entre sí; las cantidades sí se suman |
| **`RN-IN-005`** | Solo ventas; el periodo es sobre cuándo **ocurrió**; «vendido» es confirmado; pendiente y anulado aparte; el alta gratuita cuenta con importe cero |
| **`RN-IN-007`** | Los días del periodo son los de Bogotá |
| **`RN-IN-008`** | Gratuita es la venta de importe cero; sigue contando en su estado (06-10-2026) |
| **`RN-IN-014`** | La oficina de una cifra es la guardada en la línea el día de la venta; es filtro y no alcance; con ella, lo sin vendedor no cuenta; una inexistente da ceros (09-10-2026) |
| `RN-MV-078` | Cada línea de venta guarda la oficina donde se vendió, y un traslado no la mueve (09-10-2026) |
| `RN-MV-003` | El vendedor es de la línea |
| `RN-MV-031` | La precedencia funcionario > vendedor > consumidor, y «mi red» es la estructura de mando vigente |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desde | No | **Día** de Bogotá en que empieza el periodo, incluido. **Sin él, desde el principio** (06-10-2026; antes, el primero del mes en curso) |
| Hasta | No | **Día** de Bogotá en que acaba el periodo, **incluido entero**. Por defecto, hoy |
| Tramo (06-10-2026) | No | Día, semana o mes. **Sin él, no hay tramos**: la respuesta es solo el total |
| Moneda | No | Solo lo vendido en esa moneda. Una que no exista da **ceros**, no un error: el filtro no es un catálogo de monedas |
| Vendedor | No | Solo lo que vendió esa persona, **si está en mi alcance**. Fuera de él —o inexistente— da **ceros**: el indicador no confirma quién cuelga de quién |
| Oficina (09-10-2026) | No | Solo las líneas **cuya oficina guardada** es esa —la del director del vendedor el día de la venta, no la de hoy— (`RN-IN-014`). Se combina con el alcance y con el vendedor: fuera de lo que puedo ver, **ceros**. Las líneas sin vendedor no tienen oficina. Una oficina inexistente da **ceros** |

**El periodo se da en días y no en instantes**, al revés que los listados de movimientos. Un indicador se pregunta en días —«septiembre», «esta semana»— y quien lo pide no debería tener que calcular a qué hora UTC empieza el uno de septiembre en Bogotá. Por dentro sigue siendo **semiabierto**: del comienzo del primer día al comienzo del día siguiente al último.

~~**El periodo no puede pasar de 366 días.**~~ **Retirado el 06-10-2026** (`RN-IN-010`): si la historia entera se puede pedir sin fechas, acotar un rango con fechas no protege nada. Es el año con bisiesto: suficiente para cualquier comparación anual, y un tope que impide que una petición recorra el libro entero de varios años.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo | Los dos días efectivos —los pedidos o los de por defecto— y la zona en que se interpretan |
| Total (06-10-2026) | Ventas, **sea cual sea su estado**, y cuántas de ellas fueron gratuitas |
| Confirmadas | Ventas, líneas, unidades, el importe **por moneda**, y cuántas fueron gratuitas |
| Pendientes | Ventas, el importe por moneda y cuántas fueron gratuitas |
| Anuladas | Ventas, el importe por moneda y cuántas fueron gratuitas |

**Cada importe va con su moneda** —identificador y código—, y **una moneda sin ventas no aparece**: una lista de ceros por cada moneda del catálogo no informa nada. Sin ventas en el periodo, las cantidades son cero y las listas de importes están vacías.

**El periodo efectivo se devuelve** para que quien pidió sin fechas sepa qué se le contó.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso del resumen de ventas |
| Postcondición | **Ninguna.** No se escribe nada y no se audita: consultar con permiso no es un evento |

---

## 8. Flujo principal

1. El actor pide el resumen, con los filtros que quiera.
2. El sistema comprueba que tiene el permiso.
3. Fija el periodo —el pedido o el de por defecto— y lo valida.
4. **Resuelve hasta dónde llega el actor** (`RN-IN-002`) y lo estrecha al vendedor pedido, si lo hay y está dentro.
5. Suma las líneas de venta del alcance —y, si se pidió oficina, solo las que la guardan (09-10-2026)— cuyas ventas ocurrieron en el periodo, separando confirmadas, pendientes y anuladas, y cada importe por moneda.
6. Devuelve el resumen con el periodo efectivo.

---

## 9. Flujos alternativos

### FA-001 — No hay ventas en el periodo

Cantidades en cero y listas de importes vacías. No es un error.

### FA-002 — El vendedor pedido no está en mi alcance, o no existe

**Ceros**, exactamente como si no hubiera vendido nada. El sistema no consulta las ventas: fuera del alcance la respuesta es cero **por definición** (`RN-IN-002`).

### FA-003 — El actor porta roles de más de un tipo

Se rige por el de **mayor precedencia**, como en `RF-MV-015` · `FA-003`.

### FA-004 — Una venta con líneas de vendedores dentro y fuera de mi red

Cuenta **una** venta, con **solo el importe, las líneas y las unidades de las líneas de mi red** (§2.1).

### FA-005 — Una venta por validar, con líneas sin vendedor

Para el funcionario cuenta entera. Para un vendedor **no cuenta** mientras ninguna línea sea suya —no tiene vendedor que lo ponga en su alcance— y entra en cuanto se le asigne (`RN-IN-003`).

### FA-006 — Alguien de mi red dejó de estarlo

Lo que vendió **deja de contarse** para mí desde ese instante, aunque lo vendiera cuando era mío. Es la misma lectura de «vigente» que `RF-MV-015` · `FA-006`: el resumen de mi red y el listado de mi red **cuentan las mismas ventas**.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso del resumen | Prohibido |

**No hay excepción para «el vendedor no es de tu red»**: sería un oráculo de la estructura (`FA-002`).

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | «Desde» y «hasta», si vienen, son días bien formados |
| `VAL-002` | «Desde» no es posterior a «hasta» |
| ~~`VAL-003`~~ | ~~El periodo efectivo no pasa de 366 días~~ — **retirada el 06-10-2026** (`RN-IN-010`) |
| `VAL-005` | El tramo, si viene, es día, semana o mes (06-10-2026) |
| `VAL-004` | La moneda, el vendedor y la oficina (09-10-2026), si vienen, son identificadores bien formados |

**Los problemas de validación se devuelven juntos**, como en todo el sistema. Un «desde» sin «hasta» toma hoy como final; un «hasta» sin «desde» toma el primero de **su** mes, no del mes en curso, para que pedir «hasta el 31 de agosto» no produzca un rango invertido.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-001` | Un **funcionario** ve las ventas de **toda** la plataforma, incluidas las de vendedores que no cuelgan de nadie y las líneas **sin vendedor** |
| `CA-IN-002` | Un **agente** ve solo lo que vendió **él**: ni lo de otro agente del mismo director, ni lo que compró |
| `CA-IN-003` | Un **director** ve lo suyo **y lo de sus agentes**, y no lo de los agentes de otro director |
| `CA-IN-004` | Un **manager** ve lo suyo, lo de sus directores **y lo de los agentes de estos** |
| `CA-IN-005` | Una venta con una línea de mi red y otra ajena cuenta **una vez**, con **solo** el importe, las líneas y las unidades de la mía |
| `CA-IN-006` | Los importes van **por moneda** y nunca sumados entre monedas; las cantidades sí se suman; una moneda sin ventas no aparece |
| `CA-IN-007` | Confirmadas, pendientes y anuladas van **separadas**; ni las pendientes ni las anuladas se suman a lo vendido |
| `CA-IN-008` | Solo cuentan las **ventas**: una compra de puntos en el periodo no cambia el resumen; el alta gratuita confirmada suma **una venta** y **cero** de importe |
| `CA-IN-009` | El periodo es sobre **cuándo ocurrió** la venta, en días de **Bogotá**: una venta a las 20:00 del 30 de septiembre en Bogotá —ya 1 de octubre en UTC— cuenta en septiembre; el último día entra **entero** |
| `CA-IN-010` | ~~Sin fechas, el periodo es el mes en curso hasta hoy~~ — **sustituido el 06-10-2026 por `CA-IN-050`** |
| `CA-IN-011` | El filtro por **vendedor** acota dentro del alcance; uno fuera de mi red o inexistente da **ceros** y no un error. El filtro por **moneda** acota; una inexistente da ceros |
| `CA-IN-012` | Quien **dejó de colgar de mí** deja de contar para mí |
| `CA-IN-013` | Un rango invertido y un identificador mal formado son un error (el de más de 366 días se retira el 06-10-2026) |
| `CA-IN-014` | Sin el permiso, **prohibido**; sin autenticar, `401`; **ni el permiso del listado de ventas ni el de otro indicador lo abren** |
| `CA-IN-038` | El **total** es la suma de las confirmadas, las pendientes y las anuladas del alcance, y acota igual por periodo, vendedor y moneda (06-10-2026) |
| `CA-IN-039` | Las **gratuitas** se cuentan en cada estado y en el total; el alta gratuita confirmada cuenta **una** venta confirmada, con importe cero, y **una** gratuita confirmada (06-10-2026) |
| `CA-IN-040` | La gratuidad es **de la venta entera**: una venta cobrada con una línea a cero no es gratuita, y una gratuita lo es para todo vendedor que tenga una línea en ella (06-10-2026) |
| `CA-IN-050` | **Sin fechas, toda la historia**: una venta de hace años cuenta; la respuesta devuelve `from` vacío y `to` hoy (06-10-2026) |
| `CA-IN-051` | **Con una sola fecha, la otra queda abierta**: solo «desde», hasta hoy; solo «hasta», desde el principio (06-10-2026) |
| `CA-IN-052` | **No hay tope**: un periodo de varios años es válido (06-10-2026) |
| `CA-IN-053` | **Con tramo**, la respuesta trae por cada tramo los mismos bloques, todos los tramos presentes, y **la suma de los tramos es el total**; sin tramo, no trae tramos (06-10-2026) |
| `CA-IN-054` | Un tramo desconocido y un rango invertido son un error, **juntos** (06-10-2026) |
| `CA-IN-098` | Con **oficina**, el funcionario ve solo las líneas que la guardan: una venta con líneas de dos oficinas cuenta **una vez** con **solo** su parte, y las líneas **sin vendedor** y las de la venta de un **manager** no cuentan (09-10-2026) |
| `CA-IN-099` | La oficina es **la guardada en la línea**: trasladar después al director o al agente a otra oficina **no mueve** lo ya vendido —sigue contando en la de la venta y no en la nueva— (09-10-2026) |
| `CA-IN-100` | La oficina **se combina con el alcance**: un director que pide la suya ve lo suyo y lo de sus agentes de esa oficina; un agente que pide otra oficina recibe **ceros**; una oficina **inexistente** da ceros y no un error; un identificador **mal formado**, `400`; con tramo, la suma de los tramos es el total filtrado (09-10-2026) |

**`CA-IN-005` es el que sostiene el módulo**, y `CA-IN-011` el que lo protege: el primero prueba que se suma por línea y no por venta; el segundo, que el filtro por vendedor no se convierte en la forma de descubrir la estructura.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una **autoventa** —quien no cuelga de nadie se vende a sí mismo— | Cuenta para él como vendedor de la línea, y para su superior si lo tuviera |
| Una venta **pendiente** que se confirma después de la consulta | La próxima consulta la cuenta como confirmada en el mismo periodo: el periodo es sobre cuándo **ocurrió**, no sobre cuándo se confirmó |
| Una venta de **varias unidades** de un producto | Una venta, una línea, varias unidades |
| Un **paquete** | Una venta con una línea por producto (`RN-MV-028`): cuenta una venta y tantas líneas como productos |
| Un vendedor sin nadie a cargo | Lo suyo; la raíz se incluye |
| La estructura con un **ciclo**, aunque el sistema lo prohíba | La consulta termina: el alcance lo resuelve `SP` sin repetir |
| «Desde» igual a «hasta» | Un día entero |
| El libro vacío | Ceros |

---

## 14. Preguntas abiertas

**Contar por confirmación y no por ocurrencia.** Hoy el periodo es sobre cuándo **ocurrió** la venta, como en todos los listados. Para quien concilia caja, «lo que entró en septiembre» es lo **confirmado** en septiembre, aunque se vendiera en agosto. Si se pide, es un parámetro con dos valores, no un indicador nuevo, y se decide con el responsable del proyecto.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión, con el módulo `IN` ([`requirements/in.md`](../../../requirements/in.md) v0.1.0). Fija las definiciones que heredan los otros tres indicadores de ventas: **por línea** y no por venta (§2.1, `CA-IN-005`), **por moneda** (§2.2), el periodo **en días de Bogotá** con el último incluido y un tope de 366 días, y **ceros** fuera del alcance. Catorce criterios, `CA-IN-001` a `CA-IN-014`. | Responsable técnico |
| 0.2.0 | 06-10-2026 | **El total y las gratuitas** (§6.2, `RN-IN-008`, `CA-IN-038` a `CA-IN-040`), por decisión del responsable del proyecto: enmienda en el mismo resumen, sin indicador nuevo. Ampliación: ninguna cifra que ya se devolvía cambia. | Responsable técnico |
| 0.3.0 | 06-10-2026 | **`RN-IN-010`**: sin fechas, toda la historia; una sola fecha deja la otra abierta; sin tope (`VAL-003` retirada); y el tramo opcional, que añade los bloques por tramo. `CA-IN-050` a `CA-IN-054`; `CA-IN-010` sustituido. | Responsable técnico |
| 0.4.0 | 09-10-2026 | **Filtro por oficina** (`RN-IN-014`), por decisión del responsable del proyecto: cuenta las líneas cuya oficina guardada es esa —la del día de la venta, `RN-MV-078`—, de modo que un traslado no mueve las cifras. Filtro y no alcance: se combina con él; lo sin vendedor y la venta de un manager no tienen oficina; una inexistente da ceros. La respuesta no cambia. `CA-IN-098` a `CA-IN-100`. | Responsable técnico |
