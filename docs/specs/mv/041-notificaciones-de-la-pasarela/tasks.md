# TASKS — `RF-MV-041` Recibir las notificaciones de la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-041` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** — tareas `Hecha` el 01-10-2026, salvo la prueba manual contra Stripe |
| Issue | [#161](https://github.com/NexusPro-Dev/backend/issues/161) |
| Rama | `feature/stripe-tarjeta` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `CardGateway.verify` y su implementación en `StripeCardGateway`, con `GatewayEvent` sin tipos de Stripe | `RF-MV-040` · `T-03` | Unitaria con firma real: correcta, alterada, vieja | **Hecha** — 01-10-2026 |
| `T-02` | `GatewayEventRepository`: `insertIfNew`, `lockPending` con `SKIP LOCKED`, `pendingForRetry`, `finish`, `failAttempt` | `RF-MV-040` · `T-01` | | **Hecha** — 01-10-2026 |
| `T-03` | `GatewayEventIntake` y `GatewayNotificationController`; la ruta pública, fuera de la cota y de CORS | `T-01`, `T-02` | `200` vacío; `400` sin guardar; `503` sin configurar | **Hecha** — 01-10-2026 |
| `T-04` | El camino de la pasarela en `ConfirmSaleService`, `PointsPurchaseService` y `RejectPaymentService`; `PaymentRepository.findByReference` y `setIncident` | — | El mismo efecto que el manual | **Hecha** — 01-10-2026 |
| `T-05` | `GatewayEventProcessor`: la tabla de eventos, la resolución del pago con su comprobación, el oyente `AFTER_COMMIT` asíncrono y el barrido programado con cinco intentos | `T-02`, `T-04` | | **Hecha** — 01-10-2026 |
| `T-06` | `GatewayNotificationsIT`: `CA-MV-438` a `CA-MV-452` | `T-03`, `T-05` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-07` | `EndpointPermissionsIT` (`PUBLICAS`); contrato con la prosa; prueba manual con `stripe trigger` de los seis eventos; `requirements.md` | `T-06` | | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

Después de `RF-MV-040` `T-01`, `T-03` y `T-05`: `T-01` y `T-02` y `T-04` en paralelo → `T-03` → `T-05` → `T-06` → `T-07`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-438`, `CA-MV-439`, `CA-MV-452` | `T-04`, `T-05`, `T-06` |
| `CA-MV-440`, `CA-MV-442` | `T-02`, `T-05`, `T-06` |
| `CA-MV-441`, `CA-MV-450`, `CA-MV-451` | `T-01`, `T-03`, `T-06` |
| `CA-MV-443` a `CA-MV-449` | `T-05`, `T-06` |

---

## 3.1 Desviaciones respecto del plan

**Sin la biblioteca de Stripe**: el adaptador llama a su API HTTP con `RestClient` (`architecture.md` §15.4), de modo que no hay dependencia `stripe-java` en el `pom.xml`, y la regla de ArchUnit es `laPasarelaEsUnPuerto` —nada fuera de `movements.infrastructure` depende de esa capa— en vez de «nada importa `com.stripe`». **Una sola suite**, `CardPaymentIT`, con el doble `FakeCardGateway`, en vez de `CardChargeIT`, `GatewayNotificationsIT` y `PayPendingByCardIT`; los criterios de las compras por hotlink y de paquete, del funcionario y del alta van en `BuyByHotlinkIT`, `BuyPackageIT`, `RegisterSaleIT` y `SelfRegistrationIT`. **El proceso corre en `REQUIRES_NEW`**: dentro de `AFTER_COMMIT`, una transacción `REQUIRED` se sumaba a la que ya terminó y no confirmaba nada (lo destapó la suite). En las pruebas se procesa en el hilo que recibe (`CardGateway.processInline`), para que sea determinista; en ejecución, en el ejecutor de la aplicación. `CardGateway` gana `parse`, que interpreta lo guardado sin volver a verificar la firma, y `StripeEvents` la interpretación compartida con el doble. El `503` estrena `ProblemKind.SERVICIO_NO_DISPONIBLE`. **`T-07` queda a medias**: `stripe trigger` espera las credenciales.

## 4. Bloqueos

**Las credenciales de prueba** para `T-07`, como en `RF-MV-040`.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los quince criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Los seis eventos disparados con la Stripe CLI contra el entorno local.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` actualizado.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
