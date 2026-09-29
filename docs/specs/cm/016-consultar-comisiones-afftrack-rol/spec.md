# SPEC — `RF-CM-016` Consultar las comisiones afftrack de rol

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-016` |
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

Que administración vea **la escala de cada rol en cada producto FTD**: qué límites hay y cuánto paga cada uno.

---

## 2. Contexto

Hereda la forma del listado de tasas de rol ([`RF-CM-002`](../002-consultar-tasas-comision/spec.md)): una sola lista con todo lo configurado, filtrable, paginada, **sin las retiradas por omisión** y marcándolas cuando se piden.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El orden muestra la escala** | Por producto, rol y **límite ascendente**: los escalones de una misma escala salen juntos y en el orden en que se alcanzan |
| **Sin el motivo del retiro** | Como `RF-CM-002`: en bloque sería una exportación de decisiones comerciales |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administración | Consulta (`afftrack-rates:read`) |

---

## 4. Alcance

### 4.1 Incluye

- El listado paginado de escalones de rol, filtrable por **producto** y **rol**, con las retiradas **si se piden**.

### 4.2 No incluye

- Los escalones de persona: `RF-CM-019`.
- **Qué escala rige para una persona concreta**: lo decide el cierre (`RN-CM-039`) y se ve en su liquidación (`RF-CM-021`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-CM-005` | Las retiradas permanecen y se pueden ver |
| `RN-CM-039` | La escala de un rol son sus escalones vivos sobre un producto |

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Producto | No | |
| Rol | No | |
| Incluir retiradas | No | Por omisión, **no** |
| Página | No | |

### 6.2 Salida

Por escalón: identificador; rol —identificador, código y nombre—; producto —identificador, código, nombre y moneda—; límite; valor por FTD; cuánto paga al alcanzarse; y, **en los retirados**, desde cuándo lo están.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor porta el permiso |
| Postcondición | Ninguna: no escribe |

---

## 8. Flujo principal

1. Se piden los escalones, con filtros o sin ellos.
2. Se devuelven por producto, rol y límite ascendente.

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
| `VAL-001` | Identificadores bien formados; página y tamaño dentro de lo admitido. Todos juntos |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-217` | Se listan los escalones vivos con rol y producto resueltos y **cuánto paga cada uno al alcanzarse**, en orden de producto, rol y límite ascendente |
| `CA-CM-218` | Filtra por producto y por rol, combinables |
| `CA-CM-219` | Las retiradas **no salen por omisión**, salen marcadas al pedirlas, y **sin motivo** |
| `CA-CM-220` | Sin `afftrack-rates:read`, se rechaza; el listado **no hace una consulta por fila** |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El producto está retirado | Sus escalones se muestran igual |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 29-09-2026 | Primera versión, con la comisión afftrack ([`requirements/cm.md`](../../../requirements/cm.md) v0.22.0 §5.8). Criterios `CA-CM-217` a `CA-CM-220`. | Responsable del proyecto |
