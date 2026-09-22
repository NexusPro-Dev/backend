# PLAN — `RF-MV-005` Anular una venta pendiente

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-005` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 17-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Es `RF-MV-003` sin la entrega.** La misma transición condicionada al estado anterior —`UPDATE … WHERE status = 'PENDIENTE'`, cuya cuenta de filas decide— con dos columnas más en la misma sentencia, y ningún recorrido de líneas: una pendiente no concedió nada y no hay nada que deshacer. Todo lo que `RF-MV-003` · `plan.md` §1 argumenta sobre la transición vale aquí, y no se repite.

**El motivo se valida antes de tocar la venta**, como el de una eliminación (`RF-PM-006`): un motivo vacío no cuesta ni una consulta, y el Art. V.13 exige rechazar antes de ejecutar.

---

## 2. Cambios de esquema

**`V17__mv_anulacion.sql`**, sobre `movements`:

| Columna | Definición | Por qué |
|---|---|---|
| `voided_at` | `timestamptz NULL` | Cuándo se anuló |
| `void_reason` | `varchar(500) NULL` | Por qué no debía existir, escrito para una persona. Quinientos, como el motivo de una eliminación |
| `ck_movements_voided` | `(status = 'ANULADA') = (voided_at IS NOT NULL AND void_reason IS NOT NULL)` | Ata las dos al estado, como `ck_movements_confirmed`: una anulada sin motivo o una pendiente con fecha de anulación son estados que el código puede escribir y el negocio no admite |

**Columnas propias y no un `resolved_at` genérico para rechazar y anular.** Un genérico obligaría a decidir hoy si rechazar lleva motivo, y eso es de `RF-MV-004`. Dos columnas con nombre dicen exactamente qué pasó, y el `CHECK` queda simple.

**`RN-MV-001` no se rompe**: como con `confirmed_at`, lo que se escribe es **la resolución** de la venta, no la venta.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `VoidSaleRequest` | Nuevo | `reason` |
| `domain/models` | `VoidReason` | Nuevo | Con contenido y acotado; la misma forma que `DeletionReason` de `PM` |
| `domain/repository` | `MovementRepository` | Gana `voidIfPending`; el detalle proyecta las dos columnas | |
| `domain/service` | `VoidSaleService` | Nuevo | Motivo, transición, auditoría, respuesta |
| `application` | `SaleResponse` | `voidedAt` y `voidReason`, nulables con `types` | El detalle propio y de administración los llevan solos |
| `interfaces` | `MovementController` | `POST /{id}/voiding` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/{id}/voiding` | `movements:void` |

**Misma forma que `…/confirmation`**, por lo mismo: una acción con nombre y con su permiso. `RF-MV-004` será `…/rejection`.

**Cuerpo**: `{ "reason": "…" }`. Por `POST` y no en la URL, por lo que `DeleteProductRequest` ya dejó escrito: en la *query string* el motivo acabaría en los registros de acceso de cualquier proxy.

**Respuesta**: `200` con `SaleResponse`, con `voidedAt` y `voidReason` (nulos en toda venta no anulada).

| Código | Cuándo |
|---|---|
| `200` | Anulada |
| `400` | Identificador malformado, motivo vacío (`VAL-002`) o demasiado largo (`VAL-003`) |
| `401` / `403` | Sin token / sin `movements:void` |
| `404` | No existe |
| `409` | No está pendiente, con el estado en el mensaje |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:void')")`. **`CA-MV-115` se ejercita con `movements:confirm` puesto**: es la prueba de que los dos permisos son dos.

---

## 6. Auditoría

Un `ChangeEvent` de `MV` sobre `movements`, `UPDATE`: `before {status: PENDIENTE}`, `after {status: ANULADA, voided_at, void_reason}`. **No es un evento de eliminación**: nada se borra.

---

## 7. Transaccionalidad

`@Transactional`; una sentencia y el asiento. Frente a una confirmación simultánea, el bloqueo de fila decide quién gana (`FA-002`).

---

## 8. Impacto sobre otros módulos

Ninguno. `RF-MV-014` ya muestra `ANULADO` sin cambios.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| El motivo solo en la auditoría, sin columna | Quien mire la venta tendría que buscar en otro registro por qué no existe. `spec.md` §2 |
| `resolved_at` / `resolution_note` compartidos con rechazar | Decidiría por `RF-MV-004` si lleva motivo |
| `DELETE /movements/{id}` | Anular no es borrar, y `DELETE` con cuerpo no tiene semántica (RFC 9110) |
| Que el comprador pueda anular lo suyo | Otro requerimiento, otras preguntas (`spec.md` §2.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Anular una confirmada | La transición condicionada; `CA-MV-112` comprueba que la membresía sigue |
| Que los dos permisos se confundan | `CA-MV-115` con `movements:confirm` |
| Un motivo vacío que toque la venta | Se valida primero; `CA-MV-114` |

---

## 11. Estrategia de prueba

Integración, `VoidSaleIT`: transición y motivo, segunda anulación, confirmada con membresía intacta, inexistente, motivo vacío y largo, permisos (incluido el de confirmar), líneas y `RF-MV-014`, auditoría, detalle.
