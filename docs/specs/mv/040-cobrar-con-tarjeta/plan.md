# PLAN — `RF-MV-040` Cobrar con tarjeta por la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-040` |
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

**Un componente, `CardPayment`, que las entradas llaman después de guardar el pago**, como llaman hoy a `PointsPayment` cuando el método es `POINTS`. Decide si toca abrir cobro —método con `gateway = 'STRIPE'`, pasarela encendida, entrada en que quien compra paga—, lo abre por el puerto `CardGateway` (`architecture.md` §15.4) y guarda la referencia. **El cobro es un PaymentIntent de Stripe**, con `payment_method_types = ['card']`, el importe en la **unidad mínima** de la moneda —centavos en USD, según los decimales del catálogo de monedas—, la clave de idempotencia **del pago** en la cabecera `Idempotency-Key` de Stripe y `payment_id`, `movement_id` y `movement_code` en su `metadata`.

**Dentro de la transacción del pago, y con su deshacer.** La llamada ocurre después del `INSERT` del pago y antes del `COMMIT`: si falla, la excepción revierte todo (`CA-MV-432`). **Si lo que falla es el `COMMIT` después de abrir el cobro**, un `TransactionSynchronization` registrado al abrirlo **cancela el cobro** en `afterCompletion(STATUS_ROLLED_BACK)`: es lo que reduce el caso límite de `spec.md` §13 a un cobro cancelado, y no a uno huérfano que alguien podría pagar. Si esa cancelación falla también, se registra en el log y la notificación de un pago que nadie respalda queda con su error (`RF-MV-041`).

**Este requerimiento carga la migración de toda la etapa 4**, como `RF-MV-032` cargó `V61`: las piezas —tabla, columnas, permiso, método— se prueban juntas.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir** —`V62` o superior—, `V6x__mv_pasarela_stripe.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `payment_methods.gateway` | `varchar(20) NULL`, con `ck_payment_methods_gateway`; `UPDATE … SET gateway = 'STRIPE' WHERE code = 'CREDIT_CARD'` | `requirements/mv.md` §7.4 |
| `payments.incident`, `incident_at`, `refunded_amount` | Las de `requirements/mv.md` §7.7, con `ck_payments_incident` | `RN-MV-060`, para `RF-MV-041` |
| `gateway_events` | Las columnas de `requirements/mv.md` §7.14, con `uq_gateway_events_externo` y `ck_gateway_events_outcome` | `RN-MV-059`, para `RF-MV-041` |
| `fk_gateway_events_payment` | → `payments(id)` **`ON DELETE SET NULL`** | La notificación sobrevive a la limpieza del pago |
| `ix_gateway_events_pendientes` | `(received_at) WHERE processed_at IS NULL` | El reintento busca lo pendiente |
| `ix_payments_provider_reference` | `(provider_reference) WHERE provider_reference IS NOT NULL` | La notificación busca el pago por su cobro |
| Permiso | `movements:pay-pending-by-card`, por tipo de rol (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`) | `RF-MV-042`. Como los propios de `V61` |

**Guardas al final**, como `V61`: el método `CREDIT_CARD` tiene pasarela, el permiso existe y lo portan `SUPERADMIN` y `ADMIN`, y la contención (`RN-SEG-003`). Catálogo 180 → **181**, `ADMIN` 178 → **179**.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `pom.xml` | `com.stripe:stripe-java` | Nueva dependencia | La versión vigente al construir |
| `domain/service` | `CardGateway` | **Nuevo puerto** | `open(ChargeRequest) → Charge(reference, clientSecret)`, `retrieve(reference)`, `cancel(reference)`, `verify(cuerpo, firma) → Event` (`RF-MV-041`). Sin tipos de Stripe en su firma |
| `infrastructure` | `StripeCardGateway` | **Nuevo adaptador** | El único que importa `com.stripe`. Traduce los errores de Stripe a `CardGatewayException` |
| `infrastructure` | `CardGatewayConfig` | Nuevo | Crea el adaptador **solo** con `nexus.stripe.secret-key` y `webhook-secret`; sin ellas, nada, y un aviso al arrancar (`architecture.md` §15.4) |
| `domain/service` | `CardPayment` | Nuevo | `abrirSiToca(pago, movimiento, importe, moneda, clave, quienPaga)`; el mínimo por moneda (`VAL-001`); el deshacer del §1 |
| `domain/repository` | `PaymentRepository` | Gana `setProviderReference(pago, referencia)` | |
| `domain/service` | `BuyByHotlinkService`, `BuyPackageService`, `RetryPaymentService`, `PointsPurchaseService` | Llaman a `CardPayment` después de guardar el pago | `RegisterSaleService` —la venta de un funcionario (`RF-MV-001`)— y la del alta (`RF-SP-045`) **no** lo llaman |
| `application` | `CardChargeResponse` | Nuevo, `@Schema(name = "CardCharge")` | `paymentId`, `gateway`, `clientSecret` |
| `application` | `PurchaseResponse`, `PointsPurchaseResponse`, la respuesta de volver a pagar | Ganan `cardCharge`, nulo sin cobro | |
| `shared/architecture` | `LayerRulesTest` | Gana `soloElAdaptadorImportaStripe` | Nada fuera de `movements.infrastructure` depende de `com.stripe..` |

**El mínimo de la pasarela es una tabla del componente**, por código de moneda —`USD` 0.50—, y una moneda que no esté en ella **no admite tarjeta**: es preferible rechazar a cobrar en una moneda que nadie revisó.

---

## 4. Contrato de API

**Ninguna ruta nueva.** Las respuestas de las tres compras propias que existen —`POST /hotlinks/{username}/{code}/purchases` (`RF-MV-011`), `POST /packages/{code}/purchases` (`RF-MV-012`)—, de `POST /movements/mine/{id}/payments` (volver a pagar, `RF-MV-018`) y de `POST /movements/mine/points-purchases` (`RF-MV-027`) **ganan `cardCharge`**. **`RF-MV-002` y `RF-MV-013` no están construidos**: cuando lo estén, llamarán a `CardPayment` como las demás:

```json
"cardCharge": { "paymentId": "…", "gateway": "STRIPE", "clientSecret": "pi_…_secret_…" }
```

| Código nuevo | Cuándo |
|---|---|
| `422` | El importe no alcanza el mínimo de la pasarela (`EX-001`) |
| `503` | La pasarela no respondió o rechazó abrir el cobro (`EX-002`); se puede reintentar |

**El secreto de cliente no se guarda ni se audita**: se pide a la pasarela cuando hace falta (`RF-MV-042`). Es lo que la app usa una vez y no hay motivo para que viva en la base.

---

## 5. Autorización

Ninguna propia: la de cada entrada.

---

## 6. Auditoría

El `ChangeEvent` del pago gana `provider_reference`. **Ni el secreto de cliente ni la respuesta de la pasarela van a la auditoría ni al log.**

---

## 7. Transaccionalidad

La de cada entrada, con la llamada dentro y el deshacer del §1. **Un tiempo de espera corto** hacia la pasarela —diez segundos— para que una pasarela lenta no retenga la transacción.

---

## 8. Impacto sobre otros módulos

**El frontend**: integra Stripe Elements con la clave publicable y el `clientSecret`. **El contrato OpenAPI se regenera y la prosa de cada `@Operation` tocada se relee.**

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Recibir los datos de la tarjeta en la API y cobrarlos desde el servidor | Pondría a NEXUS en el alcance completo de PCI DSS (`RN-MV-057`) |
| La página de pago alojada por la pasarela (Checkout) | El responsable pidió la tarjeta **en la app** |
| Abrir el cobro después del `COMMIT` | Sin cobro quedaría una venta pendiente que nadie puede pagar desde la app |
| Guardar el secreto de cliente | No hace falta, y es un dato que permite completar el pago |
| Un método nuevo `STRIPE` | La persona elige «tarjeta», no un proveedor; `gateway` en el método lo dice sin cambiar lo que se ve |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un cobro huérfano tras un `COMMIT` fallido | El deshacer del §1 lo cancela |
| La biblioteca de Stripe filtrándose a los servicios | La regla de ArchUnit |
| Las pruebas llamando a Stripe de verdad | El puerto se dobla en las pruebas; la suite corre con la pasarela apagada |
| Recuentos del catálogo | 180 → **181**: los sitios de `V61`, más `SystemRolesSeedIT`, `MovementsPermissionsSeedIT` y `RoleDetailIT` |

---

## 11. Estrategia de prueba

Integración, `CardChargeIT`, con un `CardGateway` **doble** registrado en la prueba que anota las llamadas y puede fallar a voluntad: `CA-MV-426` a `CA-MV-436`. `CA-MV-437` con una petición que trae `cardNumber` y una lectura del contrato. **Unitarias** de la conversión a unidad mínima y del mínimo por moneda. **Ninguna prueba llama a Stripe**: el adaptador se prueba a mano contra el modo de prueba de Stripe, con la CLI, y queda anotado en `tasks.md`.
