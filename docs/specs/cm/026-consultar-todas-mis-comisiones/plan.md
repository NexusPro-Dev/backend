# PLAN — `RF-CM-026` Consultar todas mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-026` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 07-10-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-10-2026 |

!!! warning "Enmendado el 07-10-2026 — `clientId`"

    `spec.md` v0.2.0. `MyCommissionsRequest` y `OwnFilter` ganan `UUID clientId`, y `PROPIAS_DESDE` el predicado `m.user_id = :cliente` sobre la venta que ya une —el cliente es `movements.user_id`—. Una `POR_AFFTRACK` no tiene `m`, así que el predicado la deja fuera sin un caso aparte. Sin índice nuevo: el filtro estrecha lo que `ix_commissions_user` ya acota por persona. Pruebas en `MyCommissionsIT`. **Ampliación**: la respuesta no cambia.

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

Lo propio se construye como en [`RF-CM-012`](../012-consultar-mis-comisiones/plan.md) §1 —la persona la pone el token y no existe un parámetro que la cambie— y la forma de cada comisión es la del detalle de [`RF-CM-010`](../010-consultar-lotes-comision/plan.md). No se repite aquí por qué.

---

## 1. Enfoque

**Una sentencia nativa sobre `commissions`, filtrada por `k.user_id`**, con los datos ajenos unidos en ella —el lote, su moneda, la venta, el cliente y el producto—, por el mismo precedente que `JpaCommissionBatchQueryRepository`: un `JOIN` de lectura no cambia nada de nadie, y leer por el catálogo de cada módulo sería una ida por módulo y página.

**Se filtra por `commissions.user_id` y no por el dueño del lote**: son la misma persona —cada comisión está en un lote de quien la cobra (`RN-CM-025`)—, y la columna de la comisión es la que puede llevar el índice del orden.

**Las columnas de la comisión son las de `COMISION`**, la proyección que ya comparten el detalle y las retiradas, y se convierten con el mismo `comision(f)`: una tercera copia sería la que se quedara atrás el día que la comisión gane una columna. La sentencia nueva **añade** al final las del lote, la moneda y el cliente.

**El total se cuenta exacto**, como `RF-CM-012`: es el conjunto de una persona, no la tabla entera.

---

## 2. Cambios de esquema

**`V81`**, con dos cosas:

| Qué | Por qué |
|---|---|
| `commission-batches:list-own-commissions`, sembrado a **todo rol que porte `commission-batches:list-own`** | `requirements/cm.md` §6. Repartirlo por quien ya ve sus lotes conserva la contención (`RN-SEG-003`) sin tocar roles uno a uno: si un hijo porta `list-own`, su padre también |
| `ix_commissions_user (user_id, accrued_at DESC, id DESC)` | El `WHERE` y el `ORDER BY` de la página. Sin él, cada página recorre la tabla entera |

Guardas al final de la migración, como `V79`: el catálogo en **205**, el permiso exactamente en los roles que portan `list-own`, y cero filas que rompan la contención.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/repository` | `CommissionBatchQueryRepository` | Gana `searchOwn`, `countOwn`, `OwnFilter` y `OwnCommissionRow` | `OwnCommissionRow` envuelve el `CommissionRow` de siempre |
| `domain/repository` | `JpaCommissionBatchQueryRepository` | Las dos sentencias | Reutiliza `COMISION` y `comision(f)` |
| `application` | `MyCommissionsRequest` | Nuevo | Sin persona |
| `application` | `MyCommissionItem`, `MyCommissionPageResponse` | Nuevos, con `@Schema(name)` | La comisión es `CommissionLine`, la del detalle |
| `domain/service` | `CommissionBatchQueryService` | Gana `listOwnCommissions` | Valida, pagina y llama |
| `interfaces` | `CommissionBatchController` | Gana `GET /mine/commissions` | La ruta literal gana a `/mine/{id}`, como `/mine` gana a `/{id}` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/commission-batches/mine/commissions` | `commission-batches:list-own-commissions` |

Parámetros: `page`, `size`, `status`, `currencyId`, `productId`, `commissionKind`, `from`, `to`. **`from` y `to` son instantes**, como en `RF-CM-010`.

Cada elemento:

```json
{
  "commission": { "…": "la CommissionLine del detalle de un lote" },
  "batch": { "id": "…", "code": "…", "status": "PENDIENTE" },
  "currency": { "id": "…", "code": "COP" },
  "client": { "id": "…", "username": "…", "fullName": "…" }
}
```

`client` es nulo en una `POR_AFFTRACK`. La página lleva `sort = "accruedAt,desc"`.

| Código | Cuándo |
|---|---|
| `200` | Página de mis comisiones |
| `400` | Filtros inválidos, todos juntos |
| `401` | Sin token |
| `403` | Sin el permiso |

---

## 5. Autorización

Un `@PreAuthorize`; la operación entra en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos

Ninguno: lee `movements`, `movement_details`, `users`, `currencies` y `products` en la sentencia, como el detalle de un lote.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| `GET /commissions/mine` | `commissions:` es el prefijo de las tasas de rol (`requirements/cm.md` §6); una comisión vive dentro de un lote, y la ruta lo dice |
| Reutilizar `commission-batches:list-own` | Otra operación sobre otra ruta (`RN-SEG-014`) |
| Una fila plana, sin `commission` anidada | Duplicaría veinte campos de `CommissionLine` en un esquema nuevo, que se quedaría atrás el día que la comisión cambie |
| Totales por moneda en la respuesta | Fuera de alcance (`spec.md` §4.2) |
| Filtrar por el dueño del lote | Es la misma persona, y el índice va en la comisión |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que se vea lo ajeno | `CA-CM-347` y `CA-CM-349` con dos vendedores en cadena |
| Que una retirada salga dos veces | `CA-CM-353`: se lee la comisión, no las retiradas de cada lote |

---

## 11. Estrategia de prueba

`MyCommissionsIT`: `CA-CM-347` a `CA-CM-355`, con **dos vendedores en cadena** y ventas reales por la API de `MV`, como `MyCommissionBatchesIT`. `CA-CM-354` reasigna el vendedor por `POST /movements/{id}/seller-assignments`. `EndpointPermissionsIT` cubre la ruta, y la cuenta del catálogo pasa a **205** en `PermissionIT`.
