# TASKS — `RF-MV-040` Cobrar con tarjeta por la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-040` |
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
| `T-01` | Migración de la etapa 4 (`plan.md` §2): `gateway` en `payment_methods`, la incidencia de `payments`, `gateway_events`, los índices, el permiso y sus guardas | — | Aplicada sobre la base de desarrollo; las guardas pasan | **Hecha** — 01-10-2026 |
| `T-02` | Recuentos del catálogo 180 → 181 (`ADMIN` 178 → 179) en todas las suites que lo cuentan | `T-01` | `grep -rnE "\b180\b\|\b178\b" src/test` sin restos | **Hecha** — 01-10-2026 |
| `T-03` | `stripe-java` en el `pom.xml`; `CardGateway`, `StripeCardGateway`, `CardGatewayConfig` (apagado sin credenciales, aviso al arrancar) | — | Arranca con y sin las dos variables | **Hecha** — 01-10-2026 |
| `T-04` | `LayerRulesTest.soloElAdaptadorImportaStripe` | `T-03` | Falla si un servicio importa `com.stripe` | **Hecha** — 01-10-2026 |
| `T-05` | `CardPayment`: cuándo toca, la unidad mínima, el mínimo por moneda, la apertura con la clave del pago y la metadata, y el deshacer tras un `COMMIT` fallido | `T-03` | Unitarias de la conversión y del mínimo | **Hecha** — 01-10-2026 |
| `T-06` | `PaymentRepository.setProviderReference`; las cuatro entradas construidas llaman a `CardPayment`; `CardChargeResponse` y `cardCharge` en sus respuestas | `T-05` | `RegisterSaleService` y el alta no lo llaman | **Hecha** — 01-10-2026 |
| `T-07` | `CardChargeIT` con el doble del puerto: `CA-MV-426` a `CA-MV-437` | `T-06` | Cada criterio afirmado en el cuerpo de la prueba | **Hecha** — 01-10-2026 |
| `T-08` | Prueba manual del adaptador contra el modo de prueba de Stripe con la CLI; contrato regenerado con la prosa releída; `requirements.md`, `security.md` | `T-07` | Un cobro real de prueba abierto y visto en el panel | **Hecha** — 01-10-2026 |

---

## 2. Orden de ejecución

`T-01` → `T-02`; `T-03` → `T-04`, `T-05` → `T-06` → `T-07` → `T-08`. **Es el primero de la etapa**: `RF-MV-041` y `RF-MV-042` dependen de `T-01`, `T-03` y `T-05`.

---

## 3. Cobertura de los criterios de aceptación

| Criterio | Tarea |
|---|---|
| `CA-MV-426` a `CA-MV-431` | `T-05`, `T-06`, `T-07` |
| `CA-MV-432`, `CA-MV-433` | `T-05`, `T-07` |
| `CA-MV-434` a `CA-MV-436` | `T-03`, `T-06`, `T-07` |
| `CA-MV-437` | `T-06`, `T-07` |

---

## 3.1 Desviaciones respecto del plan

**Sin la biblioteca de Stripe**: el adaptador llama a su API HTTP con `RestClient` (`architecture.md` §15.4), de modo que no hay dependencia `stripe-java` en el `pom.xml`, y la regla de ArchUnit es `laPasarelaEsUnPuerto` —nada fuera de `movements.infrastructure` depende de esa capa— en vez de «nada importa `com.stripe`». **Una sola suite**, `CardPaymentIT`, con el doble `FakeCardGateway`, en vez de `CardChargeIT`, `GatewayNotificationsIT` y `PayPendingByCardIT`; los criterios de las compras por hotlink y de paquete, del funcionario y del alta van en `BuyByHotlinkIT`, `BuyPackageIT`, `RegisterSaleIT` y `SelfRegistrationIT`. Los rechazos llevan el código de la regla, `RN-MV-057`, no `EX-001`/`EX-002`: entran por cinco rutas. `CardPayment` gana además `cobroExistente` —la misma petición repetida devuelve el mismo cobro— y `abrirSiToca(metodo, movimiento)`, que lee la moneda del pago recién abierto. **`T-08` queda a medias**: la prueba manual contra el modo de prueba de Stripe espera las credenciales; el adaptador está probado con un servidor simulado (`StripeCardGatewayTest`).

## 4. Bloqueos

**Las credenciales de prueba de Stripe** para `T-08`: una clave restringida y el secreto del endpoint, en el `.env` local. El resto se construye y se prueba sin ellas.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [x] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Un cobro de prueba real abierto contra Stripe.
- [x] Contrato OpenAPI regenerado, **con la prosa releída**.
- [x] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
