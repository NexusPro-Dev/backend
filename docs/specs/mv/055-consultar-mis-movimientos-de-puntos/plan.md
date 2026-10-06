# PLAN — `RF-MV-055` Consultar mis movimientos de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-055` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 06-10-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

---

## 1. Enfoque

**Una sola consulta nativa sobre `movements` de los dos tipos de puntos**, `COMPRA_PUNTOS` y `AJUSTE_PUNTOS`, por sus identificadores literales —como `RF-MV-053`, para que el planificador vea el predicado del índice parcial—. **Las dos caben en la misma fila sin `UNION`** porque las dos viven en la misma tabla: lo que distingue una de otra son columnas que en la otra están vacías —`points_rate_id` y `payable_amount` en la compra; `concept`, `external_reference` y `recorded_by` en el ajuste—. Un `UNION` de dos consultas repetiría los filtros y el orden dos veces, y el conteo tres.

**Lo comparten `RF-MV-055` y `RF-MV-056`**: un repositorio de lectura nuevo, `PointsMovementQuery`, con un filtro que lleva **el alcance como un dato más** —la persona—; el propio lo fija con el actor autenticado y el de administración lo toma del parámetro. **No crece `JpaMovementRepository`**, que ya pasa de dos mil líneas: estas lecturas no escriben nada ni comparten nada con él salvo las tablas.

**La fila sale de los dos alcances con la misma forma**, `PointsMovementItem`; en el propio, `adjustedBy` va siempre nulo (`spec.md` §2.1). Se elige una forma y no dos porque el frontend pinta la misma tabla en las dos pantallas.

**El detalle** lee la fila y, según el tipo, **los pagos** —con `findPaymentsOf`, el de `RF-MV-031`— o **los datos del comprobante** —sin el contenido—. **La descarga** lee el contenido y responde con su tipo, `Content-Disposition: attachment` con el nombre guardado y `X-Content-Type-Options: nosniff` (`RN-MV-077`).

**Ajeno es inexistente** (`EX-002`): el detalle y la descarga del alcance propio buscan con la persona en la condición, de modo que un movimiento de otro no se encuentra; no hay una comprobación de propiedad aparte que pudiera responder distinto.

**Los gastos (0.2.0) sí exigen un `UNION ALL`**, y es la alternativa que §1 descartó para compra y ajuste: un gasto no vive en `movements` como un tipo de puntos, sino en un asiento `PAGO` de una cuenta `PUNTOS` (`RF-MV-030`). La consulta pasa a leer **una tabla derivada con la forma de la fila**: la rama de `movements` de antes y una rama nueva sobre `movement_entries` —evento `PAGO`, cuenta `PUNTOS` de una persona—, agrupada por venta, con la suma de sus asientos como puntos —negativa— y los productos de sus líneas como motivo. **Filtros, búsqueda, orden y conteo se escriben una sola vez sobre la derivada**: los nombres de sus columnas son los de `movements`, de modo que la lista blanca de orden no cambia. El planificador empuja los predicados de la persona a las dos ramas. Cuándo ocurrió un gasto es cuándo se descontó: la fecha del asiento.

---

## 2. Cambios de esquema

**`V77`**, compartida con `RF-MV-056` y `RF-MV-057`:

| Elemento | Definición | Por qué |
|---|---|---|
| `points_adjustment_receipts` | La tabla de [`requirements/mv.md`](../../../requirements/mv.md) §7.16, con `ck_points_adjustment_receipts_type` y `_size` | `RF-MV-057` la escribe; aquí se lee si la fila tiene comprobante |
| `ix_movements_puntos` | Índice parcial `(occurred_at DESC, id DESC)` sobre los dos tipos de puntos; **sustituye** a `ix_movements_ajustes` | El orden por omisión de las dos listas |
| `ix_movements_puntos_persona` | Índice parcial `(user_id, occurred_at DESC, id DESC)` sobre los dos tipos | La lista propia filtra siempre por persona |
| Renombrar | `movements:list-own-points-purchases` → `movements:list-own-points-movements`, con su nombre y descripción | Conserva las asignaciones (`requirements/mv.md` §4.12) |
| Permisos | `movements:read-own-points-movement` y `movements:download-own-points-receipt`, **por tipo de rol** —`FUNCIONARIO`, `VENDEDOR` y `CONSUMIDOR`—, como `V58` | `RN-SEG-015` |

Catálogo 197 → **202** con los de `RF-MV-056` y `RF-MV-057`. Guardas: los cinco permisos nuevos existen, los dos viejos no, el catálogo cuenta 202 y `ADMIN` sigue contenido en `SUPERADMIN` (`RN-SEG-003`).

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/repository` | `PointsMovementQuery`, `JpaPointsMovementQuery` | Nuevos: `find`, `count`, `findOne`, `findReceiptInfo`, `findReceiptFile`; **0.2.0**: la rama de los gastos y `findLines` |
| `domain/service` | `PointsMovementReader` | Nuevo: valida, resuelve el alcance y arma fila, detalle y descarga |
| `domain/service` | `PointsPurchaseService` | **Pierde `listMine`** y lo que solo él usaba |
| `domain/repository` | `MovementRepository`, `JpaMovementRepository` | **Pierden** `findOwnPointsPurchases`, `countOwnPointsPurchases` y `PointsPurchaseFilter` |
| `application` | `ListPointsMovementsRequest`, `PointsMovementSortField`, `PointsMovementItem`, `PointsMovementDetail`, `PointsReceiptInfo` | Nuevos |
| `interfaces` | `PointsMovementsController` | Nuevo: las tres rutas de aquí y las tres de `RF-MV-056` |
| `interfaces` | `PointsController` | **Pierde `GET /mine/points-purchases`** |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/movements/mine/points-movements` | `movements:list-own-points-movements` |
| `GET` | `/api/v1/movements/mine/points-movements/{id}` | `movements:read-own-points-movement` |
| `GET` | `/api/v1/movements/mine/points-movements/{id}/receipt` | `movements:download-own-points-receipt` |

Parámetros del listado: `page`, `size`, `sort` (`occurredAt` —por omisión, `desc`—, `points`, `code`), `type` (`COMPRA_PUNTOS`, `AJUSTE_PUNTOS`), `status` (`PENDIENTE`, `CONFIRMADA`, `RECHAZADA`), `currencyId`, `from`, `to`, `sign` (`SUMA`, `RESTA`), `q`. Respuesta: `PageResponse<PointsMovementItem>`.

| Código | Cuándo |
|---|---|
| `200` | La página, el detalle o el archivo |
| `400` | Parámetros inválidos, todos juntos |
| `401` / `403` | Sin token / sin permiso |
| `404` | El movimiento no existe, no es de puntos o es de otro (`EX-002`); no hay comprobante (`EX-003`) |

**Se retira** `GET /api/v1/movements/mine/points-purchases`.

---

## 5. Autorización

Un `@PreAuthorize` por ruta, con el permiso de §4. El alcance lo pone el servicio con `AuthenticatedActor`, nunca un parámetro.

---

## 6. Auditoría

Ninguna: son lecturas. **La descarga tampoco se audita**: es una lectura de un dato que el actor ya puede ver, como el detalle.

---

## 7. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| `UNION` de la consulta de compras y la de ajustes | §1 |
| Dos formas de fila, una por alcance | §1: la misma tabla en dos pantallas |
| Comprobar la propiedad aparte y responder `403` | Confirmaría que el movimiento existe |
| Retirar `list-own-points-purchases` y sembrar uno nuevo | Perdería las asignaciones hechas a mano (`requirements/mv.md` §4.12) |
| Reutilizar el detalle de `RF-MV-007` | Su permiso abre cualquier movimiento, y no trae el comprobante |

---

## 8. Estrategia de prueba

Integración, `OwnPointsMovementsIT`: `CA-MV-662` a `CA-MV-673`. Se retira `OwnPointsPurchasesIT` —o la parte de él que probaba el listado— y lo que de él siga valiendo pasa aquí.

---

## 9. Control de cambios

| Versión | Fecha | Cambio | Autor |
|---|---|---|---|
| 0.1.0 | 06-10-2026 | Primera versión. | Responsable técnico |
| 0.2.0 | 06-10-2026 | Los gastos: la tabla derivada con `UNION ALL` (§1) y las líneas en el detalle. | Responsable técnico |
