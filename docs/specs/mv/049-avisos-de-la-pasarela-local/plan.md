# PLAN — `RF-MV-049` Recibir los avisos de la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-049` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**El camino de la notificación de Stripe, sin verificar firma y con una consulta en medio.** El controlador guarda el aviso en `gateway_events` con `gateway = 'PAYRETAILERS'` —`GatewayEventIntake`, el mismo que usa Stripe— y responde `200`. Tras el `COMMIT`, **`LocalChargeReconciler.porAviso(id)`** lee el identificador del cobro, busca el pago (`PaymentRepository.findByReference`) y, si existe, llama a **`LocalChargeReconciler.conciliar(pago)`**: `LocalPaymentGateway.retrieve` fuera de transacción y, en una transacción corta, el desenlace. **`conciliar` es el mismo método que usa el barrido** (`RF-MV-050`).

**`external_id` del evento es el identificador del cobro más el estado anunciado**: dos avisos iguales son el mismo evento (`uq_gateway_events_externo`) y un aviso nuevo con otro estado no choca.

---

## 2. Cambios de esquema

Ninguno: los trae `RF-MV-048` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `LocalChargeReconciler` | Nuevo | `porAviso(eventoId)`, `conciliar(pago)`; reutiliza `ConfirmPaymentService` y `RejectPaymentService`, y `PointsPurchaseService` para la compra de puntos, como `GatewayEventProcessor` |
| `domain/repository` | `PaymentRepository` | `markIncident(pago, "COBRO_TARDIO", instante)` | `RN-MV-064` |
| `domain/service` | `VoidSaleService`, `RetryPaymentService` | Con un cobro local abierto, **rechazan el pago** con su motivo en lugar de cancelar en la pasarela | `FA-003` |
| `infrastructure` | `PayRetailersGateway` | `parse(payload)` del aviso y `retrieve` | Estados de la pasarela a cuatro desenlaces: `APROBADO`, `FINAL_NO_APROBADO`, `PENDIENTE`, `DESCONOCIDO` |
| `interfaces` | `GatewayNotificationController` | `POST /movements/gateway-notifications/payretailers` | Al lado del de Stripe |
| `shared/security` | La lista de rutas públicas | Gana la nueva | Dentro de la cota de tasa, fuera de CORS (`security.md` §6) |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/gateway-notifications/payretailers` | **Pública** |

| Código | Cuándo |
|---|---|
| `200` | Recibido y guardado —también si se ignora— |
| `400` | Ilegible (`EX-003`) |
| `503` | Pasarela apagada (`EX-001`) |

---

## 5. Autorización

Ninguna: pública. **La protege que no se cree** (`security.md` §6).

---

## 6. Auditoría

`gateway_events` guarda el aviso y su desenlace. La confirmación o el rechazo del pago se auditan como siempre; la incidencia, como las de la tarjeta.

---

## 7. Transaccionalidad

Guardar y responder, en una transacción. La consulta, fuera. El desenlace, en otra corta por pago, **con el pago bloqueado**, para que el aviso y el barrido no lo apliquen a la vez.

---

## 8. Impacto sobre otros módulos

**`CM`**: recibe el aviso de venta confirmada por el camino de siempre.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Creer el estado del aviso | No va firmado (`RN-MV-064`) |
| Consultar dentro de la petición del aviso | La pasarela no espera; y un fallo de la consulta perdería el aviso, que no se reintenta |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Avisos falsos en masa para que consultemos a la pasarela | Solo se consulta si el cobro es de un pago; y la cota de tasa |
| El nombre exacto del campo del identificador en el aviso | Lo lee solo el adaptador; se ajusta con el sandbox |

---

## 11. Estrategia de prueba

Integración, `LocalChargeNotificationIT`, con un doble de `LocalPaymentGateway` que contesta lo que cada prueba necesita: `CA-MV-612` a `CA-MV-619`. `CA-MV-614` es la prueba central: el aviso dice aprobado, la pasarela dice pendiente, y nada cambia. `EndpointPermissionsIT` gana la ruta pública.
