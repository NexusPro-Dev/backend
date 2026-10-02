# SPEC — `RF-MV-045` Rechazar un pago pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-045` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.1 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien concilia diga **«este pago no entró»** sobre **el pago**, con su motivo, sea cual sea su método y sea de una venta o de una compra de puntos, y que **el movimiento lo siga** como corresponde a su tipo.

---

## 2. Contexto

Es el espejo de `RF-MV-044` ([`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8), y hereda su argumentación sin repetirla: se nombra el pago, solo lo que cobra, cualquier método salvo un cobro abierto, el movimiento bloqueado antes que el pago y la misma respuesta. `RF-MV-004` y `RF-MV-029` se quedan sin entrada propia y describen **lo que rechazar le hace** a cada tipo.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El efecto es el de cada tipo, sin cambios** | Una venta **sigue pendiente** y admite otro intento (`RF-MV-004`, `RF-MV-018`). Una compra de puntos **queda rechazada**, sin reintento y sin asientos (`RF-MV-029`). **La asimetría es de los tipos, no de esta entrada**, y está defendida en `requirements/mv.md` §4.4 |
| **El motivo, obligatorio** | Como en `RF-MV-004` y `RF-MV-029`: un rechazo sin motivo no se puede explicar a quien pagó |
| **Mismo permiso que antes para la venta** | `movements:reject-payment`, que ya se llamaba así y pasa a cubrir los dos tipos |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien concilia, con `movements:reject-payment` | Rechaza un pago pendiente |
| La pasarela (`RF-MV-041`) | **No usa esta entrada**: el cobro cancelado recorre el mismo efecto por dentro |

---

## 4. Alcance

### 4.1 Incluye

- Rechazar el pago pendiente de una venta, con el efecto de `RF-MV-004`.
- Rechazar el pago pendiente de una compra de puntos, con el efecto de `RF-MV-029`.

### 4.2 No incluye

- **Anular la venta**: es `RF-MV-005`. Rechazar cierra un intento; anular cierra la venta.
- **Negar un retiro**: es `RF-MV-021`.

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-061` | Se concilia el pago y el movimiento lo sigue según su tipo |
| `RN-MV-039` | Un pago rechazado no vuelve; la venta admite otro intento |
| `RN-MV-058` | Un pago con cobro abierto en la pasarela no se rechaza a mano |

**Ninguna regla nueva**: `RN-MV-061` nace con `RF-MV-044`.

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| El pago | Sí | Cuál se rechaza, por su identificador |
| Motivo | Sí | Por qué no entró, hasta 500 caracteres |

### 6.2 Salida

**El movimiento, tal como queda**, con la forma de su detalle (`RF-MV-007`): el pago rechazado con su instante y su motivo, y el movimiento pendiente —una venta— o rechazado —una compra de puntos—.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene el permiso; el motivo es válido; el pago existe, está pendiente, es de una venta o de una compra de puntos, y no tiene cobro abierto |
| Postcondición | El pago está **rechazado**, con su instante y su motivo; la venta sigue **pendiente** o la compra de puntos está **rechazada**, con el mismo motivo; queda auditado |

---

## 8. Flujo principal

1. El actor indica el pago y el motivo.
2. El sistema valida el motivo antes de tocar nada.
3. Lee el pago y su movimiento, y fija el movimiento.
4. Comprueba que el movimiento cobra, que el pago sigue pendiente y que no tiene cobro abierto.
5. Rechaza el pago y hace lo que corresponde al tipo.
6. Devuelve el movimiento.

---

## 9. Flujos alternativos

### FA-001 — Es el pago de una venta

La venta sigue `PENDIENTE` y sin pago pendiente: quien compró la vuelve a pagar (`RF-MV-018`).

### FA-002 — Es el pago de una compra de puntos

La compra pasa a `RECHAZADA` con el motivo. No hay asientos: no se había movido nada.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | El pago no existe | No encontrado |
| `EX-002` | El pago es de un movimiento que **no cobra** —un retiro— | Conflicto, diciendo que se resuelve al negar el retiro. Nada cambia |
| `EX-003` | El pago **no está pendiente** | Conflicto, diciendo su estado. Nada cambia |
| `EX-004` | El pago tiene **cobro abierto** en la pasarela | Conflicto. Nada cambia |
| `EX-005` | Sin `movements:reject-payment` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El pago tiene forma de identificador |
| `VAL-002` | El motivo no está ausente ni en blanco |
| `VAL-003` | El motivo no pasa de 500 caracteres |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-507` | Rechazar el pago pendiente de una **venta** deja el pago `RECHAZADO` con su instante y su motivo, y la venta **`PENDIENTE`**, sin pago pendiente; **volver a pagarla** (`RF-MV-018`) funciona después |
| `CA-MV-508` | Rechazar el pago pendiente de una **compra de puntos** deja el pago `RECHAZADO` y la compra **`RECHAZADA`** con el mismo motivo, y **no escribe ningún asiento** |
| `CA-MV-509` | Un motivo **ausente, en blanco o de más de 500 caracteres** responde `400` y nada cambia |
| `CA-MV-510` | Un pago que **no existe** responde no encontrado; un identificador **malformado**, `400` |
| `CA-MV-511` | Un pago **ya confirmado o rechazado** responde conflicto diciendo su estado, y nada cambia |
| `CA-MV-512` | El pago de un **retiro** responde conflicto, y nada cambia |
| `CA-MV-513` | Un pago con **cobro abierto** en la pasarela responde conflicto, y nada cambia |
| `CA-MV-514` | Sin `movements:reject-payment` responde prohibido; sin autenticar, `401`. **Lo portan `SUPERADMIN` y `ADMIN`** |
| `CA-MV-515` | Queda **auditado** como en `RF-MV-004` y `RF-MV-029` |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Rechazar y confirmar el mismo pago a la vez | Solo uno se aplica (`RF-MV-044` · `CA-MV-504`) |
| Rechazar el pago de una venta cuyo último intento era con tarjeta **sin cobro** | Se rechaza como cualquier otro |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión ([`requirements/mv.md`](../../../requirements/mv.md) v0.67.0 §4.8), espejo de `RF-MV-044`. El efecto es el de `RF-MV-004` o `RF-MV-029`, sin cambios; motivo obligatorio; `movements:reject-payment` cubre los dos tipos. Criterios `CA-MV-507` a `CA-MV-515`. | Responsable del proyecto |
| 0.1.1 | 01-10-2026 | El motivo se valida con **dos** códigos, `VAL-002` (vacío) y `VAL-003` (largo), los de `RF-MV-004`: los que ya publica el contrato. Sin cambio de comportamiento. | Responsable técnico |
