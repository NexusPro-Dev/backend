# PLAN — `RF-MV-051` Pagar por la pasarela local un pago pendiente propio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-051` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

---

## 1. Enfoque

**El de `RF-MV-042`**: `LocalPayment.retomar(movimiento, actor)` bloquea el pago pendiente propio (`PaymentRepository.lockPendingOwn`), exige que su método lo cobre `PAYRETAILERS`, y abre el cobro si no lo tiene o devuelve el que tiene. Para devolver el mismo, **se guarda la dirección de la página** junto a la referencia: PayRetailers la da al abrir, y preguntarla de nuevo sería otra llamada.

---

## 2. Cambios de esquema

**`payments.checkout_url`** `varchar(500) NULL`, en `V69` de `RF-MV-048`: la dirección de la página de pago del cobro abierto. Se añade a `ck_payments_cobro_local` como cuarta columna del grupo.

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/service` | `LocalPayment` | `retomar(movimiento, actor)` |
| `interfaces` | `LocalChargeController` | `POST /movements/mine/{id}/local-charge` |
| `application` | `LocalChargeResponse` | El de `RF-MV-048` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/{id}/local-charge` | `movements:pay-pending-locally` |

| Código | Cuándo |
|---|---|
| `200` | El cobro, abierto ahora o ya abierto |
| `404` | No es una compra propia (`EX-001`) |
| `409` | Sin pago pendiente con `PSE` (`EX-002`) o sin conversión (`EX-003`) |
| `503` | Pasarela apagada o sin respuesta (`EX-004`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:pay-pending-locally')")`, y la compra se filtra por quien pide.

---

## 6. Auditoría

La del pago, cuando se abre el cobro.

---

## 7. Transaccionalidad

Como `RF-MV-048`: abrir el cobro dentro de la transacción que escribe el pago.

---

## 8. Impacto sobre otros módulos

**El frontend**: el botón «Pagar» de una compra pendiente con `PSE`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar la ruta de la tarjeta (`RF-MV-042`) con otro método | Dos operaciones en una ruta con respuestas distintas, y un permiso para dos cosas (`RN-SEG-014`) |

---

## 10. Riesgos

Ninguno nuevo.

---

## 11. Estrategia de prueba

Integración, `LocalChargeIT` de `RF-MV-048`: `CA-MV-625` a `CA-MV-629`. `EndpointPermissionsIT` gana la ruta.
