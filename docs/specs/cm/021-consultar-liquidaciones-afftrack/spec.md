# SPEC — `RF-CM-021` Consultar las liquidaciones afftrack

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-021` |
| Módulo | `CM` — Comisiones |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración sepa **qué hizo cada cierre con los FTD de cada persona**: cuántos traía, cuántos contó, cuántos pagó, con qué escalón y **cuántos le quedan para el siguiente**.

---

## 2. Contexto

La liquidación afftrack corre dentro del cierre, sin nadie delante (`RF-CM-020`), y lo que deja **no cabe en un lote**: el lote dice lo que se pagó, pero no los FTD que no alcanzaron ningún escalón, ni el remanente. «¿Por qué no cobré mis 45 FTD?» se responde aquí. Existe por lo mismo que `RF-CM-014` existe para el devengo.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El remanente vigente es el de la última** | Filtrando por persona y producto, la primera fila —la más reciente— dice cuántos FTD lleva guardados |
| **De dónde salen los nuevos** | Cada liquidación dice cuántos de sus FTD nuevos son de la persona y cuántos de su red |
| **Lo propio no entra** | Un vendedor ve lo que cobró en sus lotes (`RF-CM-012`); ver su remanente es un requerimiento más el día que se pida (`cm.md` §6) |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Consulta (`afftrack-settlements:read`) |

---

## 4. Alcance

### 4.1 Incluye

- El listado de liquidaciones, filtrable por **persona**, **producto**, **cierre** y **fechas** del cierre, y por **si pagó o no**.

### 4.2 No incluye

- **La lista de FTD de cada liquidación**, línea a línea. Queda guardada (`cm.md` §7.12) y es una lectura más el día que se pida.
- Las comisiones pagadas: `RF-CM-010`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-041` | Remanente de entrada, nuevos, pagados y remanente de salida |
| `RN-CM-039` | La escala de la que salió el escalón |
| `RN-CM-042` | Los nuevos, propios y de la red |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Persona | No | |
| Producto | No | |
| Cierre | No | |
| Desde, hasta | No | Sobre el instante del cierre |
| Pagó | No | Sí: solo las que alcanzaron un escalón; no: solo las que no |
| Página | No | |

### 6.2 Salida

Por liquidación: el cierre —identificador e instante—; la persona —identificador, nombre de usuario y nombre—; el producto —identificador, código, nombre y moneda—; **remanente de entrada**; **FTD nuevos**, y de ellos **propios** y **de su red**; **FTD pagados**; **remanente de salida**; y, si pagó, **el escalón** —su límite, su valor y su fuente, personal o de rol— y **el importe** pagado.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso |
| Postcondición | Ninguna: no escribe |

---

## 8. Flujo principal

1. Se piden las liquidaciones, con filtros o sin ellos.
2. Se devuelven **del cierre más reciente al más antiguo**, y dentro de un cierre por persona y producto.

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
| `VAL-001` | Identificadores bien formados; «desde» no posterior a «hasta»; página dentro de lo admitido. Todos juntos |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-255` | Tras un cierre con 55 FTD y escalones de 50 y 60, la liquidación dice **0 de entrada, 55 nuevos, 50 pagados, 5 de salida**, el escalón de 50 con su fuente y el importe |
| `CA-CM-256` | Una liquidación que no alcanzó ningún escalón aparece **sin escalón ni importe**, con todo de remanente |
| `CA-CM-257` | Los nuevos de un superior se reparten en **propios y de su red**, y suman los nuevos |
| `CA-CM-258` | Filtra por persona, producto, cierre, fechas y si pagó, combinables; los filtros inválidos se rechazan todos juntos; la primera fila de una persona y producto es su remanente vigente |
| `CA-CM-259` | Sin `afftrack-settlements:read`, se rechaza; el listado **no hace una consulta por fila** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El escalón que pagó se retiró o se corrigió después | Se muestra **lo que pagó**, no lo que dice hoy el escalón |
| El producto está retirado | Se muestra igual |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-255` a `CA-CM-259`. | Responsable del proyecto |
