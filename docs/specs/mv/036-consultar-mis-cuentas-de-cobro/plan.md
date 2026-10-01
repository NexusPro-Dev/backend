# PLAN — `RF-MV-036` Consultar mis cuentas de cobro

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-036` |
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

**Una sentencia** sobre `payout_accounts` unida a `payout_institutions`, con `user_id = actor AND deleted_at IS NULL` y `ORDER BY is_principal DESC, created_at DESC, id DESC`, más **una lectura del titular** por `PayoutHolderLookup` (`RF-MV-035`): es la misma persona en todas las filas, de modo que son **dos sentencias** en total, tenga las cuentas que tenga.

**«Sirve para retirar» es `payout_institutions.is_active`**, publicado como `usable`. Hoy es la única condición; si mañana hay otra —una cuenta bloqueada, una moneda—, se suma aquí y la respuesta no cambia de forma.

---

## 2. Cambios de esquema

Ninguno: `ix_payout_accounts_user` lo trae `RF-MV-032`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `PayoutAccountRepository` | `liveOf` devuelve la proyección con la entidad | La de `RF-MV-035` |
| `domain/service` | `PayoutAccountService` | Gana `listMine` | |
| `application` | `PayoutAccountResponse` | Gana `usable` | Lo devuelven también `RF-MV-035` y `RF-MV-037`, con el mismo valor |
| `interfaces` | `PayoutAccountController` | Gana `GET` | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine/payout-accounts` | `movements:list-own-payout-accounts` |

**Respuesta**: `200` con un arreglo de `PayoutAccountResponse`, sin envoltorio de página.

| Código | Cuándo |
|---|---|
| `200` | La lista, quizá vacía |
| `401` / `403` | Sin token / sin `movements:list-own-payout-accounts` |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:list-own-payout-accounts')")`; el dueño es el actor.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

**`SP`**: la lectura del titular por `PayoutHolderLookup`. **El frontend**: la pantalla «mis cuentas» y el selector del retiro. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Incluir las dadas de baja con su fecha | No se pueden usar ni editar (`spec.md` §2.1) |
| Dejar fuera las de entidades inactivas | La persona no sabría por qué desapareció su cuenta, ni que tiene que registrar otra |
| Enmascarar el número | Es suyo, y lo necesita para reconocerla |

---

## 10. Riesgos

Ninguno propio.

---

## 11. Estrategia de prueba

Integración, `ListMyPayoutAccountsIT`: `CA-MV-390` a `CA-MV-394`, con cuentas de dos personas, una dada de baja y una de entidad inactiva. `CA-MV-393` corrige el documento por SQL y vuelve a consultar. **Cuenta de sentencias** con las estadísticas de Hibernate: dos, con una o con cinco cuentas.
