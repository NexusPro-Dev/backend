# PLAN — `RF-CM-019` Registrar la comisión afftrack de una persona

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-019` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 29-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 29-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica es la de [`RF-CM-006`](../006-registrar-tasa-personalizada/plan.md) —el no solapamiento en un `EXCLUDE` traducido por estado SQL, el fin de vigencia que se vacía con presencia explícita— combinada con la de [`RF-CM-015`](../015-registrar-comision-afftrack-rol/plan.md) para el producto FTD y el importe.

---

## 1. Enfoque

**El `EXCLUDE` lleva el límite dentro** —`ex_user_afftrack_rates_vigente` sobre persona, producto, límite y rango de fechas—, y es la garantía. El caso de uso comprueba antes, solo para el mensaje. **La violación no trae nombre de restricción**: el adaptador traduce `23P01` y `40P01` a `409`, como `JpaUserCommissionRateRepository` (`RN-CM-006`).

**El filtro «vigentes en»** es el mismo predicado que usará el cierre para resolver la escala (`RN-CM-039`): `valid_from <= :fecha AND (valid_to IS NULL OR valid_to >= :fecha) AND deleted_at IS NULL`. **Se escribe una vez**, en el repositorio, y lo usan el listado y `RF-CM-020`: es lo que hace verdadero `CA-CM-236`.

---

## 2. Cambios de esquema

**Ninguno**: `V54` (`RF-CM-015`), con `ex_user_afftrack_rates_vigente` e `ix_user_afftrack_rates_user_product`.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio |
|---|---|---|---|
| `CM` | `domain/models` | `UserAfftrackRate` | Nuevo: `create`, `corregir`, `retirar`, `instantanea` |
| `CM` | `domain/repository` | `UserAfftrackRateRepository` y adaptador | Nuevos: `overlaps(...)`, `vigentesEn(userId, productId, fecha)`; traducción de `23P01`/`40P01` |
| `CM` | `domain/repository` | `UserAfftrackRateQueryRepository` y adaptador | Nuevos, con `JOIN` a `users`, `products` y `currencies` |
| `CM` | `domain/service` | `RegisterUserAfftrackRateService`, `ListUserAfftrackRatesService`, `UpdateUserAfftrackRateService`, `DeleteUserAfftrackRateService` | Nuevos |
| `CM` | `application` | Peticiones, `UserAfftrackRateResponse`, `UserAfftrackRateItem`, `UserAfftrackRatePageResponse` | Nuevos, `@Schema(name)` explícito |
| `CM` | `interfaces` | `UserAfftrackRateController` | Nuevo |

**`UserCatalog`** (`SP`) resuelve la persona, como en `RF-CM-006`; **`ProductCatalog.ftdProductIds`** y **`ProductCurrencyScale`** vienen de `RF-CM-015`.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso | Respuesta |
|---|---|---|---|
| `POST` | `/api/v1/user-afftrack-rates` | `user-afftrack-rates:create` | `201` |
| `GET` | `/api/v1/user-afftrack-rates` | `user-afftrack-rates:read` | `200`, página |
| `PATCH` | `/api/v1/user-afftrack-rates/{id}` | `user-afftrack-rates:update` | `200` |
| `POST` | `/api/v1/user-afftrack-rates/{id}/deletion` | `user-afftrack-rates:delete` | `204` |

**Alta:** `userId`, `productId`, `threshold`, `amountPerFtd`, `validFrom`, `validTo`. **Listado:** `userId`, `productId`, `onDate`, `includeDeleted`, `page`, `size`. **Corrección:** `threshold`, `amountPerFtd`, `validTo`; `userId`, `productId` y `validFrom` se rechazan si llegan. **Retiro:** `reason`.

---

## 5. Autorización

Un `@PreAuthorize` por operación con su permiso; las cuatro rutas en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

`CREATE` al registrar, `UPDATE` con antes y después si algo cambió, y `recordDeletion` con motivo al retirar.

---

## 7. Transaccionalidad

`@Transactional` en las escrituras; la corrección y el retiro leen la fila `FOR UPDATE`. **Sin bloqueo consultivo**: la carrera la cierra el `EXCLUDE`.

---

## 8. Impacto sobre otros módulos

Ninguno más allá de lo que trae `RF-CM-015`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una sola tabla de escalones con `user_id` o `role_id` | Mezclaría una tabla con vigencia y otra sin ella, con un `CHECK` de «exactamente uno» y dos restricciones de unicidad distintas según la fila; es lo que `cm.md` §7.1 y §7.2 ya descartaron para las tasas |
| Registrar la escala entera de una persona en una sola llamada | Cada escalón tiene su vigencia y su identidad; una llamada con varios obligaría a decidir qué hacer cuando uno choca |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que el listado «vigentes en» y el cierre resuelvan escalas distintas | El predicado vive una vez en el repositorio; `CA-CM-236` y la suite de `RF-CM-020` lo comprueban por los dos lados |

---

## 11. Estrategia de prueba

`UserAfftrackRatesIT`: `CA-CM-231` a `CA-CM-240`. `CA-CM-233` con dos hilos.
