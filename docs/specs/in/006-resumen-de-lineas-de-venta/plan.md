# PLAN — `RF-IN-006` Consultar el resumen de líneas de venta

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-006` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 06-10-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! warning "Enmendado el 07-10-2026 — por tipo de producto, y lo sin vendedor también"

    Decisión del responsable del proyecto, 07-10-2026: «mejor agrupemos por lo siguiente: por tipo de producto y líneas de ventas sin vendedores», con dos precisiones suyas: **por tipo de producto, solo lo vendido** —lo confirmado—, y **lo sin vendedor también por tipo**. **Cómo se construye.** El total de lo vendido es `summary(everything()).confirmed()` y el de lo sin vendedor `unassigned`, los dos ya publicados. **Lo por tipo es una lectura nueva de `SalesFigures`**, `byProductType(Lines, Interval, UUID currencyId)` —`Lines` = `SOLD` \| `UNASSIGNED`— y su versión por tramo: la sentencia del resumen con `JOIN products p ON p.id = d.product_id` —como ya hacen otras lecturas de `JpaMovementRepository`— y `p.type` en el lugar del estado, para pasar por el mismo mapeo. `SOLD` filtra `m.status = 'CONFIRMADA'`; `UNASSIGNED`, lo de `SIN_VENDEDOR`. La respuesta: `sold` y `unassigned`, cada uno con `total` y `byType`; `byType` trae los tipos con datos, por nombre.

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Tablas, clases, endpoints, transacciones, pruebas.

    Lo que decide el negocio está en `spec.md` y no se repite aquí.

---

## 1. Enfoque

**Las cifras por estado ya las da `SalesFigures.summary` con `SalesScope.everything()`**: cuenta por línea, por estado y por moneda, con las líneas sin vendedor dentro. Este requerimiento **la reutiliza tal cual** —y por eso `CA-IN-061` cuadra por construcción— y añade **una lectura** que `MV` no publica: lo sin vendedor de las ventas no anuladas. Con tramo, las dos tienen su versión por tramo (`summaryByBucket`, ya publicada, y la nueva).

**No hay alcance**: el servicio no llama a `SalesScopeResolver` (`RN-IN-011`). La única puerta es el permiso.

---

## 2. Cambios de esquema

**Ninguno.** `ix_movement_details_seller` (`V12`) responde las líneas con `seller_id` nulo.

**`V78__in_permiso_de_lineas.sql`**: siembra `indicators:read-sale-lines-summary` (`…-5e7ad8000006`) **solo** a `SUPERADMIN` y `ADMIN`, explícitos, como los permisos de administración (`security.md` §4.4). Guardas: catálogo **203**, `SUPERADMIN` 203, `ADMIN` 201, **ningún otro rol** con él, contención.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `movements/application` | `SalesFigures` | Modificado | Gana `Totals unassigned(Interval, UUID currencyId)` y `List<BucketTotals> unassignedByBucket(Interval, UUID, Granularity, ZoneId)`; `BucketTotals(LocalDate start, Totals totals)` |
| `movements/domain/repository` | `JpaSalesFigures` | Modificado | La sentencia de §4.3 |
| `indicators/application` | `SaleLinesSummaryResponse` | **Nuevo** | §4.2 |
| `indicators/domain/service` | `GetSaleLinesSummaryService` | **Nuevo** | Periodo y tramo de `SalesPeriodResolver`; sin alcance |
| `indicators/interfaces` | `SalesIndicatorsController` | Modificado | `GET /api/v1/indicators/sales/lines/summary` |

**`unassigned` no recibe alcance**, como se anticipó en `RF-IN-004` · `plan.md` §3: lo sin vendedor solo tiene sentido sobre todo el libro, y que la firma no admita otro alcance impide aplicarlo por descuido.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/sales/lines/summary` | `indicators:read-sale-lines-summary` |

**Bajo `/sales/lines`** porque es la tanda de ventas mirada desde las líneas, como `GET /movements/sales/lines` lo es en `MV`.

### 4.1 Parámetros

`from`, `to`, `currencyId` y `granularity`, los de `RN-IN-010`. **Sin `sellerId`.**

### 4.2 La respuesta

```json
{
  "period": { "from": null, "to": "2026-10-06", "zone": "America/Bogota" },
  "total":     { "sales": 15, "lines": 19, "units": 24 },
  "confirmed": { "sales": 12, "lines": 15, "units": 19, "amounts": [ … ] },
  "pending":   { "sales": 2,  "lines": 3,  "units": 4,  "amounts": [ … ] },
  "voided":    { "sales": 1,  "lines": 1,  "units": 1,  "amounts": [ … ] },
  "unassigned":{ "sales": 2,  "lines": 2,  "units": 3,  "amounts": [ … ] },
  "granularity": null,
  "buckets": null
}
```

Esquemas `SaleLinesSummary`, `SaleLinesBlock`, `SaleLinesTotal` y `SaleLinesBucket`; `period`, `granularity` y `buckets` presentes aunque sean nulos, como en `RF-IN-001`.

### 4.3 La sentencia de lo sin vendedor

La de `RF-IN-001` §4.4 con `d.seller_id IS NULL` y `m.status IN ('CONFIRMADA', 'PENDIENTE')`, agrupada por moneda —y por tramo, en su versión por tramos—. Reutiliza `DE_LAS_VENTAS` y el límite inferior opcional de `JpaSalesFigures`.

### 4.4 Códigos de respuesta

Los de `RF-IN-001` §4.3, con su permiso.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-sale-lines-summary')")`; la ruta en `PERMISO_DE_CADA_OPERACION`. **Ni `movements:list-sale-lines` ni ningún `indicators:` de ventas lo abren.**

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md` | `RN-IN-011`, `RF-IN-005`→`RF-IN-006` en el submódulo Ventas, la ficha, la ruta — **en este pase** |
| `security.md` | §4.4: el permiso declarado — **en este pase**; sembrado — al construir |
| `requirements.md` | La fila — **en este pase** |
| `requirements/mv.md` | §3: `SalesFigures` gana lo sin vendedor — al construir |
| `docs/api/index.md` | La ruta — al construir |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Ampliar el resumen de ventas | Otro permiso y otro público: el resumen es por alcance, este no |
| Acotarlo por alcance | Lo sin vendedor sería siempre cero para un vendedor (`spec.md` §2.1) |
| Reutilizar `movements:list-sale-lines` | `RN-SEG-014`: un permiso, una operación |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que administración dé el permiso a un vendedor sin saber que lo ve todo | La descripción del permiso y la de la ruta lo dicen |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Unidades, estados, total | Integración, `SaleLinesSummaryIT` | `CA-IN-059`, `CA-IN-060` |
| Coincide con el resumen de administración | Integración, llamando a las dos rutas | `CA-IN-061` |
| Sin vendedor, mixta, anulada | Integración | `CA-IN-062`, `CA-IN-063` |
| Un vendedor con el permiso ve lo mismo | Integración | `CA-IN-064` |
| Periodo, moneda, tramos | Integración | `CA-IN-065` |
| Permisos y siembra | Integración y los recuentos del catálogo | `CA-IN-066` |
