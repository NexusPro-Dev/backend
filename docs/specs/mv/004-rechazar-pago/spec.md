# SPEC — `RF-MV-004` Rechazar el pago pendiente de una venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-004` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que quien concilia pagos pueda decir **«este pago no entró»**, dejando escrito por qué, **sin que la venta se pierda**: queda pendiente, y el comprador puede volver a pagarla (`RF-MV-018`).

---

## 2. Contexto

**Estaba declarado desde el 02-09-2026 como «rechazar una venta pendiente»**, y nunca se escribió. El 26-09-2026 el responsable del proyecto decidió que **el método y el resultado del cobro son de cada intento de pago y no de la venta** ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0 §4.3, `RN-MV-039`), y con ello este requerimiento **cambia de objeto sin cambiar de sentido**: lo que se rechaza es **el pago**, y la venta sigue viva.

**Por qué la venta no se rechaza con él.** Rechazar la venta obligaba a registrar otra para volver a intentarlo, y dejaba en el registro del comprador dos compras de lo mismo. Con el rechazo en el pago, **el número que `requirements/mv.md` §4.1 quería proteger** —cuánto se intenta cobrar y no entra— se sigue pudiendo contar, y mejor: cuenta **intentos**, no ventas.

**Rechazar no es anular** (`RF-MV-005`), y la diferencia sigue siendo de negocio: anular dice «esta venta no debía existir» y la cierra; rechazar dice «este cobro no entró» y la deja abierta.

### 2.1 Lo que este requerimiento decide

| Decisión | Qué se decidió |
|---|---|
| **Solo el pago pendiente de una venta pendiente** | Un pago confirmado ya cobró; uno rechazado ya es final. Una venta confirmada o anulada no tiene pago pendiente |
| **El motivo es obligatorio** | Es lo que le dice al comprador por qué tiene que volver a pagar. Se exige antes de tocar nada |
| **La venta no cambia** | Sigue `PENDIENTE`; no se entrega ni se retira nada, porque no se había entregado nada (`RN-MV-004`) |
| **Rechazar dos veces rechaza una vez** | Misma transición atómica que confirmar y anular: la segunda no encuentra pago pendiente |
| **Un permiso propio** | `movements:reject-payment`, y no el de confirmar: `RN-SEG-014` —un permiso por operación— no admite que uno gobierne las dos |

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Quien tiene `movements:reject-payment` | Rechaza el pago pendiente de cualquier venta. Hoy, quien concilia pagos a mano; mañana, la pasarela por el mismo camino |

---

## 4. Alcance

### 4.1 Incluye

- Pasar el pago pendiente de una venta a **rechazado**, con **cuándo** y **por qué**.
- Devolver la venta con sus pagos.
- Auditar el cambio.

### 4.2 No incluye

- **Cerrar la venta**: eso es anular (`RF-MV-005`).
- **Volver a pagar** (`RF-MV-018`).
- **Recibir el rechazo de una pasarela.** Cuando llegue, escribirá el mismo resultado por su propio camino.
- **Rechazar el pago de algo que no sea una venta.** El retiro tiene su propia negativa (`RF-MV-021`).

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-039` | Un pago rechazado **no cambia el estado del movimiento**; `RECHAZADO` es final |
| `RN-MV-005` | La venta **ya no se rechaza** (enmendada el 26-09-2026): va de pendiente a confirmada o anulada |
| `RN-MV-004` | Una venta pendiente no concedió nada; rechazar su pago no tiene nada que retirar |
| `RN-MV-001` | La venta no se toca |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción |
|---|---|---|
| La venta | Sí | De cuál se rechaza el pago pendiente, por su identificador |
| Motivo | **Sí** | Por qué no entró el cobro, escrito para una persona. Con contenido y acotado en longitud |

**Se indica la venta y no el pago** porque una venta tiene a lo sumo un pago pendiente (`RN-MV-039`): no hay ambigüedad, y quien concilia trabaja con el comprobante de la venta, que es el código que conoce.

### 6.2 Salida

**La venta, con sus pagos**: el último, rechazado, con su instante y su motivo.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | El actor tiene `movements:reject-payment`; la venta existe, es una venta y está pendiente; tiene un pago pendiente; el motivo tiene contenido |
| Postcondición | Ese pago está rechazado, con su instante y su motivo; la venta sigue pendiente y sin pago pendiente; el cambio está auditado con el motivo |

---

## 8. Flujo principal

1. El actor indica de qué venta rechaza el pago y por qué.
2. El sistema comprueba que el motivo tiene contenido, **antes de mirar la venta**.
3. Pasa el pago pendiente de esa venta a rechazado **en un solo acto que solo acierta si la venta sigue pendiente y el pago también**, anotando el instante y el motivo.
4. Audita el cambio.
5. Devuelve la venta con sus pagos.

---

## 9. Flujos alternativos

### FA-001 — Rechazar y confirmar llegan a la vez

Una gana. Si ganó rechazar, confirmar recibe «no tiene un pago pendiente»; si ganó confirmar, rechazar recibe «la venta está confirmada» y **no puede deshacerlo**.

### FA-002 — La venta es de importe cero

Se rechaza igual. Es raro —el pago gratuito no espera dinero de nadie— y no se prohíbe: si alguien lo registró por error, rechazarlo y anular la venta es la salida.

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La venta no existe, o no es una venta | No encontrado |
| `EX-002` | La venta **no está pendiente** | Conflicto, diciendo en qué estado está. Nada cambia |
| `EX-003` | La venta está pendiente **sin pago pendiente** —el último ya se rechazó— | Conflicto: no hay pago que rechazar. Nada cambia |
| `EX-004` | El motivo falta o está vacío | Rechazo, antes de tocar la venta |
| `EX-005` | Quien pregunta no tiene `movements:reject-payment` | Prohibido |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | El identificador es válido |
| `VAL-002` | El motivo tiene contenido |
| `VAL-003` | El motivo no excede la longitud máxima |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-197` | El pago pendiente de una venta pendiente pasa a **rechazado**, con su instante y su motivo, y **la venta sigue pendiente**; la respuesta trae el pago rechazado |
| `CA-MV-198` | Tras el rechazo, el comprador **puede volver a pagar** la venta (`RF-MV-018`) |
| `CA-MV-199` | Rechazar **por segunda vez** responde conflicto —no hay pago pendiente— y no cambia nada |
| `CA-MV-200` | Una venta **confirmada** o **anulada** responde conflicto diciendo su estado, y su pago no cambia |
| `CA-MV-201` | Una venta que **no existe** responde no encontrado |
| `CA-MV-202` | Sin motivo, o con motivo en blanco, responde rechazo **y el pago sigue pendiente** |
| `CA-MV-203` | Sin `movements:reject-payment` responde prohibido —**también con `movements:confirm`**—; sin autenticar, `401` |
| `CA-MV-204` | El cambio queda **auditado con el motivo** |
| `CA-MV-205` | El detalle propio del comprador muestra el pago rechazado **con su motivo** |

**`CA-MV-203` con `movements:confirm` puesto es el que sostiene la separación de permisos**: hasta el 26-09-2026 estaba previsto que los dos fueran uno.

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| Una venta **anterior al 26-09-2026** | Tiene su pago desde la migración y se rechaza igual |
| Una venta de **paquete** | Se rechaza el pago de la venta entera: el pago es de la venta, no de la línea |
| Motivo con espacios alrededor | Se guarda recortado |
| Muchos pagos rechazados en la misma venta | Se admiten: cada uno es un intento, y ninguno cobró |

---

## 14. Preguntas abiertas

**Si un pago rechazado debe avisar al comprador.** Hoy lo ve al abrir su compra. Una notificación es otro requerimiento.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 26-09-2026 | Primera versión, con la etapa 6 de `MV` ([`requirements/mv.md`](../../../requirements/mv.md) v0.44.0). **Declarado desde el 02-09-2026 como «rechazar una venta pendiente»** y nunca escrito; **cambia de objeto**: lo que se rechaza es el pago, y la venta queda pendiente para volver a pagarse. **El motivo es obligatorio**, **la transición es atómica** como en confirmar y anular, y **el permiso es propio** (`movements:reject-payment`, `RN-SEG-014`). Criterios `CA-MV-197` a `CA-MV-205`. | Responsable del proyecto |
