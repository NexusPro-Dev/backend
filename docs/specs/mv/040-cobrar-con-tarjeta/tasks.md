# TASKS — `RF-MV-040` Cobrar con tarjeta por la pasarela

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-040` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| Plan | [`plan.md`](plan.md) v0.1.0 |
| `plan.md` aprobado el | 01-10-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/stripe-tarjeta` |

!!! info "Qué va en este documento"

    **Qué hay que hacer, en qué orden y cómo se comprueba.** Nada de por qué — eso está en `spec.md` y `plan.md`.

---

## 1. Tareas

**Estados:** `Pendiente` · `En curso` · `Hecha` · `Bloqueada`.

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | Migración de la etapa 4 (`plan.md` §2): `gateway` en `payment_methods`, la incidencia de `payments`, `gateway_events`, los índices, el permiso y sus guardas | — | Aplicada sobre la base de desarrollo; las guardas pasan | Pendiente |
| `T-02` | Recuentos del catálogo 180 → 181 (`ADMIN` 178 → 179) en todas las suites que lo cuentan | `T-01` | `grep -rnE "\b180\b\|\b178\b" src/test` sin restos | Pendiente |
| `T-03` | `stripe-java` en el `pom.xml`; `CardGateway`, `StripeCardGateway`, `CardGatewayConfig` (apagado sin credenciales, aviso al arrancar) | — | Arranca con y sin las dos variables | Pendiente |
| `T-04` | `LayerRulesTest.soloElAdaptadorImportaStripe` | `T-03` | Falla si un servicio importa `com.stripe` | Pendiente |
| `T-05` | `CardPayment`: cuándo toca, la unidad mínima, el mínimo por moneda, la apertura con la clave del pago y la metadata, y el deshacer tras un `COMMIT` fallido | `T-03` | Unitarias de la conversión y del mínimo | Pendiente |
| `T-06` | `PaymentRepository.setProviderReference`; las cuatro entradas construidas llaman a `CardPayment`; `CardChargeResponse` y `cardCharge` en sus respuestas | `T-05` | `RegisterSaleService` y el alta no lo llaman | Pendiente |
| `T-07` | `CardChargeIT` con el doble del puerto: `CA-MV-426` a `CA-MV-437` | `T-06` | Cada criterio afirmado en el cuerpo de la prueba | Pendiente |
| `T-08` | Prueba manual del adaptador contra el modo de prueba de Stripe con la CLI; contrato regenerado con la prosa releída; `requirements.md`, `security.md` | `T-07` | Un cobro real de prueba abierto y visto en el panel | Pendiente |

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

## 4. Bloqueos

**Las credenciales de prueba de Stripe** para `T-08`: una clave restringida y el secreto del endpoint, en el `.env` local. El resto se construye y se prueba sin ellas.

---

## 5. Definición de terminado

- [ ] `./mvnw clean verify` en verde.
- [ ] Los doce criterios de aceptación con prueba, **cada uno afirmado en el cuerpo de la prueba**.
- [ ] Un cobro de prueba real abierto contra Stripe.
- [ ] Contrato OpenAPI regenerado, **con la prosa releída**.
- [ ] `requirements.md` y `security.md` actualizados.
- [ ] **`tasks.md` aprobadas por el responsable del proyecto.**
