# SPEC — `RF-CM-009` Cerrar el periodo de comisiones, y consultar los cierres

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-009` |
| Módulo | `CM` — Comisiones |
| Versión | 0.3.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendada el | 29-09-2026 — **el cierre liquida lo afftrack** entre el barrido y el paso a pendiente (`RN-CM-043`, `RF-CM-020`) |
| Enmendada el | 30-09-2026 — **un lote abierto sin comisiones vivas no se cierra** (`RN-CM-048`) |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **cada cierto tiempo, sin que nadie lo lance**, lo que cada persona fue devengando quede **cerrado y listo para pagar**; que antes se recoja **lo que se quedó sin devengar**; y que se pueda saber **si el cierre corrió y qué hizo**.

---

## 2. Contexto

Registrado el 24-09-2026 como «liquidar las comisiones de un periodo» ([`requirements/cm.md`](../../../requirements/cm.md) v0.17.0): alguien elegía un periodo y el sistema calculaba. **El 28-09-2026 el cálculo pasó a hacerse en el momento** (v0.19.0, §5.7; `RF-CM-013`), y a este requerimiento le queda **cerrar**: tomar los lotes abiertos, que ya tienen sus comisiones, y dejarlos pendientes de pago.

**Es «correr la nómina»** y sigue siéndolo: se cierran los lotes **de todos** a la vez, no los de una persona (§5.6).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Programado, y a mano solo para relanzar** | El cierre corre solo con la frecuencia configurada; la operación a mano hace exactamente lo mismo (`RN-CM-035`) |
| **Primero el barrido** | Antes de cerrar se devenga lo que no tiene desenlace y se reintenta lo rechazado; lo que devengue entra en **este** cierre (`RN-CM-034`) |
| **Después, lo afftrack** (29-09-2026) | Tras el barrido y antes de cerrar, se liquidan los escalones afftrack de cada persona (`RF-CM-020`); lo que pagan entra en **este** cierre (`RN-CM-043`). **En la misma operación que el cierre**: si falla, no se cierra nada |
| **Un turno, un cierre** | Aunque el sistema corra en varias instancias, un turno programado se cierra una sola vez (`RN-CM-035`) |
| **El fin del periodo es el instante del cierre** | Lo que devengue un segundo después abre un lote nuevo (`RN-CM-033`) |
| **Cada cierre deja constancia** | También el que no cerró nada, y el que falló a medias |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| El reloj del sistema | Dispara el cierre programado |
| Finanzas o administración | Lanza el cierre a mano (`commission-batches:settle`) y consulta los cierres (`commission-closings:read`) |

---

## 4. Alcance

### 4.1 Incluye

- El cierre programado, con frecuencia y encendido por configuración.
- El cierre a mano.
- El barrido previo: devengar lo que no tiene desenlace y reintentar lo rechazado.
- **La liquidación afftrack** (29-09-2026), que es de `RF-CM-020` y este requerimiento invoca.
- Pasar cada lote abierto a **pendiente**, con su fin de periodo.
- La constancia de cada cierre, y su consulta.

### 4.2 No incluye

- **Calcular**: es `RF-CM-013`, que el barrido invoca.
- **Pagar**: es `RF-CM-011`.
- **Cerrar el lote de una sola persona** (§5.6 de `cm.md`).
- **Reabrir un cierre** (`RN-CM-029`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-033` | Cada lote abierto pasa a pendiente con el instante del cierre como fin |
| `RN-CM-034` | El barrido va antes, y lo que recoge entra en este cierre |
| `RN-CM-043` | La liquidación afftrack va después del barrido y antes del paso a pendiente, en la misma operación (29-09-2026) |
| `RN-CM-035` | Programado en la zona del negocio, apagable, una vez por turno; el manual es igual |
| `RN-CM-032` | El barrido reintenta solo las rechazadas; las sin comisión no |
| `RN-CM-029` | Un lote cerrado no se reabre ni se recalcula |

---

## 6. Datos

### 6.1 Entrada

**Ninguna**, ni en el programado ni en el manual: se cierra **todo** lo abierto hasta el instante del cierre.

### 6.2 Salida

**La constancia del cierre**: cuándo empezó y cuándo cerró, si fue programado o a mano —y quién, si fue a mano—, cuántos lotes cerró, cuántas líneas recogió el barrido, cuántas rechazadas reintentó y cuántas de ellas devengaron. El manual la devuelve; la consulta la lista.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | Para el manual: el actor porta `commission-batches:settle` y **no hay otro cierre en curso** |
| Postcondición | No queda ningún lote abierto **nacido antes del cierre**; cada uno de ellos está pendiente, con fin de periodo y con el cierre que lo cerró; la constancia está escrita |

---

## 8. Flujo principal

1. Se dispara el cierre —el reloj, o una persona—. Si es programado y **ese turno ya lo tomó otra instancia**, no se hace nada más (`FA-001`).
2. Queda constancia de que empezó.
3. **Barrido**: se devengan las líneas que cumplen `RN-CM-022` y no tienen desenlace; se reintentan las rechazadas.
4. **Afftrack** (29-09-2026): se fija el instante del corte y se liquidan los escalones de cada persona con los FTD activados antes de él (`RF-CM-020`).
5. **Cierre**: todo lote abierto pasa a pendiente, con el instante del cierre —**posterior al del corte**— como fin de periodo.
6. Se completa la constancia con lo que se hizo.

---

## 9. Flujos alternativos

### FA-001 — Otra instancia ya tomó el turno

La instancia que llega segunda no escribe nada y no avisa a nadie: es lo esperado con varias instancias.

### FA-002 — No hay nada abierto

Se cierra igual: constancia con cero lotes. Que el cierre corrió también es información.

### FA-003 — El barrido falla con una línea

Esa línea se queda sin desenlace (`RF-CM-013` `EX-001`) y **el cierre sigue**. El siguiente la volverá a intentar.

### FA-004 — Se devenga algo mientras se cierra

Si llega **antes** de que el cierre tome el lote, entra en él. Si llega **después**, abre un lote nuevo. **Nunca** se pierde ni entra en los dos.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Cierre a mano con otro cierre en curso | Conflicto: «Hay un cierre en curso» |
| `EX-002` | El cierre falla entre el barrido y el cierre —**también si falla la liquidación afftrack**, 29-09-2026— | Los lotes siguen abiertos —no queda ninguno a medio cerrar—, la constancia queda **sin fecha de cierre**, y el siguiente turno lo cierra todo |

---

## 11. Validaciones

**Ninguna de entrada**: el cierre no recibe datos. En la consulta, los filtros de fecha y de origen se validan todos juntos.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-170` | El cierre pasa **todos** los lotes abiertos a **pendiente**, cada uno con el instante del cierre como fin de periodo y con el cierre que lo cerró; su total no cambia |
| `CA-CM-171` | Una comisión que devenga **después** del cierre abre un lote **nuevo**, cuyo periodo empieza después del fin del cerrado |
| `CA-CM-172` | El barrido devenga una línea confirmada y con vendedor **sin desenlace**, y su comisión entra en **el lote que este cierre cierra** |
| `CA-CM-173` | El barrido reintenta una **rechazada** cuya tasa se corrigió, y devenga en este cierre; **no** reintenta una **sin comisión** |
| `CA-CM-174` | **Dos instancias** disparando el mismo turno programado producen **un solo cierre**: una constancia, y ningún lote con periodo vacío |
| `CA-CM-175` | Un cierre sin nada abierto deja constancia con **cero** lotes |
| `CA-CM-176` | El cierre a mano hace lo mismo que el programado, deja constancia de **quién** lo lanzó y la devuelve; sin `commission-batches:settle`, se rechaza |
| `CA-CM-177` | Un cierre a mano con otro en curso responde **conflicto** y no cierra nada |
| `CA-CM-178` | Una comisión que devenga **mientras** el cierre corre acaba en el lote cerrado **o** en uno nuevo, nunca en ninguno ni en los dos, y la suma de totales cuadra con la de comisiones |
| `CA-CM-179` | El cierre programado **no corre** si está apagado por configuración, y corre en la **zona del negocio** |
| `CA-CM-180` | La consulta de cierres lista los cierres del más reciente al más antiguo, con su constancia completa, filtrable por fechas y por origen; sin `commission-closings:read`, se rechaza |
| `CA-CM-300` | El cierre **no cierra** un lote abierto **sin comisiones vivas** —se devolvieron o revirtieron todas—: sigue abierto, sin fin de periodo, y no cuenta entre los lotes cerrados; lo siguiente que devengue esa persona entra en él (30-09-2026) |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un lote abierto de total cero —solo comisiones del 0 %— | Se cierra igual |
| Una persona con lotes abiertos en dos monedas | Se cierran los dos |
| El turno programado cae mientras el anterior aún corre | Es otro turno: corre. El manual sería el que responde conflicto |
| Un cierre anterior falló a medias | Su constancia queda sin fecha de cierre; este cierra lo que aquel no cerró |

---

## 14. Preguntas abiertas

**La frecuencia por defecto.** El responsable del proyecto decidió que sea **configurable**; el plan propone **mensual, a las 00:00 del día 1 en Bogotá**, porque es lo que el diseño del 24-09-2026 llamaba «lo de septiembre». Se cambia sin desplegar código.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión, con el devengo automático ([`requirements/cm.md`](../../../requirements/cm.md) v0.20.0, §5.7). **Cierra y no calcula**: barrido, lotes abiertos a pendiente, constancia. Programado y a mano. Criterios `CA-CM-170` a `CA-CM-180`. | Responsable del proyecto |

| 0.2.0 | 29-09-2026 | **El cierre liquida lo afftrack** ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8, `RN-CM-043`): un paso entre el barrido y el paso a pendiente, en la misma operación, con el instante del cierre posterior al del corte. `EX-002` lo alcanza. Los criterios de la liquidación son de `RF-CM-020`; los de este requerimiento no cambian. | Responsable del proyecto |
| 0.3.0 | 30-09-2026 | **Un lote abierto sin comisiones vivas no se cierra** (`RN-CM-048`, [`requirements/cm.md`](../../../requirements/cm.md) v0.26.0 §5.10): desde que una comisión puede salir de un lote —devuelta a su pendiente, o revertida al corregirse el vendedor—, un abierto puede quedarse vacío, y cerrarlo dejaría un pendiente que no se puede pagar. `CA-CM-300`. | Responsable del proyecto |
