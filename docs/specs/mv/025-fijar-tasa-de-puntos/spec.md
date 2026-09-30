# SPEC — `RF-MV-025` Fijar la tasa de puntos de una moneda

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-025` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que administración pueda decir **a cuánto se venden los puntos en cada moneda** («1 USD = 100 puntos») y cambiarlo cuando quiera, **sin perder a cuánto se vendían antes**.

---

## 2. Contexto

**Es lo primero de la etapa 3** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4): sin tasa no se pueden comprar puntos (`RF-MV-027`) ni pagar con ellos (`RF-MV-030`). La decidió el responsable del proyecto el 30-09-2026: **configurable, una por moneda y con histórico**.

**Fijar no es editar.** Cada vez que se fija una tasa queda escrita **una tasa nueva**, que rige desde ese instante; la anterior no se toca (`RN-MV-050`). Es lo que permite explicar una compra de hace meses: la compra dice qué tasa usó, y esa tasa sigue ahí.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Rige desde que se fija** | No se programa una tasa para el futuro. Nadie lo ha pedido, y una tasa futura obligaría a decidir qué se enseña mientras tanto |
| **Fijar la misma que rige no escribe nada** | Devuelve la vigente, y el histórico no se llena de filas que no cambian nada |
| **Solo en monedas activas** | Una moneda retirada no vende nada; fijarle tasa no serviría |
| **No se borra ni se retira una tasa** | Para dejar de vender puntos en una moneda no hay operación todavía. Queda declarado en §14 |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:set-points-rate` | Fija la tasa de cualquier moneda |

---

## 4. Alcance

### 4.1 Incluye

- Fijar la tasa de una moneda, con efecto inmediato.
- Conservar las anteriores.
- Dejar escrito quién la fijó.

### 4.2 No incluye

- **Consultar las tasas**: es `RF-MV-026`.
- **Consultar el histórico**: no se publica (`requirements/mv.md` §4.4).
- **Programar una tasa futura**, **retirar una tasa** o **dejar de vender puntos** en una moneda.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-050` | Una tasa por moneda, mayor que cero; fijar inserta una nueva y la anterior no se toca |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Moneda | Sí | La moneda cuya tasa se fija |
| Puntos por unidad | Sí | Cuántos puntos da una unidad de esa moneda. Mayor que cero, con hasta cuatro decimales |

### 6.2 Salida

**La tasa que rige ahora**: moneda, puntos por unidad y desde cuándo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:set-points-rate`; la moneda existe y está activa; el valor es válido |
| Postcondición | La tasa nueva rige para esa moneda desde ese instante; las anteriores siguen escritas; queda auditado quién la fijó |

---

## 8. Flujo principal

1. El actor indica la moneda y los puntos por unidad.
2. El sistema valida los dos datos, **antes de tocar nada**.
3. Si la tasa vigente de esa moneda ya es ese valor, sigue por `FA-001`.
4. Escribe la tasa nueva, que rige desde ahora.
5. Audita, con la tasa que regía antes, y devuelve la nueva.

---

## 9. Flujos alternativos

### FA-001 — La tasa ya es esa

Se devuelve la vigente, **sin escribir nada y sin auditar**: no ha cambiado nada.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | Algún dato falta o es inválido: valor no positivo, con más de cuatro decimales o demasiado grande | Rechazo, antes de tocar nada |
| `EX-002` | La moneda no existe | Rechazo |
| `EX-003` | La moneda está inactiva | Conflicto |
| `EX-004` | Quien pide no tiene `movements:set-points-rate` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Moneda presente y con forma de identificador |
| `VAL-002` | Puntos por unidad presente, mayor que cero, con hasta cuatro decimales y hasta ocho cifras enteras |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-293` | Fijar una tasa en una moneda **sin tasa** la deja vigente desde ese instante, y la respuesta la devuelve |
| `CA-MV-294` | Fijar otra en la misma moneda la **sustituye**: rige la nueva y **la anterior sigue escrita, sin cambios** |
| `CA-MV-295` | Fijar **la misma que rige** responde con la vigente y **no escribe nada**, ni tasa ni auditoría |
| `CA-MV-296` | Valor cero, negativo, con cinco decimales o ausente: rechazo, y **nada cambia** |
| `CA-MV-297` | Moneda inexistente: rechazo; moneda **inactiva**: conflicto. En los dos casos nada cambia |
| `CA-MV-298` | Sin `movements:set-points-rate` responde prohibido; sin autenticar, `401` |
| `CA-MV-299` | Queda **auditado**, con quién la fijó y la tasa que regía antes |
| `CA-MV-300` | Las tasas de **otras monedas** no cambian |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Dos personas fijan tasa en la misma moneda a la vez | Quedan las dos, y rige la que se escribió después |
| Se fija una tasa y en el mismo instante alguien compra puntos | La compra usa la que regía cuando se registró; la tasa nueva vale para las siguientes |
| Tasa con valor menor que uno («1 COP = 0.0250 puntos») | Se admite |

---

## 14. Preguntas abiertas

**Dejar de vender puntos en una moneda.** Hoy no hay forma de retirar una tasa; se puede desactivar la moneda.

**Consultar el histórico.** Se guarda y no se publica.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Fijar inserta una tasa nueva y conserva las anteriores**; rige desde que se fija; fijar la vigente no escribe nada. Criterios `CA-MV-293` a `CA-MV-300`. | Responsable del proyecto |
