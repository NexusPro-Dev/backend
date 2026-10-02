# PLAN — `RF-MV-039` Consultar las cuentas de cobro de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-039` |
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

**La misma sentencia que `RF-MV-036`** con el dueño como parámetro y `deleted_at IS NULL` como condición opcional, ordenada por `deleted_at IS NOT NULL, is_principal DESC, coalesce(deleted_at, created_at) DESC, id DESC`, más la lectura del titular por `PayoutHolderLookup`. **Que la persona exista** lo responde esa misma lectura: vacío es `404`. Dos sentencias.

---

## 2. Cambios de esquema

Ninguno.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `PayoutAccountRepository` | Gana `of(userId, incluirBajas)` | `liveOf` pasa a ser `of(userId, false)` |
| `domain/service` | `PayoutAccountService` | Gana `listOf` | |
| `application` | `PayoutAccountResponse` | Gana `deletedAt` | Nulo siempre en las rutas propias |
| `interfaces` | `PayoutAccountController` | Gana `GET /movements/users/{userId}/payout-accounts` | El controlador es uno: las rutas comparten la respuesta |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/users/{userId}/payout-accounts` | `movements:read-user-payout-accounts` |

**Parámetro**: `includeDeleted` (`true` · `false`, por omisión `false`). **Respuesta**: `200` con un arreglo de `PayoutAccountResponse`.

| Código | Cuándo |
|---|---|
| `200` | La lista, quizá vacía |
| `400` | Filtro o identificador malformado (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:read-user-payout-accounts` |
| `404` | La persona no existe o está eliminada (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:read-user-payout-accounts')")`. **No hay excepción para lo propio** (`CA-MV-413`): es otra operación, y lo propio tiene la suya.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

**`SP`**: la lectura del titular. **El frontend**: la ficha de una persona en administración. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un filtro `userId` en la ruta propia | Una ruta con dos permisos según el parámetro; `RN-SEG-014` los quiere en dos operaciones |
| Ponerla bajo `/users/{id}`, en `SP` | Las cuentas de cobro son de `MV`; `SP` tendría que leerlas de otro módulo |
| Alcance por estructura comercial | Es lectura de administración (`spec.md` §2.1) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que el permiso se dé por tipo de rol por error | La migración lo da solo a `SUPERADMIN` y `ADMIN`, y su guarda lo comprueba |

---

## 11. Estrategia de prueba

Integración, `ListUserPayoutAccountsIT`: `CA-MV-410` a `CA-MV-414`, con un usuario `CONSUMIDOR` en `CA-MV-413`.
