# PLAN — `RF-MV-056` Consultar los movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-056` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 06-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

---

## 1. Enfoque

**El de `RF-MV-055`** ([`plan.md`](../055-consultar-mis-movimientos-de-puntos/plan.md) §1), con la persona tomada del parámetro `userId` en lugar del actor, y **dos cosas más en la consulta**: quién registró el ajuste —`LEFT JOIN users` por `recorded_by`, como `RF-MV-053`— y la búsqueda por nombre, usuario y correo. **El documento no entra en la consulta**: ni se selecciona ni se busca (`CA-MV-680`).

**La búsqueda por persona es un `JOIN` que el alcance propio no paga**: el filtro sabe qué alcance es y solo añade las condiciones sobre `users` en el de administración. Las dos listas leen `users` igual —para la persona de la fila—, de modo que el `JOIN` está en las dos; lo que cambia es el predicado.

---

## 2. Cambios de esquema

**`V77`** ([`055 plan.md`](../055-consultar-mis-movimientos-de-puntos/plan.md) §2). De aquí: renombrar `movements:list-points-adjustments` → `movements:list-points-movements`, y `movements:read-points-movement` y `movements:download-points-receipt` a `SUPERADMIN` y `ADMIN`, explícito.

---

## 3. Componentes afectados

Los de `RF-MV-055`, más:

| Capa | Componente | Cambio |
|---|---|---|
| `domain/service` | `PointsAdjustmentService` | **Pierde `list`** |
| `domain/repository` | `MovementRepository`, `JpaMovementRepository` | **Pierden** `findPointsAdjustments`, `countPointsAdjustments`, `PointsAdjustmentFilter`, `PointsAdjustmentListRow` |
| `application` | `ListPointsAdjustmentsRequest`, `PointsAdjustmentSortField`, `PointsAdjustmentItem` | **Se retiran** |
| `interfaces` | `PointsController` | **Pierde `GET /points-adjustments`** |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/points-movements` | `movements:list-points-movements` |
| `GET` | `/api/v1/movements/points-movements/{id}` | `movements:read-points-movement` |
| `GET` | `/api/v1/movements/points-movements/{id}/receipt` | `movements:download-points-receipt` |

Parámetros del listado: los de `RF-MV-055`, más `userId`. Códigos: los de `RF-MV-055` §4. **Se retira** `GET /api/v1/movements/points-adjustments`; **`POST` sigue**.

---

## 5. Autorización

Un `@PreAuthorize` por ruta.

---

## 6. Auditoría

Ninguna (`RF-MV-055` §6).

---

## 7. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Mantener `RF-MV-053` y añadir otra lista de compras | Lo que el responsable pidió es lo contrario |
| Sacar quién hizo el ajuste de la auditoría | `RF-MV-053` §1 |

---

## 8. Estrategia de prueba

Integración, `PointsMovementsIT`: `CA-MV-674` a `CA-MV-684`. **`PointsAdjustmentListIT` se retira**; lo que de él siga valiendo —búsqueda sin acentos, sin documento, quién lo hizo nulo antes de `V73`— pasa aquí.
