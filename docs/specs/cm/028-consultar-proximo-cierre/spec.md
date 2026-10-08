# SPEC — `RF-CM-028` Consultar el próximo cierre y cómo se pagará

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-028` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 08-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración sepa **cuándo es el próximo cierre**, **si ya puede elegir cómo se pagará** y **qué hay elegido**, para que el frontend muestre el botón de `RF-CM-029` solo cuando sirve.

---

## 2. Contexto

Desde el 08-10-2026 **el cierre programado paga en el mismo momento** lo que cierra, salvo que alguien elija pagarlo a mano en las **48 horas anteriores** (`requirements/cm.md` v0.43.0 §5.12, `RN-CM-053`, `RN-CM-054`). El responsable del proyecto: «2 días antes aparecerá un botón para indicar si el pago se realizará manual o automático». **Este requerimiento es lo que dice si el botón aparece**; elegir es `RF-CM-029`.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió | Lo que se descartó |
|---|---|---|
| **Qué cierre** | **El próximo programado**, según la frecuencia configurada | *Una lista de cierres futuros* — solo se elige el próximo |
| **Si nadie eligió** | Dice **automático**, y que **nadie eligió** | *Decir «sin elegir» sin modo* — el frontend tendría que saber cuál es el valor por defecto |
| **Quién decide si el botón aparece** | **El frontend**, con lo que esta consulta responde | *Que el backend no responda fuera de la ventana* — el botón no podría anunciar cuándo aparecerá |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Consulta (`commission-closings:read-next`) |

---

## 4. Alcance

### 4.1 Incluye

- Cuándo es el próximo cierre programado y cuándo se abre la ventana para elegir.
- Si la ventana está abierta ahora.
- Cómo se pagará ese cierre, si alguien lo eligió, quién y cuándo.

### 4.2 No incluye

- **Elegir**: `RF-CM-029`.
- **Los cierres ya hechos** y cómo se pagaron: `RF-CM-009`.
- **Cuánto se va a pagar**: los lotes abiertos se consultan con `RF-CM-010`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-054` | La ventana, el valor por defecto y que la elección vale para un solo cierre |
| `RN-CM-035` | Cuándo es el cierre, en qué zona, y que se puede apagar |

---

## 6. Datos

### 6.1 Entrada

Ninguna.

### 6.2 Salida

**El instante del próximo cierre**, **el instante en que se abre la ventana**, **si está abierta ahora**, **el modo de pago** —automático o manual—, **si alguien lo eligió** y, si lo eligió, **quién y cuándo**.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El cierre programado está encendido |
| Postcondición | Nada cambia |

---

## 8. Flujo principal

1. Administración pide el próximo cierre.
2. Se calcula cuándo es, según la frecuencia configurada y la zona del negocio.
3. Se busca la elección hecha para ese cierre.
4. Se responde.

---

## 9. Flujos alternativos

### FA-001 — Nadie eligió

El modo es **automático** y la respuesta dice que **nadie eligió**, sin persona ni fecha.

### FA-002 — El cierre está en marcha

Si el turno que acaba de dispararse todavía corre, **el próximo es el siguiente**: la elección de ese ya no se puede cambiar.

---

## 10. Excepciones

### EX-001 — El cierre programado está apagado

Sin cierre programado no hay próximo cierre: **conflicto**, y lo dice.

---

## 11. Validaciones

Ninguna: no hay entrada.

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-381` | **Fuera de la ventana**, responde el instante del próximo cierre, el de apertura de la ventana —**48 horas antes**— y **ventana cerrada**; sin elección, el modo es **automático** y **nadie eligió** |
| `CA-CM-382` | **Dentro de la ventana**, responde **ventana abierta**; si alguien eligió, el modo elegido **con quién y cuándo** |
| `CA-CM-383` | Con el cierre mensual, el próximo es **las 00:00 del día 1 en Bogotá** y la ventana se abre **a las 00:00 del penúltimo día del mes** |
| `CA-CM-384` | Con el cierre programado **apagado**, responde **conflicto** |
| `CA-CM-385` | Sin `commission-closings:read-next`, se rechaza —**también con `commission-closings:read`**— |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una elección de un cierre que ya pasó | No se muestra: es de otro turno |
| La frecuencia cambia mientras hay una elección hecha | Si el próximo cierre cambia de instante, la elección hecha no le aplica, y el modo vuelve a ser automático hasta que se elija otra vez |
| Un cierre más seguido que cada 48 horas | La ventana se abre al terminar el anterior: siempre está abierta |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 08-10-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.43.0 §5.12, `RN-CM-054`), por decisión del responsable del proyecto: el botón aparece 48 horas antes del cierre. Criterios `CA-CM-381` a `CA-CM-385`. | Responsable del proyecto |
