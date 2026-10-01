# PLAN — `RF-MV-038` Dar de baja una cuenta de cobro propia

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-038` |
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

**El bloqueo por persona de `RF-MV-035`**, la lectura de la cuenta propia viva (`findLiveOwn`, de `RF-MV-037`) y dos sentencias: `UPDATE … SET deleted_at = now(), is_principal = false, updated_at = now()` y, si era la principal, `UPDATE … SET is_principal = true WHERE id = (la viva más antigua de esa persona)`. **El orden importa**: primero se quita la marca y después se pone, o `uq_payout_accounts_principal` vería dos a la vez. `ck_payout_accounts_baja` impide que la sentencia olvide quitarla.

**«La más antigua»** es `ORDER BY created_at, id LIMIT 1`, con el identificador como desempate.

---

## 2. Cambios de esquema

Ninguno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `PayoutAccountRepository` | Gana `softDelete(id)` y `promoteOldest(userId)` | |
| `domain/service` | `PayoutAccountService` | Gana `delete` | |
| `interfaces` | `PayoutAccountController` | Gana `DELETE /{id}` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `DELETE` | `/api/v1/movements/mine/payout-accounts/{id}` | `movements:delete-own-payout-account` |

| Código | Cuándo |
|---|---|
| `204` | Dada de baja |
| `401` / `403` | Sin token / sin `movements:delete-own-payout-account` |
| `404` | La cuenta no es suya, no existe o ya está dada de baja (`EX-001`) |

**Repetir la petición responde `404`** y no `204`: la segunda ya no encuentra una cuenta viva. Es lo que hacen las demás bajas lógicas del proyecto.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:delete-own-payout-account')")`; la propiedad la comprueba la sentencia.

---

## 6. Auditoría

Un `ChangeEvent` sobre `payout_accounts`, `DELETE` lógico, con la entidad y el número enmascarado. Si promovió otra, un `UPDATE` sobre esa.

---

## 7. Transaccionalidad

`@Transactional`: el bloqueo, la lectura y las dos sentencias.

---

## 8. Impacto sobre otros módulos

**El frontend**: la pantalla «mis cuentas». **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Borrado físico | `withdrawal_destinations.payout_account_id` apunta a ella; los retiros perderían la referencia de qué cuenta usaron |
| Rechazar la baja con un retiro pendiente | El responsable decidió permitirla: el retiro tiene su copia |
| Dejar sin principal a quien le quedan cuentas | Pedir un retiro sin indicar cuenta fallaría teniendo cuentas (`RN-MV-056`) |
| Promover la más reciente | La más antigua lleva más tiempo en uso; cualquiera de las dos es defendible, y se eligió una |

---

## 10. Riesgos

Ninguno propio.

---

## 11. Estrategia de prueba

Integración, `DeletePayoutAccountIT`: `CA-MV-404` a `CA-MV-409`. `CA-MV-406` y `CA-MV-407` necesitan el retiro con destino: se escriben **después** de la enmienda de `RF-MV-019`.
