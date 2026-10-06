# SPEC — `RF-IN-005` Consultar el resumen de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-005` |
| Módulo | `IN` — Indicadores |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! warning "Enmendado el 06-10-2026 — alineado con la lista de los movimientos de puntos (`RF-MV-056`)"

    Decisión del responsable del proyecto, 06-10-2026: «actualiza el indicador de puntos según el endpoint de consulta de todos los puntos de admin» —la lista de los movimientos de puntos de `RF-MV-056`—, con tres decisiones suyas: **la fecha es cuándo ocurrió**, como en la lista; **las compras van por estado, con lo pagado**; y **los nombres son los de la lista**. **El indicador suma las filas de esa lista** —la misma definición, la misma fecha—, de modo que con los mismos filtros cuadran. **Las compras van por estado** —confirmadas, pendientes y rechazadas—, cada una con cuántas, sus puntos y **lo pagado** por moneda; los puntos de una pendiente o rechazada son los que daría o habría dado, y **no** mueven el saldo. **Los gastos** son una fila por venta pagada con puntos, con lo que de verdad se descontó, y **su fecha es cuándo se descontó**. **Los ajustes**, lo sumado y lo restado. **La fecha de compras y ajustes es cuándo ocurrieron**, no cuándo entraron los puntos: una compra pedida el 30 y cobrada el 1 cuenta el 30. Se añaden los filtros de la lista **por tipo y por estado**; ninguno de los dos cambia el saldo, que sigue siendo el de hoy. **La respuesta cambia de forma**: `purchases`, `spent` y `adjustments` en lugar de `purchased`, `redeemed`, `added` y `removed`.

!!! warning "Enmendado el 06-10-2026 — sin fechas, todo; y cada indicador se puede partir en tramos (RN-IN-010)"

    Decisión del responsable del proyecto, 06-10-2026: «los indicadores se recogen en su totalidad a no ser que se les envíe una fecha en los filtros», y «tener la capacidad de pedir los indicadores por meses, por días y por semanas, y adicionalmente un filtro de inicio y fin; si van vacíos se consulta todo». **Sin fechas, todo**: comprados, redimidos y ajustes de **toda la historia**, de modo que, sin fechas, **el saldo siempre es comprados − redimidos + sumados − restados**. Con una sola fecha, la otra queda abierta; sin tope. **Con tramo**, la respuesta trae además, **por cada tramo**, las cuatro clases por moneda; **el saldo no se parte**, porque es el de hoy.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Saber **cuántos puntos se han comprado, cuántos se han redimido y cuántos hay**, en el alcance de quien pregunta: lo que entró y salió en un periodo, y el saldo de hoy.

---

## 2. Contexto

**Es la segunda tanda del módulo** —la primera fue la de ventas— y la pidió el responsable del proyecto el 06-10-2026: «el siguiente indicador es para saber la información de los puntos: cuántos se han comprado, cuántos se han redimido y el balance». Tres decisiones suyas del mismo día lo definen:

| Pregunta | Decisión |
|---|---|
| ¿De quién son los puntos que cuenta? | **Según el alcance**, como los de ventas (`RN-IN-002`): administración, los de toda la plataforma; un vendedor, los suyos y los de las personas de su red; cualquier otro, los suyos |
| ¿Y los ajustes a mano? | **Aparte**, con lo sumado y lo restado, para que el saldo se explique: comprados − redimidos ± ajustes |
| ¿El saldo de cuándo? | **El de hoy**, sea cual sea el periodo; lo comprado, lo redimido y los ajustes son **los del periodo** |

### 2.1 Qué es cada cifra

Los puntos son un saldo propio de cada persona, separado del dinero y **uno por moneda**: se compran en una moneda y se gastan en ella ([`requirements/mv.md`](../../../requirements/mv.md) §4.4). Cada cambio en ese saldo es uno de tres hechos, y cada hecho es una cifra:

| Cifra | Es | No es |
|---|---|---|
| **Comprados** | Los puntos que **entraron** por una compra de puntos **ya cobrada** | Una compra pendiente o rechazada: todavía no dio ningún punto |
| **Redimidos** | Los puntos que **salieron** al pagar una venta con ellos | El valor en dinero de esa venta |
| **Ajustes** | Lo que administración **sumó** o **restó** a mano, cada sentido por separado | Una compra ni un pago: es una corrección |
| **Saldo** | Los puntos que las personas del alcance **tienen hoy** | El saldo al final del periodo |

**El periodo se mira sobre cuándo se movieron los puntos**: una compra cuenta el día en que se cobró —que es cuando dio los puntos—, no el día en que se pidió. Es la diferencia con los indicadores de ventas, que miran cuándo ocurrió la venta, y es a propósito: aquí se cuentan puntos, y los puntos de una compra pendiente no existen.

### 2.2 Por qué el saldo no cuadra con el periodo, y cuándo sí

El saldo es **el de hoy** y las otras cifras son **las del periodo**, de modo que en general no cuadran. **Cuadran exactamente cuando el periodo cubre todo lo ocurrido hasta hoy**: entonces el saldo es comprados − redimidos + sumados − restados. Es la prueba de que nada se pierde, y es un criterio de aceptación.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien porta un rol de tipo **funcionario** | Ve los puntos de **todas** las personas |
| Quien porta un rol de tipo **vendedor** | Ve **los suyos y los de las personas de su red**, en toda la profundidad |
| Cualquier otro con el permiso | Ve **los suyos** |

**Todos entran por el mismo permiso**, propio de este indicador (`RN-IN-001`), y **lo que ven lo decide su alcance** (`RN-IN-002`).

---

## 4. Alcance

### 4.1 Incluye

- Por moneda, en el periodo: **comprados**, **redimidos**, **sumados por ajuste** y **restados por ajuste**, con cuántos movimientos hubo de cada clase.
- Por moneda, **el saldo de hoy**.
- Acotar a **una moneda** y a **una persona** del alcance.

### 4.2 No incluye

- **El dinero de las compras de puntos**: cuánto se pagó por ellos. Es otra pregunta, y otro indicador si se pide.
- **El valor en dinero de lo redimido**: depende de la tasa del día de cada pago.
- **Los puntos de la empresa**: la cuenta que lleva los puntos en circulación no es de ninguna persona.
- **El saldo de otro día que hoy**.
- **La evolución en el tiempo** y **los rankings** (quién compra o redime más): indicadores propios si se piden.
- **Los clientes de un vendedor**: el alcance recorre la estructura de mando, no la cartera (`RN-SP-049`), igual que en ventas.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-IN-001`** | Permiso propio; ningún otro lo abre |
| **`RN-IN-002`** | El alcance por tipo de rol; una persona fuera de él da **ceros**, no un error |
| **`RN-IN-004`** | Un saldo de puntos **por moneda**; nunca se suman puntos de monedas distintas |
| **`RN-IN-007`** | Los días del periodo son los de Bogotá |
| **`RN-IN-009`** | Qué es comprado, redimido y ajuste, y que el periodo mira cuándo se movieron (nace aquí) |
| `RN-MV-051`, `RN-MV-052` | Comprar da puntos al cobrarse; pagar con puntos los descuenta en el acto |
| `RN-MV-076` | El ajuste a mano suma o resta, con su motivo |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desde, hasta | No | Como en `RF-IN-001`: días de Bogotá, el último incluido, **sin fechas, toda la historia; una sola deja la otra abierta; sin tope** (06-10-2026, `RN-IN-010`). **No acotan el saldo** |
| Moneda | No | Solo esa moneda; una que no exista da ceros |
| Persona | No | Solo los puntos de esa persona, **si está en mi alcance**; si no —o no existe—, ceros |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo | El efectivo, como en `RF-IN-001` |
| Por moneda | La moneda; **comprados** (puntos y compras), **redimidos** (puntos y ventas), **sumados** y **restados** por ajuste (puntos y ajustes), y **el saldo de hoy** |

**Una moneda aparece si en ella hay algo que contar**: un movimiento en el periodo o un saldo distinto de cero hoy. Los puntos van con dos decimales, como se guardan, y **siempre en positivo**: «redimidos 300» y no «−300»; el sentido lo da el nombre de la cifra.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso del resumen de puntos |
| Postcondición | **Ninguna.** No se escribe ni se audita nada |

---

## 8. Flujo principal

1. El actor pide el resumen de puntos, con los filtros que quiera.
2. El sistema comprueba el permiso, fija y valida el periodo.
3. Resuelve el alcance y lo estrecha a la persona pedida, si la hay y está dentro.
4. Suma, por moneda, lo que entró y salió de los puntos de esas personas en el periodo, separado por clase.
5. Suma, por moneda, el saldo de hoy de esas personas.
6. Devuelve las dos cosas juntas.

---

## 9. Flujos alternativos

### FA-001 — Nadie del alcance tiene puntos

Lista vacía. No es un error.

### FA-002 — La persona pedida no está en mi alcance, o no existe

**Ceros**, sin consultar: el indicador no confirma quién cuelga de quién.

### FA-003 — Un periodo sin movimientos, con saldo

La moneda aparece con las cifras del periodo en cero y su saldo de hoy.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso del resumen de puntos | Prohibido |

---

## 11. Validaciones

Las de `RF-IN-001` §11 —el periodo y los identificadores—, con sus mismos códigos.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-041` | ~~Comprados son los puntos de las compras cobradas~~ — **sustituido el 06-10-2026 por `CA-IN-068`**: las compras van por estado |
| `CA-IN-042` | Lo **gastado** —antes «redimido»— son los puntos descontados al pagar ventas, en positivo (nombre cambiado el 06-10-2026) |
| `CA-IN-043` | Los **ajustes** van aparte: lo sumado y lo restado, cada uno con su cuenta de ajustes |
| `CA-IN-044` | El **saldo** es el de hoy, sea cual sea el periodo; y con un periodo que lo cubre todo, **saldo = comprados − redimidos + sumados − restados** |
| `CA-IN-045` | Todo va **por moneda**: los puntos de una moneda no se suman con los de otra |
| `CA-IN-046` | El **alcance** es el de ventas: un funcionario ve todos; un director, los suyos y los de sus agentes, no los de otra rama; un agente, los suyos |
| `CA-IN-047` | El filtro por **persona** acota dentro del alcance; fuera o inexistente, ceros y no error. El de **moneda** acota |
| `CA-IN-048` | ~~El periodo es sobre cuándo se movieron los puntos~~ — **sustituido el 06-10-2026 por `CA-IN-072`** |
| `CA-IN-049` | Sin el permiso, **prohibido**; sin autenticar, `401`; **ningún permiso de ventas lo abre** |
| `CA-IN-057` | **Sin fechas, toda la historia**, y entonces **el saldo es siempre comprados − redimidos + sumados − restados** (06-10-2026) |
| `CA-IN-058` | **Con tramo**, cada tramo trae las cuatro clases por moneda, todos los tramos presentes, y la suma de los tramos es el total del periodo; **el saldo solo va en el total** (06-10-2026) |
| `CA-IN-067` | **Con los mismos filtros, las cifras son la suma de las filas de la lista de administración** de los movimientos de puntos: cuántas y cuántos puntos por tipo, y lo pagado de las compras (06-10-2026) |
| `CA-IN-068` | **Las compras van por estado** —confirmadas, pendientes, rechazadas—, con cuántas, sus puntos y lo pagado; las pendientes y rechazadas **no** mueven el saldo (06-10-2026) |
| `CA-IN-069` | **Los gastos** son una fila por venta pagada con puntos, con lo descontado, en la fecha del descuento: una venta registrada antes y pagada con puntos después cae en la fecha del pago (06-10-2026) |
| `CA-IN-070` | Los filtros **por tipo y por estado** acotan como en la lista; uno desconocido es un error, junto a los demás (06-10-2026) |
| `CA-IN-071` | **El saldo** es el de hoy: no lo cambian ni el periodo, ni el tipo, ni el estado; y sin filtros, saldo = compras confirmadas − gastos + sumados − restados (06-10-2026) |
| `CA-IN-072` | **La fecha de compras y ajustes es cuándo ocurrieron**: una compra pedida el 30 y cobrada el 1 cuenta el 30 (06-10-2026) |

**`CA-IN-044` es el que sostiene el indicador**: si el saldo y las cifras del periodo no cuadran cuando deben, alguna clase se está contando mal o falta.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta pagada con puntos de **varias líneas** | Cuenta **una** venta redimida, con todos los puntos que se descontaron |
| Una persona que **dejó la red** | Sus puntos dejan de contar para mí, también su saldo |
| Un ajuste que **resta todo el saldo** | Saldo cero; la moneda sigue apareciendo si hubo movimientos en el periodo |
| Una moneda **desactivada** con saldo | Aparece con su saldo |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión, con la tanda de puntos del módulo `IN`, a petición del responsable del proyecto. Tres decisiones suyas: **según el alcance**, **los ajustes aparte** y **el saldo de hoy** con las cifras del periodo. Nace `RN-IN-009`. Nueve criterios, `CA-IN-041` a `CA-IN-049`. | Responsable técnico |
| 0.2.0 | 06-10-2026 | **`RN-IN-010`**: sin fechas, toda la historia; sin tope; y el tramo opcional, sin partir el saldo. `CA-IN-057` y `CA-IN-058`. | Responsable técnico |
| 0.3.0 | 06-10-2026 | **Alineado con la lista de los movimientos de puntos** (`RF-MV-056`): suma sus filas, con su fecha; compras por estado con lo pagado; nombres y filtros de la lista. `CA-IN-067` a `CA-IN-072`; `CA-IN-041` y `CA-IN-048` sustituidos, `CA-IN-042` renombrado. | Responsable técnico |
