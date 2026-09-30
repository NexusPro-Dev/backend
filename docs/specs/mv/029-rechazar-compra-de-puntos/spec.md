# SPEC — `RF-MV-029` Rechazar el pago de una compra de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-029` |
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

Que administración declare que **el dinero de una compra de puntos no entró**, diciendo por qué, y que la compra quede cerrada sin haber dado ningún punto.

---

## 2. Contexto

**Es la otra salida de la revisión de `RF-MV-028`** ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Rechazar es final**, al revés que en la venta: una venta conserva el pago rechazado y se vuelve a pagar (`RF-MV-018`) porque tiene líneas, vendedores y estado que no quiere perder; una compra de puntos no tiene nada de eso, y quien siga queriendo los puntos compra otra vez.

**No mueve ningún saldo**: la compra pendiente no había abonado nada (`RN-MV-051`).

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **El motivo es obligatorio** | Quien compró tiene que poder leer por qué no recibió sus puntos (`RF-MV-031`) |
| **Cierra la compra y su pago** | Los dos quedan rechazados, con el mismo motivo |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:reject-points-purchase` | Rechaza el pago de cualquier compra de puntos pendiente |

---

## 4. Alcance

### 4.1 Incluye

- Rechazar el pago pendiente y cerrar la compra como rechazada, con motivo.

### 4.2 No incluye

- **Volver a pagar** una compra rechazada.
- **Devolver** una compra ya confirmada: una compra abonada no se anula (`RN-MV-051`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-051` | Rechazar deja la compra rechazada, con motivo, sin asientos, y es final |
| `RN-MV-039` | El pago pasa a rechazado |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| Compra | Sí | Cuál |
| Motivo | Sí | Por qué, escrito para quien compró. Con contenido y acotado |

### 6.2 Salida

**La compra rechazada**, con su motivo y su pago.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:reject-points-purchase`; la compra existe y está pendiente; el motivo es válido |
| Postcondición | La compra y su pago están rechazados, con el motivo y el instante; **ningún saldo cambió**; está auditado |

---

## 8. Flujo principal

1. El actor indica la compra y el motivo.
2. El sistema pasa la compra de pendiente a rechazada —solo si seguía pendiente— y rechaza su pago, **en un solo acto**.
3. Audita y devuelve la compra.

---

## 9. Flujos alternativos

Ninguno.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | No existe, o no es una compra de puntos | No encontrado |
| `EX-002` | No está pendiente | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | Motivo vacío o demasiado largo | Rechazo |
| `EX-004` | Quien pide no tiene `movements:reject-points-purchase` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | Motivo con contenido y dentro de la longitud máxima |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-326` | Una compra pendiente pasa a **rechazada**, con el motivo y el instante, y su pago también |
| `CA-MV-327` | **Ningún saldo cambia** y no hay asientos |
| `CA-MV-328` | Rechazar **por segunda vez**, o una compra **confirmada**: conflicto, y nada cambia |
| `CA-MV-329` | Sin motivo, o con uno demasiado largo: rechazo, y nada cambia |
| `CA-MV-330` | El identificador de una **venta** o de un **retiro** responde no encontrado |
| `CA-MV-331` | Sin `movements:reject-points-purchase` responde prohibido; sin autenticar, `401`. El cambio queda auditado, con quién rechazó |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Se rechaza y confirma a la vez | Ver `RF-MV-028` `CA-MV-323`: gana uno, y si gana el rechazo no se abona nada |

---

## 14. Preguntas abiertas

Ninguna.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 30-09-2026 | Primera versión, con la etapa 3 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.54.0 §4.4). **Rechazar es final** y cierra la compra, al revés que en la venta; motivo obligatorio; sin asientos. Criterios `CA-MV-326` a `CA-MV-331`. | Responsable del proyecto |
