# PLAN — `RF-IN-003` Consultar las ventas por producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-003` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 06-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Tablas, clases, endpoints, transacciones, pruebas.

    Lo que decide el negocio está en `spec.md` y no se repite aquí.

---

## 1. Enfoque

**Lo compartido lo construye `RF-IN-001`** ([`plan.md`](../001-resumen-de-ventas/plan.md) §1). Este plan añade un método a `SalesFigures`, un servicio y una ruta. Lo que decide es **dónde se ordena y se corta**: en la base, porque el ranking necesita ver todos los productos del periodo para devolver los diez primeros, y traerlos todos para quedarse con diez es justo lo que una base hace mejor.

---

## 2. Cambios de esquema

Ninguno. `movement_details.product_id` no tiene índice propio y no lo necesita: el filtro es el intervalo, y el agrupado es sobre lo ya filtrado.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `movements/application` | `SalesFigures` | Modificado | Gana `ProductRanking confirmedByProduct(SalesScope, Interval, UUID currencyId, int limit)`. `ProductRanking(long totalProducts, List<ProductFigure> top)`; `ProductFigure(UUID productId, String productName, long sales, long lines, long units, List<Amount> amounts)` |
| `movements/domain/repository` | `JpaSalesFigures` | Modificado | §4.3 |
| `indicators/application` | `SalesByProductResponse` | **Nuevo** | §4.2 |
| `indicators/domain/service` | `GetSalesByProductService` | **Nuevo** | `VAL-005`, periodo, alcance, corte y mapeo |
| `indicators/interfaces` | `SalesIndicatorsController` | Modificado | `GET /api/v1/indicators/sales/by-product` |

**`ProductFigure` lleva `List<Amount>` y no un importe**: sin moneda elegida, un producto vendido en dos monedas tiene dos importes (`spec.md` §13). `Amount(UUID currencyId, String currencyCode, BigDecimal amount)` es el de `Summary`, ya publicado.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/sales/by-product` | `indicators:read-sales-by-product` |

### 4.1 Parámetros

Los de `RF-IN-001` §4.1, y `limit`: entero, por defecto 10, entre 1 y 50; fuera de rango es `400` `VAL-005`, devuelto con los demás.

### 4.2 La respuesta

```json
{
  "period": { "from": "2026-09-01", "to": "2026-09-30", "zone": "America/Bogota" },
  "orderedBy": "UNITS",
  "totalProducts": 14,
  "products": [
    { "product": { "id": "…", "name": "Bot Gold" },
      "sales": 9, "lines": 9, "units": 11,
      "amounts": [ { "currency": { "id": "…", "code": "USD" }, "amount": 990.00 } ] }
  ]
}
```

`orderedBy`: `UNITS` \| `AMOUNT`. `@Schema(name = "SalesByProduct")`, `SalesByProductRow` e **`IndicatorProduct`** —no `ProductRef`, que ya existe dos veces y springdoc funde por nombre simple—.

### 4.3 La sentencia

Dos pasos en **una** sentencia con `WITH`:

1. `por_producto`: la de `RF-IN-001` §4.4 con `m.status = 'CONFIRMADA'`, agrupada por `d.product_id` y, para el orden, la suma de unidades, de ventas (`count(DISTINCT m.id)`) e —con moneda— de importe; más el nombre de la línea más reciente (`(array_agg(d.product_name ORDER BY m.occurred_at DESC, m.id DESC))[1]`).
2. El total con `count(*) OVER ()`, el orden de `spec.md` §2.2 y `LIMIT :limite`.

Y **una segunda** para los importes por moneda de **solo** los productos del corte (`d.product_id IN (:top)`, agrupado por producto y moneda). Son dos sentencias fijas, sea cual sea el límite; hacerlo en una obligaría a ordenar por una magnitud que mezcla monedas, o a traer los importes de todos los productos para descartar la mayoría.

### 4.4 Códigos de respuesta

Los de `RF-IN-001` §4.3, con su permiso.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-sales-by-product')")`; la ruta en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`: las dos sentencias describen el mismo instante.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md`, `requirements.md` | Ficha y fila — en este pase; al construir, estado |
| `docs/api/index.md` | La ruta — al construir |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Agrupar por `product_name` | Dos productos con el mismo nombre se fundirían, y uno renombrado se partiría (`spec.md` §2.1) |
| Pedir el nombre vigente a `PM` | Un producto retirado o renombrado diría lo que es hoy y no lo que se vendió; y sería una dependencia `IN` → `PM` sin necesidad |
| Ordenar en Java | Traería todos los productos del periodo para devolver diez |
| Paginar | Es un ranking (`spec.md` §4.2) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| `array_agg` del nombre sobre muchas líneas por producto | Solo sobre lo filtrado por intervalo y alcance; se mide en la prueba de costes |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Suma de las filas = confirmado del resumen, con `limit=50` | Integración, `SalesByProductIT` | `CA-IN-023` |
| Orden por unidades y por importe; desempates | Integración | `CA-IN-024` |
| Renombrado, retirado, paquete | Integración | `CA-IN-025`, `CA-IN-026` |
| Límite y total | Integración | `CA-IN-027` |
| Alcance con una venta de dos ramas | Integración | `CA-IN-028` |
| Errores y permisos | Integración | `CA-IN-029` |
| Coste: dos sentencias con `limit` 2 y 50 | Integración, estadísticas de Hibernate | Sin `N+1` por producto |
