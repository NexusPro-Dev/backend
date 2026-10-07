# SPEC — `RF-IN-007` Consultar el resumen de lotes de comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-007` |
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

Saber, **hoy**, cuántos lotes de comisiones hay en cada estado —abiertos, pendientes de pago y pagados— y **por cuánto** en cada moneda: la vista de administración de lo que se está acumulando, de lo que falta por pagar y de lo que ya se pagó.

---

## 2. Contexto

**Lo pidió el responsable del proyecto el 07-10-2026**: «el de lotes de comisiones: por estado, valor total, total de lotes». Es el primero de la tanda de **comisiones** que `requirements/in.md` §1.3 declaraba sin escribir. Dos decisiones suyas del mismo día lo definen:

| Pregunta | Decisión |
|---|---|
| ¿Quién lo ve, y qué ve? | **Administración, sin alcance**, como el resumen de líneas de venta (`RN-IN-011`): quien porta el permiso ve todos los lotes de todas las personas |
| ¿Sobre qué periodo? | **Ninguno: es una foto de hoy.** No acepta fechas ni tramos (`RN-IN-012`) |

### 2.1 Por qué es una foto y no un periodo

Un lote es **una persona, una moneda y un periodo de comisiones** que pasa por tres estados: **abierto** mientras crece, **pendiente** desde que el cierre lo congela, y **pagado** cuando se abona (`RN-CM-030`, `RN-CM-033`). La pregunta que este indicador responde es **dónde está hoy el dinero de las comisiones**: cuánto se sigue acumulando, cuánto espera a que Finanzas lo pague y cuánto ya salió. **Esa pregunta no tiene periodo**: un lote pendiente desde hace tres meses es tan pendiente como el de ayer, y filtrarlo por fechas lo escondería justo cuando más importa verlo.

Las otras dos formas que se estudiaron, y por qué no:

| Forma | Por qué no |
|---|---|
| Filtrar por **el inicio del periodo del lote** | Responde «qué lotes se abrieron en el mes», que no es la pregunta, y mezcla en el mismo filtro lotes que hoy están en estados distintos por razones que el filtro no muestra |
| Filtrar **cada estado por su propia fecha** —abierto por inicio, pendiente por cierre, pagado por pago— | Tres fechas distintas detrás de un solo `from`, y la suma de los estados dejaría de significar nada |

Si un día se quiere **«cuánto se pagó en el mes»**, es una pregunta de flujo y no de foto, y será un requerimiento propio que diga sobre qué fecha se cuenta.

### 2.2 Por qué no se acota por alcance

Como en `RF-IN-006` · `spec.md` §2.1: es una vista **de administración**, la de quien decide cuándo y cuánto se paga. Un vendedor ya tiene sus lotes en su propia consulta (`RF-CM-012`). Por eso **el permiso es la única puerta**: se siembra solo para administración, y si administración se lo da a otro rol, ese rol ve las cifras enteras (`RN-IN-011`).

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| **Administración**, con el permiso | Ve los lotes de **todas** las personas |
| Cualquier rol al que administración le dé el permiso | Igual: **las cifras enteras** (`RN-IN-011`) |

---

## 4. Alcance

### 4.1 Incluye

- **Por estado** —abiertos, pendientes, pagados—: **cuántos lotes** y **su valor** por moneda.
- **El total**: cuántos lotes hay sean cuales sean sus estados, y su valor por moneda.
- **Filtrar por una moneda.**

### 4.2 No incluye

- **Un periodo o tramos** (§2.1, `RN-IN-012`).
- **Por persona** o **por rol**: el detalle de cada lote ya lo da la consulta de lotes de `CM` (`RF-CM-010`).
- **Cuántas comisiones** lleva cada lote, ni de qué clase —por venta o afftrack—.
- **Acotar a un vendedor o a una red** (§2.2).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| **`RN-IN-001`** | Permiso propio; ningún otro lo abre, tampoco los de lotes de `CM` |
| **`RN-IN-011`** | Sin alcance: quien porta el permiso ve todos los lotes |
| **`RN-IN-012`** | Sin periodo: cuenta los lotes como están hoy (nace aquí) |
| `RN-IN-004` | Un valor por moneda; nunca se suman entre monedas |
| `RN-IN-006` | Cuenta sobre los lotes vivos, sin guardar nada |
| `RN-CM-028` | Un lote es de una sola moneda: su valor entero va a esa moneda |
| `RN-CM-030`, `RN-CM-033` | Los tres estados, y que el valor de un lote abierto crece con cada devengo |
| `RN-CM-046`, `RN-CM-047` | El valor de un lote es el que tiene hoy: lo retirado o revertido ya no está en él |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Moneda | No | Solo los lotes de esa moneda; una inexistente da ceros |

**Ni fechas, ni tramo, ni persona** (§2.1, §2.2).

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Abiertos, pendientes, pagados | Cada uno: cuántos lotes y su valor por moneda |
| Total | Cuántos lotes en total y su valor por moneda |

**El valor de un lote es el total que el lote tiene hoy**: lo devengado en él, menos lo que se le retiró o se le revirtió. Para un lote pagado es lo que se abonó. **Los tres estados vienen siempre**, aunque estén en cero, para que el tablero no tenga que adivinar qué falta.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor está autenticado y tiene el permiso del resumen de lotes de comisiones |
| Postcondición | **Ninguna.** |

---

## 8. Flujo principal

1. El actor pide el resumen de lotes, con o sin moneda.
2. El sistema comprueba el permiso.
3. Cuenta los lotes por estado y moneda, y suma su valor.
4. Devuelve el resumen.

---

## 9. Flujos alternativos

### FA-001 — No hay lotes

Los tres estados y el total en cero, y los valores vacíos.

### FA-002 — Nada pendiente de pago

Pendientes en cero: es la situación tras pagar la nómina.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Quien pregunta no tiene el permiso | Prohibido |

---

## 11. Validaciones

| ID | Regla | Código |
|---|---|---|
| `VAL-001` | La moneda, si llega, es un identificador bien formado | El de una consulta mal formada |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-IN-072` | **Por estado**: abiertos, pendientes y pagados, cada uno con **cuántos lotes** y **su valor por moneda**, el total del lote |
| `CA-IN-073` | **Los tres estados vienen siempre**, en cero si no hay lotes en ellos |
| `CA-IN-074` | **El total** es la suma de los tres estados, en lotes y en valor de cada moneda; **dos monedas no se suman** |
| `CA-IN-075` | **Por moneda**: con una moneda, solo sus lotes; una moneda inexistente da ceros |
| `CA-IN-076` | **Es una foto de hoy**: un lote abierto que cierra el periodo pasa de abiertos a pendientes, y uno que se paga, de pendientes a pagados, en la siguiente consulta; **las fechas que se envíen no cambian la respuesta** |
| `CA-IN-077` | **El valor es el de hoy**: una comisión que se devenga suma al abierto; una retirada de un pendiente al abierto resta de pendientes y suma a abiertos |
| `CA-IN-078` | **Sin alcance**: un vendedor con el permiso ve las mismas cifras que administración |
| `CA-IN-079` | Sin el permiso, **prohibido**, también con los permisos de lotes de `CM` o los de otros indicadores; sin token, `401`; el permiso se siembra **solo** a `SUPERADMIN` y `ADMIN` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Un lote al que se retiraron todas sus comisiones | Sigue siendo un lote, en su estado, con valor cero (`RN-CM-048`) |
| Una persona que vende en dos monedas | Dos lotes, uno en cada moneda |
| Un lote abierto mientras se consulta | Su valor es el del instante de la consulta: un devengo un segundo después ya no está |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 07-10-2026 | Primera versión, a petición del responsable del proyecto: por estado, cuántos lotes y su valor; **de administración y sin alcance** (`RN-IN-011`) y **sin periodo**, una foto de hoy (`RN-IN-012`, nace aquí). Ocho criterios, `CA-IN-072` a `CA-IN-079`. | Responsable técnico |
