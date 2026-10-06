# SPEC — `RF-IN-004` Consultar las ventas por vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-004` |
| Módulo | `IN` — Indicadores |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien tenga el permiso sepa **quién vende cuánto dentro de su alcance**: un director, cuánto vendió cada uno de sus agentes y él; un manager, cada persona de su red; administración, cada vendedor de la plataforma y cuánto queda sin asignar.

---

## 2. Contexto

**Es lo confirmado de `RF-IN-001` agrupado por el vendedor de cada línea**, y hereda de él qué se cuenta, el periodo, el alcance y los ceros fuera de él ([`RF-IN-001` · `spec.md`](../001-resumen-de-ventas/spec.md) §2, §6.1), y de `RF-IN-003` cómo se ordena y se corta ([`RF-IN-003` · `spec.md`](../003-ventas-por-producto/spec.md) §2.2).

### 2.1 Cada persona con lo que vendió ella, no con lo de su rama

**La fila de un director dice lo que vendió el director**, no lo que vendió él más sus agentes. Si dijera lo segundo, la suma de las filas contaría dos veces cada venta de un agente —en su fila y en la de su director— y el ranking no respondería «quién vende más», sino «quién tiene la rama más grande». **El acumulado por rama es otra pregunta**: `RF-SP-058` la responde para el FTD con su árbol, y si se quiere para las ventas será un requerimiento propio.

### 2.2 Lo que está sin asignar, aparte

Una venta por validar tiene líneas **sin vendedor** (`RN-MV-034`), y solo administración las ve (`RN-IN-003`). **No son una fila del ranking**, porque no son de nadie: van **aparte**, como «sin asignar», para que la suma de las filas más lo sin asignar cuadre con el resumen, y para que administración vea cuánto le queda por atribuir.

### 2.3 Solo quien vendió algo

**Aparecen las personas con ventas confirmadas en el periodo.** Un agente que no vendió nada no tiene fila. Es una decisión con precio —un director no ve en este indicador quién de los suyos no vende— y se toma porque, para administración, «todos los vendedores» es una lista que este indicador no tiene por qué recorrer entera; queda como pregunta abierta (§14).

---

## 3. Actores

Los de [`RF-IN-001`](../001-resumen-de-ventas/spec.md) §3, con el permiso de este indicador.

---

## 4. Alcance

### 4.1 Incluye

- Por vendedor de mi alcance con ventas confirmadas en el periodo: su **identidad**, **ventas**, **líneas**, **unidades** e **importe por moneda**.
- **Lo sin asignar**, aparte, solo para administración y solo sin filtro de vendedor.
- Un **límite** de filas y el total de vendedores, como en `RF-IN-003`.
- Acotar a una moneda y a un vendedor de mi alcance.

### 4.2 No incluye

- El acumulado por rama (§2.1).
- Los vendedores sin ventas (§2.3).
- El rol de cada vendedor: cambia con el tiempo, y el de hoy no es el que tenía cuando vendió.
- Comisiones.

---

## 5. Reglas de negocio aplicables

Las de [`RF-IN-001`](../001-resumen-de-ventas/spec.md) §5, y además:

| Regla | Cómo aplica |
|---|---|
| `RN-MV-034` | Las líneas sin vendedor existen, y van aparte (§2.2) |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desde, hasta, moneda, vendedor | No | Como en `RF-IN-001` §6.1. Con vendedor, la respuesta es su fila sola |
| Límite | No | Por defecto **20**; como mucho **100** |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo, orden | Como en `RF-IN-003` |
| Vendedores | Cada uno con su identidad —identificador, nombre de usuario y nombre— y sus cifras, en orden |
| Total de vendedores | Cuántos vendedores tuvieron ventas, aunque no quepan |
| Sin asignar | Ventas, líneas, unidades e importe por moneda de las líneas sin vendedor. **Solo** para administración sin filtro de vendedor; para los demás, ausente |

**Un vendedor retirado sigue apareciendo** con lo que vendió y su nombre: lo vendido no se borra porque la persona se vaya.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso de las ventas por vendedor |
| Postcondición | **Ninguna.** |

---

## 8. Flujo principal

1. El actor pide las ventas por vendedor, con los filtros y el límite que quiera.
2. El sistema comprueba el permiso, fija y valida el periodo y el límite.
3. Resuelve el alcance, como en `RF-IN-001`.
4. Agrupa las líneas confirmadas del alcance por su vendedor, ordena como `RF-IN-003` y corta.
5. Si el alcance es todo y no hay filtro de vendedor, suma aparte las líneas sin vendedor.
6. Devuelve las filas con la identidad de cada vendedor, el total y lo sin asignar.

---

## 9. Flujos alternativos

### FA-001 — No hay ventas en el periodo

Lista vacía, total cero; para administración, lo sin asignar en ceros.

### FA-002 — El vendedor pedido no está en mi alcance, o no existe

Lista vacía y total cero, **sin consultar**.

### FA-003 — Alguien de mi red dejó de estarlo

Deja de aparecer, como en `RF-IN-001` · `FA-006`.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso de las ventas por vendedor | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` a `VAL-004` | Las de `RF-IN-001` §11 |
| `VAL-005` | El límite, si viene, está entre 1 y 100 |

Devueltas juntas.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-030` | La suma de las filas —sin límite— **más lo sin asignar** coincide con lo **confirmado** del resumen de `RF-IN-001` para el mismo actor y filtros **en líneas, unidades e importe por moneda**; las ventas no, por §13 |
| `CA-IN-031` | La fila de un director dice **lo que vendió él**, no lo de sus agentes: ninguna venta se cuenta en dos filas |
| `CA-IN-032` | Un **director** ve su fila y las de **sus** agentes con ventas; un **manager**, las de toda su red; un **agente**, la suya; nadie ve la de otra rama |
| `CA-IN-033` | **Administración** ve todos los vendedores con ventas y **lo sin asignar** aparte; un vendedor no recibe lo sin asignar, ni administración cuando filtra por vendedor |
| `CA-IN-034` | Quien no vendió nada en el periodo **no tiene fila**; un vendedor **retirado** con ventas sí, con su nombre |
| `CA-IN-035` | El orden es el de `RF-IN-003` —por importe con moneda, por unidades sin ella—, y el límite y el total también; por defecto 20 |
| `CA-IN-036` | Un vendedor fuera de mi red, o inexistente, da lista vacía y no un error; quien dejó de colgar de mí deja de aparecer |
| `CA-IN-037` | Un límite fuera de 1..100, un rango invertido y más de 366 días son un error, juntos; sin el permiso, **prohibido**, y otro `indicators:` no lo abre |

**`CA-IN-031` es el que distingue este indicador de `RF-SP-058`**, y `CA-IN-030` el que prueba que no se pierde ni se duplica nada.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta con líneas de dos vendedores de mi red | Cuenta una venta en **cada** fila, con las líneas de cada uno; por eso las **ventas** de las filas pueden sumar más que las del resumen, y las líneas, unidades e importes no |
| Una autoventa | En la fila de quien se vendió a sí mismo |
| Un vendedor con ventas en dos monedas | Una fila, dos importes |

**El primer caso es la única cifra que no cuadra con el resumen, y es correcto**: en el resumen la venta es una; aquí es una venta **para cada vendedor que participó**. `CA-IN-030` compara por eso líneas, unidades e importes, y no ventas.

---

## 14. Preguntas abiertas

**Los vendedores sin ventas.** Para un director, saber qué agentes no vendieron nada es tan útil como el ranking. Incluirlos con ceros exige recorrer **toda** su red —para él es barato, para administración son todos los vendedores de la plataforma—. Si se pide, la salida natural es incluirlos solo con alcance de red, y lo decide el responsable del proyecto.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. Hereda de `RF-IN-001` qué se cuenta y de `RF-IN-003` cómo se ordena, y decide: **cada persona con lo que vendió ella**, no con lo de su rama (§2.1, que lo separa de `RF-SP-058`); **lo sin asignar aparte** y solo para administración (§2.2); **solo quien vendió algo** (§2.3, abierta en §14). Límite 20, tope 100. Ocho criterios, `CA-IN-030` a `CA-IN-037`. | Responsable técnico |
