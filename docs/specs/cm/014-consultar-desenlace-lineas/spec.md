# SPEC — `RF-CM-014` Consultar el desenlace de las líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-014` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración sepa **qué pasó con cada línea de venta** a efectos de comisión, y sobre todo **cuáles se rechazaron y por qué**, para corregir la tasa que sobraba.

---

## 2. Contexto

Cuando la liquidación era una operación que alguien lanzaba, **su respuesta** traía las líneas rechazadas (`RN-CM-026`). Con el devengo automático (`requirements/cm.md` v0.19.0, §5.7) una línea se rechaza **sin nadie delante**, y el rechazo tiene que quedar **consultable**. Este requerimiento es esa consulta.

**Un rechazo no es definitivo** (`RN-CM-032`): cada cierre lo reintenta, y en cuanto la tasa se corrige, la línea devenga sola. Lo que este listado permite es **saber qué corregir**.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Los tres desenlaces, filtrables** | El uso principal es ver las rechazadas, pero «¿por qué esta venta no pagó comisión?» se responde también con las sin comisión |
| **Lo que falta no sale aquí** | Una línea que aún no tiene desenlace —espera al barrido— **no aparece**: este listado dice qué **pasó**, no qué falta |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Consulta (`commission-accruals:read`) |

---

## 4. Alcance

### 4.1 Incluye

- El listado de desenlaces, filtrable por **desenlace**, **venta**, **producto** y **fechas** del último intento.

### 4.2 No incluye

- **Forzar un reintento**: lo hace el cierre (`RF-CM-009`), y el cierre a mano sirve para adelantarlo.
- Las comisiones de las líneas devengadas: `RF-CM-010`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-032` | Tres desenlaces; los intentos de una rechazada |
| `RN-CM-026` | El motivo del rechazo |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Desenlace | No | Devengada, sin comisión o rechazada |
| Venta | No | |
| Producto | No | |
| Desde, hasta | No | Sobre el último intento |
| Página | No | |

### 6.2 Salida

Por línea: la venta —identificador y comprobante—, el producto —identificador y nombre—, el vendedor, el desenlace, el motivo si fue rechazada, cuántos intentos lleva, y cuándo fue el primero y el último.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso |
| Postcondición | Ninguna: no escribe |

---

## 8. Flujo principal

1. Se piden los desenlaces, con filtros o sin ellos.
2. Se devuelven, **el último intento más reciente primero**.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

Ninguna propia más allá de las validaciones.

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Desenlace conocido; identificadores bien formados; «desde» no posterior a «hasta». Todos juntos |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-203` | Una línea **rechazada** aparece con su motivo —la suma de la cadena y el importe de la línea— y su número de intentos |
| `CA-CM-204` | Tras corregir la tasa y **cerrar**, la misma línea aparece como **devengada**, con los intentos sumados |
| `CA-CM-205` | Una línea sin tasa en toda su cadena aparece como **sin comisión**, sin motivo |
| `CA-CM-206` | Filtra por desenlace, venta, producto y fechas, combinables; los filtros inválidos se rechazan todos juntos |
| `CA-CM-207` | Una línea confirmada y con vendedor **sin desenlace todavía** no aparece |
| `CA-CM-208` | Sin `commission-accruals:read`, se rechaza; el listado **no hace una consulta por fila** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El producto está retirado | Se muestra igual |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión, con el devengo automático ([`requirements/cm.md`](../../../requirements/cm.md) v0.20.0). Lo que antes era la respuesta de liquidar, ahora consultable. Criterios `CA-CM-203` a `CA-CM-208`. | Responsable del proyecto |
