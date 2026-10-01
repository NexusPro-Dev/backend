# PLAN — `RF-MV-037` Editar una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-037` |
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

**El mismo bloqueo por persona que `RF-MV-035`** (`plan.md` §1), tomado antes de leer. Después: la cuenta por `id AND user_id = actor AND deleted_at IS NULL` —una cuenta ajena y una inexistente dan la misma fila vacía, y por eso el mismo `404`—, su entidad, la validación de lo pedido **sobre el resultado** (`PayoutAccount.editar`) y, si algo cambia, el desmarcado de la anterior principal y el `UPDATE`. `uq_payout_accounts_numero` y `uq_payout_accounts_principal` siguen siendo la segunda defensa, como al registrar.

**La copia del retiro no se toca porque no hay nada que tocar**: vive en otra tabla, sin claves que la sigan (`requirements/mv.md` §7.13).

---

## 2. Cambios de esquema

Ninguno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `PayoutAccount` | Gana `editar(tipo, numero, principal)` | Devuelve si cambió algo; reutiliza `VAL-004` de `registrar` |
| `domain/repository` | `PayoutAccountRepository` | Gana `findLiveOwn(id, userId)` y `update` | |
| `domain/service` | `PayoutAccountService` | Gana `update` | `FA-001`, auditoría con lo anterior |
| `application` | `PayoutAccountRequests.Update` | Nuevo | `{ accountType?, number?, principal? }`; **sin** `institutionId` |
| `interfaces` | `PayoutAccountController` | Gana `PATCH /{id}` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `PATCH` | `/api/v1/movements/mine/payout-accounts/{id}` | `movements:update-own-payout-account` |

**Respuesta**: `200` con `PayoutAccountResponse`.

| Código | Cuándo |
|---|---|
| `200` | Editada, o sin cambios (`FA-001`) |
| `400` | `EX-001` o `EX-004`; la entidad en el cuerpo es propiedad desconocida |
| `401` / `403` | Sin token / sin `movements:update-own-payout-account` |
| `404` | La cuenta no es suya, no existe o está dada de baja (`EX-002`) |
| `409` | Entidad inactiva (`EX-003`) o número repetido (`EX-005`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:update-own-payout-account')")`; la propiedad la comprueba la sentencia.

---

## 6. Auditoría

Un `ChangeEvent` sobre `payout_accounts`, `UPDATE`, con los campos que cambiaron, antes y después, **el número enmascarado** (`RF-MV-035` · `plan.md` §6). Si desmarcó otra, un segundo `UPDATE` sobre esa.

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo, las lecturas, el desmarcado y el `UPDATE`.

---

## 8. Impacto sobre otros módulos

**El frontend**: la pantalla «mis cuentas». **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una ruta propia para marcar la principal | Un permiso más para un dato de la misma fila; `RN-SEG-014` pide uno por operación, y editar la cuenta es una |
| Permitir cambiar la entidad | Cambiaría también qué datos se piden; registrar otra es más claro |
| `403` para la cuenta ajena | Confirmaría que existe; es lo que `RN-MV-055` evita |

---

## 10. Riesgos

Ninguno propio: los de `RF-MV-035`.

---

## 11. Estrategia de prueba

Integración, `EditPayoutAccountIT`: `CA-MV-395` a `CA-MV-403`. `CA-MV-402` necesita un retiro con destino: se escribe **después** de la enmienda de `RF-MV-019`.
