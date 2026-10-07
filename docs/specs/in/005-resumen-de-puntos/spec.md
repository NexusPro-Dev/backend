# SPEC — `RF-IN-005` Consultar el resumen de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-005` |
| Módulo | `IN` — Indicadores |
| Versión | 0.4.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! warning "Enmendado el 06-10-2026 — sin fechas, todo; y cada indicador se puede partir en tramos (RN-IN-010)"

    Decisión del responsable del proyecto, 06-10-2026: «los indicadores se recogen en su totalidad a no ser que se les envíe una fecha en los filtros», y «tener la capacidad de pedir los indicadores por meses, por días y por semanas, y adicionalmente un filtro de inicio y fin; si van vacíos se consulta todo». **Sin fechas, todo**: comprados, redimidos y ajustes de **toda la historia**, de modo que, sin fechas, **el saldo siempre es comprados − redimidos + sumados − restados**. Con una sola fecha, la otra queda abierta; sin tope. **Con tramo**, la respuesta trae además, **por cada tramo**, las cuatro clases por moneda; **el saldo no se parte**, porque es el de hoy.

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Saber **cuántos puntos se han comprado, cuántos se han redimido y cuántos hay**, en el alcance de quien pregunta: lo que entró y salió en un periodo, y su saldo.

---

## 2. Contexto

**Es la segunda tanda del módulo** —la primera fue la de ventas— y la pidió el responsable del proyecto el 06-10-2026: «el siguiente indicador es para saber la información de los puntos: cuántos se han comprado, cuántos se han redimido y el balance». Tres decisiones suyas del mismo día lo definen:

| Pregunta | Decisión |
|---|---|
| ¿De quién son los puntos que cuenta? | **Según el alcance**, como los de ventas (`RN-IN-002`): administración, los de toda la plataforma; un vendedor, los suyos y los de las personas de su red; cualquier otro, los suyos |
| ¿Y los ajustes a mano? | **Aparte**, con lo sumado y lo restado, para que el saldo se explique: comprados − redimidos ± ajustes |
| ¿El saldo de cuándo? | ~~**El de hoy**, sea cual sea el periodo~~. ~~Desde el 07-10-2026, el del cierre del periodo~~. **Desde 0.4.0, el del periodo, calculado con las cuatro cifras**: comprados − redimidos + sumados − restados; sin fechas, los puntos de hoy. Lo comprado, lo redimido y los ajustes son **los del periodo** |

### 2.1 Qué es cada cifra

Los puntos son un saldo propio de cada persona, separado del dinero y **uno por moneda**: se compran en una moneda y se gastan en ella ([`requirements/mv.md`](../../../requirements/mv.md) §4.4). Cada cambio en ese saldo es uno de tres hechos, y cada hecho es una cifra:

| Cifra | Es | No es |
|---|---|---|
| **Comprados** | Los puntos que **entraron** por una compra de puntos **ya cobrada** | Una compra pendiente o rechazada: todavía no dio ningún punto |
| **Redimidos** | Los puntos que **salieron** al pagar una venta con ellos | El valor en dinero de esa venta |
| **Ajustes** | Lo que administración **sumó** o **restó** a mano, cada sentido por separado | Una compra ni un pago: es una corrección |
| **Saldo** | **Comprados − redimidos + sumados − restados del periodo** (0.4.0): lo que el periodo hizo con los puntos; sin fechas, los puntos que hay hoy | Una lectura aparte del saldo de las cuentas |

**El periodo se mira sobre cuándo se movieron los puntos**: una compra cuenta el día en que se cobró —que es cuando dio los puntos—, no el día en que se pidió. Es la diferencia con los indicadores de ventas, que miran cuándo ocurrió la venta, y es a propósito: aquí se cuentan puntos, y los puntos de una compra pendiente no existen.

### 2.2 Por qué el saldo no cuadra con el periodo, y cuándo sí

**Desde 0.4.0 cuadran siempre, por construcción**: el saldo se calcula con las cuatro cifras del periodo. Sin fechas, es toda la historia y coincide con los puntos que las personas tienen hoy; esa coincidencia es la prueba de que nada se pierde (0.3.0 lo leía al cierre de `to`; antes, siempre de hoy). Es la prueba de que nada se pierde, y es un criterio de aceptación.

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
- Por moneda, **el saldo del periodo**, con las cuatro cifras (0.4.0).
- Acotar a **una moneda** y a **una persona** del alcance.

### 4.2 No incluye

- **El dinero de las compras de puntos**: cuánto se pagó por ellos. Es otra pregunta, y otro indicador si se pide.
- **El valor en dinero de lo redimido**: depende de la tasa del día de cada pago.
- **Los puntos de la empresa**: la cuenta que lleva los puntos en circulación no es de ninguna persona.
- El saldo acumulado a una fecha: el saldo es el del periodo (0.4.0).
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
| Desde, hasta | No | Como en `RF-IN-001`: días de Bogotá, el último incluido, **sin fechas, toda la historia; una sola deja la otra abierta; sin tope** (06-10-2026, `RN-IN-010`). **Acotan también el saldo**, que es el del periodo (0.4.0) |
| Moneda | No | Solo esa moneda; una que no exista da ceros |
| Persona | No | Solo los puntos de esa persona, **si está en mi alcance**; si no —o no existe—, ceros |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo | El efectivo, como en `RF-IN-001` |
| Por moneda | La moneda; **comprados** (puntos y compras), **redimidos** (puntos y ventas), **sumados** y **restados** por ajuste (puntos y ajustes), y **el saldo del periodo**: comprados − redimidos + sumados − restados |

**Una moneda aparece si en ella hay algo que contar**: un movimiento en el periodo (0.4.0). Los puntos van con dos decimales, como se guardan, y **siempre en positivo**: «redimidos 300» y no «−300»; el sentido lo da el nombre de la cifra.

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
5. Calcula, por moneda, el saldo del periodo con las cuatro cifras.
6. Devuelve las dos cosas juntas.

---

## 9. Flujos alternativos

### FA-001 — Nadie del alcance tiene puntos

Lista vacía. No es un error.

### FA-002 — La persona pedida no está en mi alcance, o no existe

**Ceros**, sin consultar: el indicador no confirma quién cuelga de quién.

### FA-003 — Un periodo sin movimientos, con saldo

~~La moneda aparece con su saldo~~: desde 0.4.0 un periodo sin movimientos no trae la moneda.

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
| `CA-IN-041` | **Comprados** son los puntos de las compras **cobradas** en el periodo; una compra pendiente o rechazada no suma nada |
| `CA-IN-042` | **Redimidos** son los puntos gastados al pagar ventas en el periodo, en positivo |
| `CA-IN-043` | Los **ajustes** van aparte: lo sumado y lo restado, cada uno con su cuenta de ajustes |
| `CA-IN-044` | **saldo = comprados − redimidos + sumados − restados del periodo**, siempre (0.4.0); sin fechas, coincide con los puntos de hoy |
| `CA-IN-071` | **El saldo depende del periodo** (07-10-2026, 0.4.0): un periodo sin movimientos no trae la moneda, y cada tramo trae su saldo con sus cuatro cifras |
| `CA-IN-045` | Todo va **por moneda**: los puntos de una moneda no se suman con los de otra |
| `CA-IN-046` | El **alcance** es el de ventas: un funcionario ve todos; un director, los suyos y los de sus agentes, no los de otra rama; un agente, los suyos |
| `CA-IN-047` | El filtro por **persona** acota dentro del alcance; fuera o inexistente, ceros y no error. El de **moneda** acota |
| `CA-IN-048` | El periodo es sobre **cuándo se movieron los puntos**, en días de Bogotá: una compra pedida el 30 y cobrada el 1 cuenta el 1 |
| `CA-IN-049` | Sin el permiso, **prohibido**; sin autenticar, `401`; **ningún permiso de ventas lo abre** |
| `CA-IN-057` | **Sin fechas, toda la historia**, y entonces **el saldo es siempre comprados − redimidos + sumados − restados** (06-10-2026) |
| `CA-IN-058` | **Con tramo**, cada tramo trae las cuatro clases por moneda, todos los tramos presentes, y la suma de los tramos es el total del periodo; ~~el saldo solo va en el total~~ (06-10-2026); **desde 0.4.0 cada tramo trae el suyo** |

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
| 0.3.0 | 07-10-2026 | **El saldo es el del cierre del periodo**, a petición del responsable del proyecto («que el saldo también dependa del periodo»): al final del día `to` en Bogotá; sin `to`, el de hoy. `CA-IN-044` se enmienda y nace `CA-IN-071`. | Responsable del proyecto |
| 0.4.0 | 07-10-2026 | **El saldo se calcula con las cuatro cifras del periodo**, corrigiendo 0.3.0 por indicación del responsable del proyecto («calcula el balance con los datos de los puntos, no por aparte»); también por tramo. `CA-IN-044` y `CA-IN-071` se reescriben. | Responsable del proyecto |
