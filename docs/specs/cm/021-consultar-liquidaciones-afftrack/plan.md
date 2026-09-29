# PLAN — `RF-CM-021` Consultar las liquidaciones afftrack

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-021` |
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

La mecánica es la de [`RF-CM-014`](../014-consultar-desenlace-lineas/plan.md) tal como se construyó (su `tasks.md` §3.1): **una sentencia con `JOIN` de lectura** a los módulos ajenos.

---

## 1. Enfoque

Una sentencia paginada sobre `afftrack_settlements`, con:

- `JOIN` a `commission_closings` —el instante del cierre—, `users`, `products` y `currencies`;
- `LEFT JOIN` a `commissions` por `afftrack_settlement_id` —**lo que se pagó**: valor por FTD e importe, copiados en la comisión (`spec.md` §13)—;
- un subtotal de `afftrack_ftds` por liquidación con `count(*) FILTER (WHERE chain_level = 0)` y `FILTER (WHERE chain_level > 0)`, **en una subconsulta agregada unida por la liquidación** y no correlacionada por fila.

**El escalón se lee de la comisión y no de la tabla de escalones**, que puede haberse corregido o retirado: el límite es `paid_ftds`, el valor es `commissions.fixed_amount`, la fuente es `afftrack_settlements.source`.

---

## 2. Cambios de esquema

**Ninguno**: `V54`, con `ix_afftrack_settlements_user_product_created`. El filtro por cierre usa `uq_afftrack_settlements_closing_user_product`, que empieza por `closing_id`.

---

## 3. Componentes afectados

| Módulo | Capa | Componente | Cambio |
|---|---|---|---|
| `CM` | `domain/repository` | `AfftrackSettlementQueryRepository` y adaptador | Nuevos |
| `CM` | `domain/service` | `ListAfftrackSettlementsService` | Nuevo |
| `CM` | `application` | `ListAfftrackSettlementsRequest`, `AfftrackSettlementItem`, `AfftrackSettlementPageResponse` | Nuevos, `@Schema(name)` explícito |
| `CM` | `interfaces` | `AfftrackSettlementController` | Nuevo |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/afftrack-settlements` | `afftrack-settlements:read` |

Filtros `userId`, `productId`, `closingId`, `from`, `to`, `paid`; `page`, `size`. Orden: `commission_closings.closed_at` descendente, persona, producto. Cada elemento: `closing {id, closedAt}`, `user`, `product`, `carriedIn`, `newFtds`, `ownFtds`, `networkFtds`, `paidFtds`, `carriedOut`, `tier {threshold, amountPerFtd, source}` o nulo, `amount` o nulo. Códigos: `200`, `400` todos juntos, `401`, `403`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('afftrack-settlements:read')")`; en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

Ninguno.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Guardar `own_ftds` y `network_ftds` en la liquidación | Deducibles de `afftrack_ftds`, y la liquidación ya declara la única cuenta que importa (`ck_afftrack_settlements_counts`) |
| Leer el valor del escalón de `afftrack_rates` | Diría lo que vale hoy, no lo que se pagó |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Un `N+1` en los subtotales | Subconsulta agregada; estadísticas de Hibernate (`CA-CM-259`) |

---

## 11. Estrategia de prueba

`AfftrackSettlementsIT`: `CA-CM-255` a `CA-CM-259`, sobre cierres reales de `RF-CM-020`.
