# SPEC — `RF-IN-008` Consultar el resumen de mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-008` |
| Módulo | `IN` — Indicadores |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 07-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que cada persona sepa, de **sus propias** comisiones, **cuántas tiene y cuánto suman** en total y según el estado del lote en que están hoy: **abierto** —lo que sigue acumulando—, **pendiente** —lo cerrado que espera el pago— y **pagado**.

---

## 2. Contexto

**Lo pidió el responsable del proyecto el 07-10-2026**: «un indicador nuevo para las comisiones personales, pero los detalles: ver el total de comisiones, cuántas están en el lote abierto, pendiente y pagados». Es el segundo de la tanda de comisiones, y la cara personal de `RF-IN-007`: aquel cuenta **lotes de todas las personas** para administración; este cuenta **comisiones de quien pregunta**. Tres decisiones suyas del mismo día:

| Pregunta | Decisión |
|---|---|
| ¿De quién son las cifras? | **Solo las mías**: la persona la pone la sesión, y no hay filtro de persona (`RN-IN-013`, nace aquí) |
| ¿Sobre qué periodo? | **El estado de hoy, con fechas opcionales**, como `RF-IN-007` desde su enmienda: sin fechas, todas mis comisiones; con fechas, las que **nacieron** esos días (`RN-IN-012`) |
| ¿Qué se cuenta? | **Cuántas y cuánto**: el número de comisiones y su valor por moneda |

### 2.1 Por qué solo lo mío, y no mi red

Los demás indicadores de un vendedor cuentan **su red** (`RN-IN-002`): un director ve lo vendido por sus agentes. **Una comisión no se reparte así**: cada nivel de la cadena tiene **la suya**, a su nombre, en su propio lote (`RN-CM-011`). Lo que un director cobra por la venta de su agente ya es **una comisión suya**, y está en sus cifras. Sumarle además las de sus agentes le mostraría dinero que no es suyo. Por eso este indicador es **de lo propio** (`RN-SEG-015`), como la consulta de mis lotes (`RF-CM-012`) y la de todas mis comisiones (`RF-CM-026`).

### 2.2 Por qué las fechas son las del nacimiento de la comisión

Una comisión nace cuando se devenga —al confirmarse la venta, o al reatribuirse la línea (`RN-CM-047`)— y no cambia de fecha al moverse de lote. **Es la misma fecha que filtra la lista de todas mis comisiones** (`RF-CM-026`), y así las cifras de este indicador **cuadran con esa lista** con los mismos filtros. El estado, en cambio, es el del lote **hoy** (`RN-IN-012`): una comisión de septiembre cuyo lote se pagó ayer cuenta como pagada.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Cualquier persona con el permiso | Ve **sus propias** comisiones, nunca las de otro |

---

## 4. Alcance

### 4.1 Incluye

- **Por estado del lote** —abierto, pendiente, pagado—: **cuántas comisiones** y **su valor** por moneda.
- **El total**: cuántas comisiones tengo, sean cuales sean sus estados, y su valor por moneda.
- **Filtrar por una moneda** y **por un rango de fechas** sobre el nacimiento de la comisión.

### 4.2 No incluye

- **Las comisiones de otra persona**, ni de mi red (§2.1).
- **Tramos** (`RN-IN-012`).
- **El detalle de cada comisión**: lo da la lista de todas mis comisiones (`RF-CM-026`).
- **Por clase** —por venta o afftrack—, **por producto** o **por nivel** de la cadena.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-IN-001`** | Permiso propio; ningún otro lo abre, tampoco los de lotes o comisiones de `CM` |
| **`RN-IN-013`** | Solo lo propio, sin alcance de red (nace aquí) |
| **`RN-IN-012`** | El estado es el del lote hoy; las fechas eligen qué comisiones se cuentan |
| `RN-IN-010` | Los días del rango: sin «desde», desde el principio; sin «hasta», hoy |
| `RN-IN-004` | Un valor por moneda; nunca se suman entre monedas |
| `RN-IN-006` | Cuenta sobre las comisiones vivas, sin guardar nada |
| `RN-IN-007` | Los días son los de Bogotá |
| `RN-CM-011` | Una comisión por nivel de la cadena, cada una a nombre de su persona |
| `RN-CM-046`, `RN-CM-047` | Una comisión retirada cuenta en el lote donde está hoy; la cadena vieja de una línea reatribuida ya no existe |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Moneda | No | Solo las comisiones de esa moneda; una inexistente da ceros |
| Desde, hasta | No | Días, en la zona del negocio. Solo las comisiones **nacidas** esos días, los dos incluidos |

**Ni persona, ni tramo** (§2.1, `RN-IN-012`). La persona es quien pregunta.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Periodo | Los días efectivos y la zona, como en los demás indicadores |
| Abiertas, pendientes, pagadas | Cada una: cuántas comisiones y su valor por moneda, según el estado de su lote hoy |
| Total | Cuántas comisiones en total y su valor por moneda |

**El valor de una comisión es su importe**, el que nació con ella (`RN-CM-029`). **Los tres estados vienen siempre**, aunque estén en cero.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso del resumen de mis comisiones |
| Postcondición | **Ninguna.** |

---

## 8. Flujo principal

1. El actor pide el resumen de sus comisiones, con o sin moneda y fechas.
2. El sistema comprueba el permiso.
3. Cuenta las comisiones **del actor** por el estado de su lote y su moneda, y suma su importe.
4. Devuelve el resumen.

---

## 9. Flujos alternativos

### FA-001 — No tengo comisiones

Los tres estados y el total en cero, y los valores vacíos.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso | Prohibido |

---

## 11. Validaciones

| ID | Regla | Código |
|---|---|---|
| `VAL-001` | La moneda, si llega, es un identificador bien formado; las fechas, días válidos | El de una consulta mal formada |
| `VAL-002` | «Desde» no es posterior a «hasta» | `VAL-002` |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-090` | **Por estado del lote**: abiertas, pendientes y pagadas, cada una con **cuántas comisiones** y **su valor por moneda**; el total es la suma de los tres, sin mezclar monedas |
| `CA-IN-091` | **Solo lo mío**: las comisiones de otra persona —también las de mi red, aunque sean de la misma venta— no cuentan; un `sellerId` enviado se ignora |
| `CA-IN-092` | **Los tres estados vienen siempre**, en cero si no tengo comisiones en ellos |
| `CA-IN-093` | **El estado es el de hoy**: si mi lote se cierra, sus comisiones pasan de abiertas a pendientes; si se paga, a pagadas; una comisión retirada a mi lote abierto cuenta como abierta |
| `CA-IN-094` | **Por moneda**: solo las de esa moneda; una inexistente da ceros; una mal formada, `400` |
| `CA-IN-095` | **Por fechas**: solo las comisiones nacidas esos días, en su estado de hoy; sin fechas, todas, y el periodo va desde el principio hasta hoy; «desde» posterior a «hasta» es `400` con `VAL-002` |
| `CA-IN-096` | Sin el permiso, **prohibido**, también con los de lotes o comisiones de `CM` o los de otros indicadores; sin token, `401`; el permiso lo porta **todo rol que ve sus lotes** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una comisión de importe cero | Cuenta como una, con valor cero |
| Una comisión retirada de un lote pendiente al abierto | Cuenta en abiertas, y conserva su fecha de nacimiento |
| Una línea cuyo vendedor se corrige | La comisión vieja desaparece y la nueva nace ese día (`RN-CM-047`) |
| Cobro en dos monedas | Un valor por moneda en cada estado |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 07-10-2026 | Primera versión, a petición del responsable del proyecto: de mis comisiones, cuántas y cuánto por estado del lote y en total; **solo lo propio** (`RN-IN-013`, nace aquí), el estado de hoy con fechas opcionales sobre el nacimiento de la comisión. Siete criterios, `CA-IN-090` a `CA-IN-096`. | Responsable técnico |
