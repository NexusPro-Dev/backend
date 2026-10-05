# PLAN — `RF-MV-048` Cobrar por la pasarela local

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-048` |
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

**El de la tarjeta, con un puerto propio.** Las entradas ya llaman a `CardPayment.abrirSiToca` tras registrar el pago; ganan una llamada gemela a **`LocalPayment.abrirSiToca`**, que actúa solo si el método lo cobra `PAYRETAILERS` y la pasarela está encendida. `LocalPayment` busca la conversión vigente del país de quien paga (`CountryConversionRateRepository.current`, de `RF-MV-046`), calcula el importe, llama a **`LocalPaymentGateway.open`** y escribe en el pago la referencia, la moneda local, el importe convertido y la conversión.

**`CardPayment` deja de mirar solo si hay pasarela**: hoy trata como tarjeta **cualquier** método con `gateway`, y desde esta etapa `PSE` lo tiene. Pasa a exigir `gateway = 'STRIPE'` (`CA-MV-609`). Es el cambio que no se ve y que rompería la tarjeta si se olvidara.

**Este requerimiento carga la migración de la etapa**, como `RF-MV-040` cargó `V62`.

---

## 2. Cambios de esquema

`V69__mv_pasarela_local.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `payments.charge_currency_id`, `charge_amount`, `conversion_rate_id` | `uuid NULL` → `currencies` `RESTRICT`; `bigint NULL` en centésimas; `uuid NULL` → `country_conversion_rates` `RESTRICT` | `RN-MV-063`, [`requirements/mv.md` §7.7](../../../requirements/mv.md) |
| `ck_payments_cobro_local` | Las tres nulas o las tres presentes, y `charge_amount > 0` | `requirements/mv.md` §7.6 |
| `ck_payments_incident` | **Se sustituye**: añade `COBRO_TARDIO` | `RN-MV-064` |
| `ck_payment_methods_gateway` | **Se sustituye**: añade `PAYRETAILERS` | `requirements/mv.md` §7.6 |
| `UPDATE payment_methods SET gateway = 'PAYRETAILERS' WHERE code = 'PSE'` | | `requirements/mv.md` §4.10 |
| `movements:pay-pending-locally` | Por tipo de rol (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`), como `pay-pending-by-card` | `RF-MV-051` |
| `ix_payments_cobro_local_pendiente` | Parcial: `(occurred_at)` donde `status = 'PENDIENTE' AND provider_reference IS NOT NULL AND charge_currency_id IS NOT NULL` | El barrido de `RF-MV-050` |

**Los `PSE` pendientes que ya existen no se tocan**: no tienen cobro abierto, y una persona los sigue confirmando (`CA-MV-608`). Guardas al final, como `V62`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `LocalPaymentGateway` | Nuevo puerto | `enabled()`, `name()`, `open(LocalChargeOrder)`, `retrieve(reference)` (`architecture.md` §15.5) |
| `domain/service` | `LocalPayment` | Nuevo | `abrirSiToca(metodo, movimiento, persona)`, `cobroExistente(movimiento)`; el cálculo hacia arriba con `RoundingMode.CEILING` a escala cero de la moneda local |
| `domain/service` | `CardPayment` | `cobraLaPasarela` exige `STRIPE` | `CA-MV-609` |
| `domain/service` | `RegisterSaleService`, `BuyPackageService`, `RetryPaymentService`, `PointsPurchaseService` | Llaman también a `LocalPayment` | Las cuatro entradas del §4.1 de la spec |
| `domain/service` | `ConfirmPaymentService`, `RejectPaymentService` | El cobro abierto de la pasarela local también los cierra (`RN-MV-058`) | Como con la tarjeta |
| `domain/repository` | `PaymentRepository` | `setLocalCharge(pago, referencia, moneda, importe, conversión)` | |
| `infrastructure` | `PayRetailersGateway`, `PayRetailersSettings` | Nuevos | `POST /transactions` con `trackingId` = el pago, `notificationUrl`, `returnUrl`, `testMode` y los datos de la persona; `RestClient`; cabecera y Basic |
| `application` | `LocalChargeResponse` | Nuevo | `paymentId`, `gateway`, `checkoutUrl`, `currency`, `amount` |
| `application` | `SaleResponse`, `PurchaseResponse`, `PointsPurchaseResponse` | Ganan `localCharge`, presente y nulo si no hay | Como `cardCharge` |

**La persona** —nombre, correo, documento, teléfono, país— la lee `LocalPayment` por `UserCatalog`, el puerto de `SP` que `MV` ya consume. Se envía lo que hay; lo que la pasarela exija y falte lo rechaza ella (`EX-003`).

---

## 4. Contrato de API

**Ninguna ruta nueva.** Las respuestas de las entradas ganan **`localCharge`**: `{ paymentId, gateway: "PAYRETAILERS", checkoutUrl, currency: { id, code }, amount }`, presente y nulo cuando no hay cobro local.

| Código | Cuándo |
|---|---|
| `409` | Sin conversión vigente para el país de quien paga (`EX-001`) |
| `503` | La pasarela no respondió (`EX-002`) |
| `422` | La pasarela rechazó el cobro (`EX-003`) |

**Se releen las `@Operation`** de las cinco entradas: la de la tarjeta ya explica `cardCharge`, y cada una gana un párrafo para `localCharge`.

---

## 5. Autorización

La de cada entrada. Ningún permiso nuevo.

---

## 6. Auditoría

El pago ya se audita al registrarse; **el cobro local se añade a esa misma instantánea**: referencia, moneda, importe convertido y conversión.

---

## 7. Transaccionalidad

**Abrir el cobro ocurre dentro de la transacción del registro**, como con la tarjeta (`architecture.md` §15.4 y §15.5): si la pasarela falla, se revierte todo. **Sin cancelación al revertir**: PayRetailers no publica cómo cancelar un cobro, y un cobro abierto sin pago que lo respalde **no se confirma nunca** —el aviso y el barrido no encuentran pago— y caduca solo. Se registra en el log.

---

## 8. Impacto sobre otros módulos

**`SP`**: se lee la persona por `UserCatalog`. **El frontend**: tras la compra, si llega `localCharge`, lleva al cliente a `checkoutUrl`; la vuelta la recibe `PAYRETAILERS_RETURN_URL`, que **no confirma nada**: la app consulta la compra hasta verla confirmada o rechazada.

**Enmiendas a otras tripletas**, con una fila en su control de cambios: `RF-MV-002`, `RF-MV-011`, `RF-MV-012`, `RF-MV-013`, `RF-MV-018` y `RF-MV-027` (abren el cobro local), `RF-MV-044` y `RF-MV-045` (no alcanzan un cobro local abierto) y `RF-MV-005` (anular, §9 de `RF-MV-049`).

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar `CardGateway` | Cobro en la app con secreto de cliente y notificación firmada, frente a página externa y aviso sin firma (`architecture.md` §15.5) |
| Un método por cada método local | El sistema no ve cuál eligió el cliente hasta que la pasarela lo dice (`requirements/mv.md` §4.10) |
| Convertir con la tasa de `SP` | Una sola tasa y para mostrar; la conversión por país tiene precio de cobro (`RN-MV-062`) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que la tarjeta empiece a cobrar `PSE` | `CardPayment` exige `STRIPE` (`CA-MV-609`) |
| Que la forma exacta del cuerpo de PayRetailers difiera de la documentada | El adaptador es el único sitio que la conoce; se ajusta con el sandbox sin tocar el dominio |
| Cobros abiertos que quedan huérfanos al revertirse una transacción | No se confirman nunca (§7); se registran en el log |

---

## 11. Estrategia de prueba

Integración, `LocalChargeIT`, con un doble de `LocalPaymentGateway`: `CA-MV-600` a `CA-MV-610`. `CA-MV-609` reutiliza el doble de `CardGateway` de `CardPaymentIT`. Unitarias del redondeo hacia arriba. **Ninguna prueba llama a PayRetailers.**
