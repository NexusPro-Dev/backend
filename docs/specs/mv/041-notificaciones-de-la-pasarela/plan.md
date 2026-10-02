# PLAN — `RF-MV-041` Recibir las notificaciones de la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-041` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 01-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 01-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Dos tiempos, dos transacciones.**

1. **Recibir** (en la petición): leer el cuerpo **como bytes, sin deserializar** —la firma es sobre los bytes exactos—, verificarlo con `CardGateway.verify` (en el adaptador, `Webhook.constructEvent` de Stripe con tolerancia de 300 s), y `INSERT … ON CONFLICT (gateway, external_id) DO NOTHING` en `gateway_events`. Responder `200` con cuerpo vacío. **Si el `INSERT` insertó**, publicar `GatewayEventReceived(id)`.
2. **Procesar** (después del `COMMIT`): un `@TransactionalEventListener(AFTER_COMMIT)` con `@Async` toma el evento con `SELECT … FOR UPDATE SKIP LOCKED` sobre la fila **sin procesar**, lo interpreta, aplica, y escribe `processed_at`, `outcome` y `error` en la **misma** transacción que lo aplicado. Si lanza, la transacción se revierte, `attempts` sube en otra pequeña y la fila sigue pendiente.

**El reintento** es un `@Scheduled` cada minuto que toma las pendientes con más de un minuto y `attempts < 5`, por el mismo procesador. Agotados los cinco, `outcome = 'ERROR'` con el último fallo. Es el mismo patrón que `CM` usa para el devengo (`RN-MV-049`), y `SKIP LOCKED` hace que el oyente y el barrido no procesen la misma fila a la vez.

**La interpretación es por tipo de evento de Stripe**, en una tabla del procesador:

| Evento | Acción |
|---|---|
| `payment_intent.succeeded` | Resolver el pago (§2), comprobar importe y moneda, y confirmar: **`ConfirmSaleService`** si es `VENTA`, **`PointsPurchaseService.confirm`** si es `COMPRA_PUNTOS`, por su camino interno sin la comprobación de `RN-MV-058` que impide hacerlo a mano |
| `payment_intent.payment_failed` | `PROCESADO`, con `last_payment_error.message` en `error`; el pago no cambia |
| `payment_intent.canceled` | Rechazar el pago si sigue pendiente, con el motivo «cobro cancelado en la pasarela» |
| `charge.refunded` | `incident = 'REEMBOLSADO'`, `refunded_amount = amount_refunded` (acumulado, de la pasarela) |
| `charge.dispute.created` | `incident = 'EN_DISPUTA'` |
| `charge.dispute.closed` | `DISPUTA_GANADA` si `status = won`, `DISPUTA_PERDIDA` si `lost`; otro estado, `ERROR` |
| Otro | `IGNORADO` |

---

## 2. Resolver el pago

**Por la `metadata.payment_id` del PaymentIntent, y comprobado contra `provider_reference`**: las dos tienen que coincidir. Para los eventos de `charge.*`, el PaymentIntent es `charge.payment_intent` (y en una disputa, `dispute.payment_intent`). **Lo que no resuelve un pago es `EX-003`**: queda con `outcome = 'ERROR'` y **no se reintenta** —no va a resolverse solo—, salvo el caso de la incidencia sobre un pago aún no confirmado (`spec.md` §13), que **sí** se reintenta.

**Importe y moneda**: `amount_received` en unidad mínima y `currency` en minúsculas, contra el `amount` del pago y el código de la moneda del movimiento, con la misma conversión de `RF-MV-040`.

---

## 3. Cambios de esquema

Ninguno: los trae `RF-MV-040`.

---

## 4. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `CardGateway.verify` | Del puerto de `RF-MV-040` | Devuelve `GatewayEvent(externalId, type, json, paymentIntentId, metadataPaymentId, amountReceived, currency, refunded, disputeStatus, failureMessage)`, sin tipos de Stripe |
| `domain/repository` | `GatewayEventRepository`, `JpaGatewayEventRepository` | Nuevos | `insertIfNew`, `lockPending(id)`, `pendingForRetry`, `finish`, `failAttempt` |
| `domain/service` | `GatewayEventIntake` | Nuevo | La primera transacción |
| `domain/service` | `GatewayEventProcessor` | Nuevo | La segunda: la tabla de §1, el oyente y el barrido |
| `domain/service` | `ConfirmSaleService`, `PointsPurchaseService`, `RejectPaymentService` | Ganan un camino **de la pasarela** | El mismo efecto que el manual, sin la comprobación de `RN-MV-058` y con `source = 'STRIPE'` en la auditoría |
| `domain/repository` | `PaymentRepository` | Gana `findByReference`, `setIncident` | |
| `interfaces` | `GatewayNotificationController` | Nuevo | `POST /movements/gateway-notifications/stripe`, cuerpo `byte[]`, cabecera `Stripe-Signature` |
| `shared/security` | `SecurityConfig` | La ruta, pública | Y fuera de `RateLimitFilter` y de CORS |

---

## 5. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/gateway-notifications/stripe` | **Ninguno: pública**, autenticada por firma |

| Código | Cuándo |
|---|---|
| `200` | Recibida —nueva o repetida—, o con su error de `EX-003`; cuerpo vacío |
| `400` | Firma ausente o inválida, o vieja (`EX-001`) |
| `503` | La pasarela no está configurada (`EX-002`) |

**Se documenta en el contrato** como ruta de la pasarela, para que nadie la llame desde el frontend.

---

## 6. Autorización

**Ninguna**, y es deliberado (`security.md` §6, decimoquinta ruta pública). La firma la autentica, y **`EndpointPermissionsIT` la declara en `PUBLICAS`**.

---

## 7. Auditoría

**La notificación misma es la constancia** de lo que dijo la pasarela, en `gateway_events`. **Lo que aplica se audita como el camino manual** —la confirmación, el rechazo, la incidencia— con la pasarela como origen y sin actor humano. **El cuerpo no va al log de aplicación**: lleva datos de la persona que pagó.

---

## 8. Transaccionalidad

§1. **La recepción no espera al proceso**, y el proceso no se ejecuta en el hilo de la petición: la pasarela recibe su `200` en milisegundos.

---

## 9. Impacto sobre otros módulos

**`CM`** recibe el aviso de las líneas comisionables al confirmarse la venta, **igual que hoy** (`RN-MV-049`): por el mismo `ConfirmSaleService`.

---

## 10. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Procesar dentro de la petición | Un proceso lento haría que la pasarela reenviara, y la etapa 4 decidió responder antes de trabajar |
| Consultar a la pasarela el estado del cobro en vez de fiarse de la notificación | Es lo mismo con una llamada más; la firma ya prueba el origen. Queda para reconciliar a mano si hace falta |
| Deserializar el cuerpo con Jackson y luego verificar | La firma es sobre los bytes exactos; reserializar la rompe |
| Guardar solo lo que se entiende de la notificación | `RN-MV-059`: tal como lo dice, para releerla |

---

## 11. Riesgos

| Riesgo | Mitigación |
|---|---|
| Procesar dos veces la misma notificación | `uq_gateway_events_externo` y `SKIP LOCKED` |
| Confirmar dos veces un pago | La transición condicionada al estado de `ConfirmSaleService`, que ya existe (`RN-MV-039`) |
| Que las pruebas firmen distinto que Stripe | El adaptador se prueba aparte, con un cuerpo y una firma reales de la CLI; las pruebas de integración doblan `verify` |

---

## 12. Estrategia de prueba

Integración, `GatewayNotificationsIT`, con el doble del puerto que **devuelve el `GatewayEvent` que la prueba prepara** o falla la firma: `CA-MV-438` a `CA-MV-452`. `CA-MV-442` hace fallar el procesador una vez y llama al barrido. `CA-MV-440` envía dos veces y cuenta entregas y avisos a `CM`. **Una unitaria del adaptador** con un cuerpo y una cabecera `Stripe-Signature` generados con el secreto de prueba, para fijar que `verify` acepta la firma correcta y rechaza la alterada y la vieja.
