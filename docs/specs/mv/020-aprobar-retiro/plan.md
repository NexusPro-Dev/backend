# PLAN — `RF-MV-020` Aprobar un retiro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-020` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 26-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 26-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Es `RF-MV-003` para un retiro**: la transición condicionada —`UPDATE movements SET status = 'CONFIRMADA', confirmed_at = :at WHERE id = :id AND status = 'PENDIENTE' AND movement_type_id = <RETIRO>`— y, si acierta, en la misma transacción, el pago y el evento `APROBACION` por el `Ledger` de `RF-MV-019`. **La transición va primero** y es la que resuelve la carrera con negar (`FA-001`): quien pierde no toca ningún saldo.

El pago se inserta **ya confirmado**, con `idempotency_key = IdempotencyKey.generada()` —nadie la manda: es una operación de administración sin reintento del cliente— y `provider_reference` con la referencia.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir**:

| Elemento | Definición | Por qué |
|---|---|---|
| `payment_methods` | Fila `MANUAL`, «Pago registrado a mano», `is_active = true`, `visibility = 'INTERNO'`, con identificador literal de la serie de MV | `requirements/mv.md` §7.4. `RF-MV-009` no la devuelve (`RN-MV-023`) |
| Permiso | `movements:approve-withdrawal`, a `SUPERADMIN` y `ADMIN` explícito | Pagar es tarea de administración |

La migración termina con un `DO $$` que levanta excepción si `MANUAL` no quedó, como `V78` con `GRATIS`: el servicio la busca por código y **no** admite que falte.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `ApproveWithdrawalRequest` | Nuevo | `providerReference`, nulable |
| `domain/models` | `ProviderReference` | Nuevo | Recortada, en blanco = ausente, hasta 120 |
| `domain/repository` | `JpaMovementRepository` | Gana `confirmWithdrawalIfPending` | |
| `domain/service` | `ApproveWithdrawalService` | Nuevo | Referencia, transición, pago, `Ledger`, auditoría |
| `application` | `WithdrawalResponse` | Gana `payments` | La misma forma de pago que `RF-MV-018` |
| `interfaces` | `MovementController` | `POST /{id}/withdrawal-approval` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/withdrawal-approval` | `movements:approve-withdrawal` |

**Una acción con nombre**, como `…/confirmation`: **no** se reutiliza `…/confirmation` para los retiros porque son dos permisos y dos operaciones (`RN-SEG-014`). **Cuerpo**: `{ "providerReference": "…" }` o `{}`.

| Código | Cuándo |
|---|---|
| `200` | Aprobado, con el retiro y su pago |
| `400` | Identificador malformado o referencia demasiado larga |
| `401` / `403` | Sin token / sin `movements:approve-withdrawal` |
| `404` | No existe o no es un retiro |
| `409` | No está pendiente, con el estado en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:approve-withdrawal')")`.

---

## 6. Auditoría

Un `ChangeEvent` sobre `movements`, `UPDATE`, `PENDIENTE` → `CONFIRMADA`, que cita el pago y el evento `APROBACION`.

---

## 7. Transaccionalidad

`@Transactional`: transición, pago y dos asientos. El `Ledger` bloquea en orden. Si ganó negar, la transición toca cero filas y no se escribe nada más.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar `POST /{id}/confirmation` | Dos operaciones, un permiso: `RN-SEG-014` |
| Aprobar sin escribir un pago | El día de la pasarela habría que inventar dónde vive la referencia; hoy ya tiene sitio |
| Hacer obligatoria la referencia | No siempre la hay (`spec.md` §2.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Mover lo retenido dos veces | La transición primero; `CA-MV-239` y `CA-MV-241` |
| Aprobar una venta por esta ruta | El tipo en la sentencia; `CA-MV-242` |

---

## 11. Estrategia de prueba

Integración, `ApproveWithdrawalIT`: `CA-MV-236` a `CA-MV-243`, con dos hilos —aprobar y negar— en `CA-MV-241`.
