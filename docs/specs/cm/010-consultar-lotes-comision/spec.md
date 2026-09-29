# SPEC — `RF-CM-010` Consultar los lotes de comisión, y el detalle de uno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-010` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendada el | 29-09-2026 — **cada comisión dice de qué clase es**, `POR_VENTA` o `POR_AFFTRACK` (`RN-CM-044`) |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración y Finanzas vean **qué se le debe a quién**: todos los lotes, con su estado, y dentro de uno **cada comisión, línea a línea y nivel a nivel**, con lo que se aplicó.

---

## 2. Contexto

Los lotes nacen con la primera comisión de una persona en una moneda (`RF-CM-013`), crecen mientras están abiertos, se cierran (`RF-CM-009`) y se pagan (`RF-CM-011`). **Este requerimiento es la lectura de todo eso para quien administra**; la de cada vendedor sobre lo suyo es `RF-CM-012`.

**Alcance global** (`requirements/cm.md` §5.3): quien porta el permiso ve todos los lotes, porque ver «los de mi red» depende de **D-22** y no entra.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Los tres estados, también el abierto** | El lote abierto se lista con su total **al día**; es lo que Finanzas mira para prever el cierre |
| **El detalle explica cada número** | Cada comisión dice de qué venta y producto sale, qué nivel de la cadena cobra, de qué tasa salió y con qué fecha se resolvió |
| **Lectura, sin efectos** | Consultar no cambia nada |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración o Finanzas | Lista los lotes (`commission-batches:read`) y abre uno (`commission-batches:read-detail`) |

---

## 4. Alcance

### 4.1 Incluye

- El listado de lotes, filtrable por **estado**, **persona**, **moneda** y **fechas** del periodo.
- El detalle de un lote con **todas** sus comisiones.

### 4.2 No incluye

- Los lotes propios: `RF-CM-012`.
- Pagar: `RF-CM-011`.
- Los desenlaces de las líneas que no pagaron: `RF-CM-014`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-033` | El abierto se ve, sin fin de periodo |
| `RN-CM-008` | El detalle muestra lo **copiado**, no lo que dice hoy la tasa |
| `RN-CM-024`, `RN-CM-025` | La fecha con que se resolvió, y el nivel de la cadena |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Estado | No | Abierto, pendiente o pagado |
| Persona | No | De quién es el lote |
| Moneda | No | |
| Desde, hasta | No | Sobre el inicio del periodo |
| Página | No | La paginación de siempre |

### 6.2 Salida

**Listado**: por lote, su código, la persona —identificador y nombre—, la moneda, el periodo, el estado, el total, cuántas comisiones tiene, y si está pagado, cuándo y con qué movimiento.

**Detalle**: lo mismo, y cada comisión con: la venta —identificador y comprobante—, el producto —identificador y nombre—, la persona que cobra ese nivel y el nivel, la fuente y la tasa exacta, la forma y el valor, el precio unitario y la cantidad, lo devengado, la fecha con que se resolvió y el instante del devengo.

**Desde el 29-09-2026 cada comisión dice su clase** (`RN-CM-044`): **`POR_VENTA`**, con todo lo anterior; o **`POR_AFFTRACK`**, que **no tiene venta, línea ni nivel** y dice en su lugar el producto FTD, **cuántos FTD pagó** —el límite del escalón—, el valor por FTD, la fuente y el escalón exacto, y la liquidación de la que sale (`RF-CM-021`). **El listado cuenta las dos clases** en «cuántas comisiones tiene».

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso de la operación |
| Postcondición | Ninguna: no escribe |

---

## 8. Flujo principal

1. Se piden los lotes, con filtros o sin ellos.
2. Se devuelven, **el más reciente primero**.
3. Se abre uno y se devuelve con sus comisiones, ordenadas por venta, línea y nivel.

---

## 9. Flujos alternativos

**Ninguno**: una lista vacía es una respuesta válida.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El lote no existe | No encontrado |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Estado conocido; identificadores bien formados; «desde» no posterior a «hasta». **Todos los errores juntos** |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-181` | El listado devuelve los lotes de **todas** las personas, del periodo más reciente al más antiguo, con su total y su número de comisiones |
| `CA-CM-182` | Filtra por estado, persona, moneda y fechas, combinables |
| `CA-CM-183` | Un lote **abierto** aparece sin fin de periodo y con su total **al día**: tras un devengo nuevo, el total mostrado sube |
| `CA-CM-184` | El detalle trae **cada comisión** con su venta, producto, persona, nivel, fuente, tasa, forma, valor, base, importe, fecha de resolución e instante del devengo |
| `CA-CM-185` | El detalle muestra **lo copiado**: corregir la tasa después **no cambia** lo que se muestra |
| `CA-CM-186` | Un lote que no existe responde **no encontrado** |
| `CA-CM-187` | Los filtros inválidos se rechazan **todos juntos** |
| `CA-CM-188` | Sin el permiso de cada operación, se rechaza; las dos lecturas **no hacen una consulta por fila** |
| `CA-CM-262` | El detalle de un lote con comisiones de las dos clases dice la de cada una: las `POR_VENTA` con su venta, línea y nivel; las `POR_AFFTRACK` **sin** ellos, con el producto, los FTD pagados, el valor y su liquidación; el total del lote las suma todas (29-09-2026) |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| La persona del lote está eliminada | Se muestra igual, con su nombre |
| El producto está retirado | Se muestra igual |
| Un lote con cientos de comisiones | El detalle las devuelve todas; un lote es de una persona y un periodo, y su tamaño está acotado por lo que esa persona vende |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 28-09-2026 | Primera versión ([`requirements/cm.md`](../../../requirements/cm.md) v0.20.0). Listado y detalle, con el lote abierto al día. Criterios `CA-CM-181` a `CA-CM-188`. | Responsable del proyecto |

| 0.2.0 | 29-09-2026 | **Cada comisión dice su clase** (`RN-CM-044`, [`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8): `POR_VENTA` o `POR_AFFTRACK`, y la segunda sin venta, línea ni nivel. `CA-CM-262`. | Responsable del proyecto |
