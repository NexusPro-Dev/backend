# PLAN — `RF-MV-042` Pagar con tarjeta un pago pendiente propio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-042` |
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

**El pago pendiente se lee con su fila bloqueada** (`FOR UPDATE`), junto con su movimiento, su método y su moneda, filtrando por `movements.user_id = actor` y tipo `VENTA` o `COMPRA_PUNTOS`. **Con `provider_reference`**: `CardGateway.retrieve` devuelve el estado y el secreto; si el cobro está `canceled`, `EX-002`. **Sin ella**: `CardPayment.abrir` de `RF-MV-040`, con su deshacer. El bloqueo es lo que hace que dos peticiones simultáneas abran **un** cobro (`spec.md` §13): la segunda espera y lee la referencia ya escrita.

---

## 2. Cambios de esquema

Ninguno. El permiso lo trae la migración de `RF-MV-040`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `PaymentRepository` | Gana `lockPendingOwn(movimiento, actor)` | Con el método, la pasarela, el importe, la moneda y la referencia |
| `domain/service` | `CardPayment` | Gana `retomar(movimiento)` | Reutiliza la apertura de `RF-MV-040` |
| `domain/service` | `CardGateway.retrieve` | Del puerto de `RF-MV-040` | `Charge(reference, clientSecret, status)` |
| `interfaces` | `MovementController` (o el de la etapa) | `POST /movements/mine/{id}/card-charge` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/mine/{id}/card-charge` | `movements:pay-pending-by-card` |

**Sin cuerpo.** **`POST` y no `GET`** aunque a veces solo lea: cuando no hay cobro, **lo abre**, y un `GET` que crea cosas en un tercero sería una sorpresa para cualquier caché o reintento.

| Código | Cuándo |
|---|---|
| `200` | El cobro, con su secreto (`CardCharge`) |
| `401` / `403` | Sin token / sin `movements:pay-pending-by-card` |
| `404` | No existe, no es suyo o no es venta ni compra de puntos (`EX-001`) |
| `409` | No hay pago pendiente con tarjeta, o el cobro está cancelado (`EX-002`) |
| `422` | Por debajo del mínimo de la pasarela (`EX-004`) |
| `503` | Pasarela apagada o sin responder (`EX-003`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:pay-pending-by-card')")`; el dueño es el actor.

---

## 6. Auditoría

Abrir el cobro audita el pago con su `provider_reference`, como `RF-MV-040`. **Retomarlo no audita**: no cambia nada.

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo, y la apertura con su deshacer.

---

## 8. Impacto sobre otros módulos

**El frontend**: el botón «pagar» de una compra pendiente con tarjeta, en «mis compras» y en «mis compras de puntos». **El contrato OpenAPI se regenera y la prosa se relee.**

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Guardar el secreto y devolverlo en el detalle | El responsable no pidió guardarlo, y es un dato que permite completar el pago |
| Usar volver a pagar (`RF-MV-018`) para esto | Exige que no haya pago pendiente, y aquí lo hay: abriría otro y cerraría el que alguien podría estar pagando |
| `GET` | Crea un cobro en un tercero (§4) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Dos cobros para un pago | El bloqueo de la fila y la clave del pago en la pasarela |

---

## 11. Estrategia de prueba

Integración, `PayPendingByCardIT`, con el doble del puerto: `CA-MV-453` a `CA-MV-461`. `CA-MV-454` registra la venta por `RF-MV-001`. **El recuento del catálogo** lo cubre `RF-MV-040` · `T-02`.
