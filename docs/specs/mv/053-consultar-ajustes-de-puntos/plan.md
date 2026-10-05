# PLAN — `RF-MV-053` Consultar los ajustes de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-053` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

---

## 1. Enfoque

**Una consulta nativa sobre `movements` del tipo `AJUSTE_PUNTOS`**, con `users` dos veces —el sujeto y quien lo registró— y `currencies`, y su conteo acotado (`BoundedCount`), como el libro de `RF-MV-006`. El orden sale de una lista blanca con desempate por `id`, como `CourseCategorySortField`.

**Quién lo hizo se guarda en el movimiento** (`movements.recorded_by`): `RF-MV-052` lo escribe desde `V73` con el actor autenticado. Leerlo de `audit_change_log` haría de la auditoría una tabla de negocio, y su forma —un JSON— no se puede indexar ni unir con garantías.

---

## 2. Cambios de esquema

**`V73`** (compartida con `RF-MV-054`):

| Elemento | Definición | Por qué |
|---|---|---|
| `movements.recorded_by` | `uuid NULL`, FK a `users` `ON DELETE SET NULL` | Quién registró un ajuste. `SET NULL`: borrar una persona no borra lo que hizo |
| `ix_movements_ajustes` | Índice parcial `(occurred_at DESC, id DESC)` sobre los `AJUSTE_PUNTOS` | El orden por omisión. Parcial por el tipo, que sale de una subconsulta a un catálogo fijo: se escribe con el identificador literal del tipo |
| Permisos | `movements:list-points-adjustments` y `movements:read-user-balances`, a `SUPERADMIN` y `ADMIN` | Catálogo 183 → **185** |

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/models` | `Movement` | `recordedBy` en el ajuste |
| `domain/repository` | `MovementRepository` | `recorded_by` al insertar; `findPointsAdjustments`, `countPointsAdjustments` |
| `domain/service` | `PointsAdjustmentService` | Escribe el actor; nace `list(...)` |
| `application` | `ListPointsAdjustmentsRequest`, `PointsAdjustmentSortField`, `PointsAdjustmentItem` | Nuevos |
| `interfaces` | `PointsController` | `GET /points-adjustments` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/points-adjustments` | `movements:list-points-adjustments` |

Parámetros: `page`, `size`, `sort` (`occurredAt` —por omisión, `desc`—, `points`, `code`), `userId`, `currencyId`, `from`, `to`, `sign` (`SUMA`, `RESTA`), `q`. Respuesta: la página de siempre (`PageResponse`) de `PointsAdjustmentItem`.

| Código | Cuándo |
|---|---|
| `200` | Siempre que la consulta sea válida, también vacía |
| `400` | Página, orden, sentido o periodo inválidos, todos juntos |
| `401` / `403` | Sin token / sin permiso |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:list-points-adjustments')")`.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Leer quién lo hizo de la auditoría | §1 |
| Reutilizar `movements:adjust-points` | `RN-SEG-014` |
| Reutilizar el libro de `RF-MV-006` con `type=AJUSTE_PUNTOS` | No trae los puntos con signo, la referencia ni quién lo hizo, y su permiso abre todo el libro |

---

## 8. Estrategia de prueba

Integración, `PointsAdjustmentListIT`: `CA-MV-647` a `CA-MV-655`.
