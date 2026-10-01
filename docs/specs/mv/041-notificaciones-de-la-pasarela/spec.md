# SPEC — `RF-MV-041` Recibir las notificaciones de la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-041` |
| Módulo | `MV` — Movimientos |
| Versión | 0.1.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`. No se nombran tablas, clases, endpoints ni librerías.

---

## 1. Objetivo

Que **lo que dice la pasarela de pago** —el cobro entró, la tarjeta se rechazó, el cobro se canceló, se devolvió el dinero, el cliente lo disputó— **llegue al sistema y se aplique una sola vez**, sin que nadie tenga que mirarlo a mano.

---

## 2. Contexto

Con la tarjeta (`RF-MV-040`), la app sabe que el formulario terminó, pero **no sabe si el dinero entró**. Lo sabe la pasarela, que lo **notifica** a este sistema, **firmado**, y **lo reenvía** mientras no reciba respuesta ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6). Esa notificación es **la única fuente de verdad** de un pago con cobro abierto (`RN-MV-058`): las confirmaciones a mano dejan de alcanzarlo.

**Lo que la etapa 4 decidió el 02-09-2026, y aquí se cumple**: la notificación **se guarda antes de interpretarse**, la respuesta se da **antes de trabajar**, y la doble entrega se para **en el esquema**, porque no es el caso raro sino el normal.

### 2.1 Lo que este requerimiento decide

| Notificación | Qué hace |
|---|---|
| **El cobro entró** | Confirma el pago **si sigue pendiente** y el importe y la moneda son los suyos; con él se confirma el movimiento, **por el mismo camino que la confirmación a mano**: la venta entrega y avisa a comisiones; la compra de puntos los abona |
| **La tarjeta se rechazó** | **Se registra** con lo que contestó el banco, y el pago **sigue pendiente**: la app deja reintentar el mismo cobro con otra tarjeta (§4.6) |
| **El cobro se canceló** | Rechaza el pago si sigue pendiente. Si ya lo estaba —porque la cancelación la pidió este sistema— no hay nada que hacer |
| **Se devolvió dinero** | Marca el pago confirmado como **reembolsado**, con el importe devuelto acumulado. **No revierte nada** (`RN-MV-060`) |
| **Se abrió una disputa** | Marca el pago como **en disputa** |
| **Se cerró una disputa** | Marca el pago como **disputa ganada** o **disputa perdida** |
| **Cualquier otra** | Se guarda y **se ignora** |

**Y lo que no cuadra no se aplica**: un cobro que entró por un importe o una moneda distintos de los del pago, o para un pago que ya no está pendiente, o que no corresponde a ningún pago, **no confirma nada** y queda guardado con su error, para que administración lo vea.

---

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| La pasarela de pago | Notifica; no tiene sesión y se identifica por su firma |

---

## 4. Alcance

### 4.1 Incluye

- Verificar la firma y la vigencia de cada notificación.
- Guardarla entera, una sola vez.
- Responder en cuanto queda guardada, y procesarla después, con reintentos.
- Aplicar las siete clases de §2.1.

### 4.2 No incluye

- **Revertir** lo entregado o las comisiones de un pago reembolsado o perdido en disputa (`RN-MV-060`).
- **Consultar** las notificaciones guardadas por API. Administración ve la incidencia en el pago (`RF-MV-006`).
- **Avisar por correo.**

---

## 5. Reglas de negocio aplicables

| Regla | Cómo aplica |
|---|---|
| `RN-MV-059` | Firma, guardado previo, una sola vez, respuesta antes de trabajar, lo inesperado se ignora |
| `RN-MV-058` | La notificación confirma o rechaza; se comprueban importe y moneda; la tarjeta rechazada no cierra el pago |
| `RN-MV-060` | Reembolso y disputa se marcan, sin revertir |
| `RN-MV-039` | A lo sumo un pago confirmado; de un pago confirmado o rechazado no se vuelve |
| `RN-MV-049`, `RN-MV-051` | Confirmar una venta avisa a comisiones; confirmar una compra de puntos los abona |

**Ninguna regla nueva.**

---

## 6. Datos

### 6.1 Entrada

**El cuerpo de la notificación, tal como lo envía la pasarela**, y su firma.

### 6.2 Salida

Un acuse de recibo, **sin datos**: la pasarela solo necesita saber que se recibió.

---

## 7. Precondiciones y postcondiciones

| Tipo | Condición |
|---|---|
| Precondición | La pasarela está configurada; la firma verifica y la notificación no es vieja |
| Postcondición | La notificación está guardada una vez. Procesada, el pago queda como §2.1 dice, o la notificación queda con su error |

---

## 8. Flujo principal

1. Llega una notificación con su firma.
2. El sistema verifica la firma y que no sea vieja, **antes de guardar nada**.
3. La guarda entera, si no la tenía ya. Si la tenía, sigue por `FA-001`.
4. Responde que la recibió.
5. **Después**, la procesa según su clase (§2.1) y anota el desenlace.

---

## 9. Flujos alternativos

### FA-001 — La notificación ya se había recibido

Se responde que se recibió, **sin guardarla otra vez y sin procesarla otra vez**.

### FA-002 — El proceso falla

La notificación queda guardada y pendiente, y **se reintenta** después, hasta un número de veces; agotado, queda con su error para administración. **No se le pide a la pasarela que la reenvíe.**

---

## 10. Excepciones

| ID | Situación | Resultado |
|---|---|---|
| `EX-001` | La firma falta o no verifica, o la notificación es vieja | Rechazo, **sin guardar nada** |
| `EX-002` | La pasarela no está configurada en este entorno | Servicio no disponible, sin guardar nada |
| `EX-003` | El cobro no cuadra con el pago —importe, moneda, pago que no está pendiente o que no existe— | La notificación se guarda con su error y **no se aplica**. A la pasarela se le responde que se recibió |

---

## 11. Validaciones

| ID | Validación |
|---|---|
| `VAL-001` | La firma verifica con el secreto compartido |
| `VAL-002` | La marca de tiempo de la firma no tiene más de cinco minutos |

---

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-MV-438` | Un **cobro que entró** por el importe y la moneda del pago lo **confirma**, y con él la venta: queda confirmada, **entrega** y **avisa a comisiones**, igual que la confirmación a mano |
| `CA-MV-439` | Un cobro que entró para una **compra de puntos** confirma el pago y **abona los puntos** |
| `CA-MV-440` | **La misma notificación recibida dos veces** se guarda **una** y se aplica **una**: la venta entrega una vez y comisiones recibe un aviso |
| `CA-MV-441` | Una notificación con **firma inválida**, sin firma, o con una marca de tiempo de **más de cinco minutos**, se rechaza y **no se guarda** |
| `CA-MV-442` | La respuesta se da **en cuanto se guarda**; si el proceso falla, la notificación queda pendiente y **un reintento** la aplica |
| `CA-MV-443` | Una **tarjeta rechazada** queda registrada con su motivo y el pago **sigue pendiente**; después, un cobro que entra sobre el mismo cobro lo confirma |
| `CA-MV-444` | Un **cobro cancelado** rechaza el pago pendiente; la venta sigue pendiente y admite volver a pagar |
| `CA-MV-445` | Un **reembolso** marca el pago confirmado como reembolsado con el importe devuelto —también parcial, y acumulado si hay dos—, y **la venta, lo entregado, los saldos y las comisiones no cambian** |
| `CA-MV-446` | **Abrir una disputa** marca el pago en disputa; **cerrarla** lo marca ganada o perdida |
| `CA-MV-447` | Un cobro que entró por **otro importe o en otra moneda** no confirma nada, y la notificación queda con su error |
| `CA-MV-448` | Un cobro que entró para un pago **ya rechazado**, o para **ninguno**, no confirma nada y queda con su error |
| `CA-MV-449` | Una notificación de una **clase que no se espera** se guarda y se marca **ignorada** |
| `CA-MV-450` | Con la pasarela **sin configurar**, la ruta responde servicio no disponible y no guarda nada |
| `CA-MV-451` | La ruta **no exige sesión**: sin token y con firma válida, se acepta |
| `CA-MV-452` | La confirmación del pago queda **auditada** como la confirmación a mano, con la pasarela como origen |

---

## 13. Casos límite

| Caso | Comportamiento |
|---|---|
| El cobro entra mientras alguien anula la venta | Gana el primero que escribe: si la anulación canceló el cobro antes, la pasarela no lo cobra; si la pasarela lo cobró antes, la anulación se rechaza (`RN-MV-058`) |
| Las notificaciones llegan desordenadas —el reembolso antes que el cobro— | El reembolso de un pago que todavía no está confirmado no se aplica: queda con su error, y **un reintento** posterior lo aplica cuando el pago ya está confirmado |
| Un cobro abierto por una venta que se deshizo (`RF-MV-040` §13) | No hay pago: queda con su error |

---

## 14. Preguntas abiertas

**Cuántos reintentos.** El plan fija un número; si administración necesita reprocesar a mano, será un requerimiento propio.

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 01-10-2026 | Primera versión, con la etapa 4 de `MV` para la tarjeta ([`requirements/mv.md`](../../../requirements/mv.md) v0.64.0 §4.6). **La notificación firmada es la única fuente de verdad**; se guarda antes de interpretarse y una sola vez; la tarjeta rechazada no cierra el pago; reembolsos y disputas se marcan. Criterios `CA-MV-438` a `CA-MV-452`. | Responsable del proyecto |
