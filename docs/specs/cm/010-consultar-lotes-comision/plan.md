# PLAN — `RF-CM-010` Consultar los lotes de comisión, y el detalle de uno

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-010` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 28-09-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 28-09-2026 |
| Enmendado el | 29-09-2026 — `commissionKind` y la forma de la fila afftrack (§12) |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

La mecánica de los listados de `CM` —página envuelta, filtros validados todos juntos, prueba de número de sentencias— es la de [`RF-CM-002`](../002-consultar-tasas-comision/plan.md), y **se hereda sin repetirla**.

---

## 1. Enfoque

**Dos sentencias propias y una lectura en bloque por módulo ajeno.** El listado es una sentencia sobre `commission_batches` con un `COUNT` correlacionado de `commissions`. El detalle es una sentencia para la cabecera y otra para sus comisiones. **Lo que no es de `CM`** —el nombre de las personas, el comprobante de las ventas, el nombre de los productos— **se pide de una vez por página**: `UserCatalog.findAll(ids)`, `CommissionableLines.describe(detailIds)` y `ProductCatalog.saleViewOf(ids)` —que no filtra retirados—, **nunca por fila** (`CA-CM-188`).

---

## 2. Cambios de esquema

**Ninguno**: todo lo crea `V51` (`RF-CM-013`). Un índice de apoyo al orden, `ix_commission_batches_periodo` sobre `period_start DESC`, **se añade a `V51`** en lugar de a una migración propia, porque ninguna está escrita todavía.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio | Nota |
|---|---|---|---|---|
| `MV` | `application` | `CommissionableLines` | Gana `describe(Collection<UUID> detailIds)` | Comprobante de la venta y producto de cada línea, **sin** filtrar por estado: una línea ya comisionada se describe siempre |
| `CM` | `domain/repository` | `CommissionBatchQueryRepository` y adaptador | Nuevo | Listado y detalle |
| `CM` | `domain/service` | `ListCommissionBatchesService`, `GetCommissionBatchService` | Nuevos | Validan, leen y componen con las lecturas ajenas |
| `CM` | `application` | `CommissionBatchItem`, `CommissionBatchPageResponse`, `CommissionBatchDetailResponse`, `CommissionLineResponse`, `ListCommissionBatchesRequest` | Nuevos | `@Schema(name)` explícito: `RF-CM-012` devuelve **las mismas formas** y no deben fundirse con otras de igual nombre |
| `CM` | `interfaces` | `CommissionBatchController` | Gana dos rutas | |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/commission-batches` | `commission-batches:read` |
| `GET` | `/api/v1/commission-batches/{id}` | `commission-batches:read-detail` |

Filtros: `status`, `userId`, `currencyId`, `from`, `to`. Orden: `period_start` descendente, y el identificador para desempatar.

| Código | Cuándo |
|---|---|
| `200` | Siempre que haya permiso, aunque la lista esté vacía |
| `400` | Filtros o identificador malformados, todos juntos |
| `401` / `403` | Sin token / sin el permiso de la operación |
| `404` | El lote no existe (solo el detalle) |

**`/commission-batches/mine` (`RF-CM-012`) no lo captura `/{id}`**: la variable es un UUID y la ruta literal gana, como `/movements/mine`.

---

## 5. Autorización

Un `@PreAuthorize` por operación; las dos en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna: son lecturas.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

**`MV`** publica `describe` en `CommissionableLines` (`RF-CM-013`). Ninguna enmienda documental: `requirements/mv.md` v0.49.0 ya dice que `MV` publica una lectura de líneas para `CM`.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Copiar el comprobante y el nombre del producto en `commissions` | Duplicaría lo que no cambia —el comprobante— o lo que sí —el nombre del producto—, y `RN-CM-008` pide copiar lo que decide el importe, no lo que se muestra |
| Un `JOIN` directo a `movement_details` | Un repositorio no cruza módulo (D-25) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un `N+1` al componer | Estadísticas de Hibernate en la prueba (`CA-CM-188`) |

---

## 11. Estrategia de prueba

`ListCommissionBatchesIT` y `GetCommissionBatchIT`: `CA-CM-181` a `CA-CM-188`, con lotes producidos **devengando ventas reales** por la API de `MV` y cerrando con `RF-CM-009`, no insertando filas a mano. `CA-CM-188` cuenta sentencias con una página de varios lotes.

## 12. La clase de cada comisión — enmienda del 29-09-2026

`RN-CM-044`. **La sentencia del detalle pasa de unir la línea a unirla por `LEFT JOIN`**, y gana un `LEFT JOIN` a `afftrack_settlements` para el producto de las filas afftrack —`COALESCE` del producto de la línea y del de la liquidación—. `CommissionLineResponse` gana `commissionKind` y `afftrackSettlementId`; **`movementId`, `movementCode`, `detailId`, `chainLevel` y `unitPrice` pasan a nulables** en el contrato, y se dice en la prosa de la `@Operation`: es un cambio de forma que el frontend tiene que leer. `quantity`, `rateType` y `fixedAmount` significan en la fila afftrack los FTD pagados, `FIJO` y el valor por FTD. **`RF-CM-012` hereda el cambio**, porque devuelve las mismas formas. `CommissionBatchesIT` gana `CA-CM-262`.
