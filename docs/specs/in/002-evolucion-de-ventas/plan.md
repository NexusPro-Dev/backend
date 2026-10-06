# PLAN — `RF-IN-002` Consultar la evolución de las ventas

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-002` |
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

**Todo lo compartido lo construye `RF-IN-001`** ([`plan.md`](../001-resumen-de-ventas/plan.md) §1): el permiso ya está sembrado, el periodo, el alcance y su corte ya existen, y `SalesFigures` ya está publicada. Este plan añade **un método a `SalesFigures`**, **un servicio** y **una ruta**, y decide **dónde se agrupa por tramo y dónde se rellenan los huecos**.

**Agrupa la base; rellena `IN`.** `MV` agrupa con `date_trunc` sobre la hora de Bogotá y devuelve solo los tramos con ventas; `IN` genera el calendario completo del periodo y lo cruza. Rellenar en SQL con `generate_series` funcionaría, pero metería en `MV` una regla de presentación —«los tramos vacíos aparecen»— que es de `IN` (`spec.md` §2.1). **La zona viaja como parámetro**: `MV` no decide en qué zona se cuenta, la recibe de `BusinessCalendar.zona()` por `IN`.

---

## 2. Cambios de esquema

Ninguno. El permiso lo siembra la migración de `RF-IN-001` · `T-01`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `movements/application` | `SalesFigures` | Modificado | Gana `List<Bucket> confirmedByBucket(SalesScope, Interval, UUID currencyId, Granularity, ZoneId)`. `Bucket(LocalDate start, UUID currencyId, String currencyCode, long sales, long lines, long units, BigDecimal amount)`: una fila por tramo **y moneda** |
| `movements/application` | `SalesFigures.Granularity` | **Nuevo** | `DAY`, `WEEK`, `MONTH` |
| `movements/domain/repository` | `JpaSalesFigures` | Modificado | §4.3 |
| `indicators/application` | `SalesSeriesResponse` | **Nuevo** | §4.2 |
| `indicators/domain/service` | `SalesCalendar` | **Nuevo** | Los inicios de tramo del periodo —lunes para la semana, día uno para el mes—; puro, sin base |
| `indicators/domain/service` | `GetSalesSeriesService` | **Nuevo** | Valida (`VAL-005` junto a los demás), resuelve periodo y alcance, corta con ceros, consulta, **cruza con el calendario** y rellena monedas |
| `indicators/interfaces` | `SalesIndicatorsController` | Modificado | `GET /api/v1/indicators/sales/series` |

**Con el corte fuera del alcance, el calendario se sigue generando**: la respuesta es la serie completa con ceros (`spec.md` · `FA-002`), no una lista vacía.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/sales/series` | `indicators:read-sales-series` |

### 4.1 Parámetros

Los de `RF-IN-001` §4.1, y `granularity`: `DAY` \| `WEEK` \| `MONTH`, por defecto `DAY`; uno no admitido es `400` `VAL-005`, devuelto con los demás.

### 4.2 La respuesta

```json
{
  "period": { "from": "2026-09-10", "to": "2026-09-23", "zone": "America/Bogota" },
  "granularity": "WEEK",
  "currencies": [ { "id": "…", "code": "COP" }, { "id": "…", "code": "USD" } ],
  "buckets": [
    { "start": "2026-09-07", "sales": 2, "lines": 2, "units": 3,
      "amounts": [ { "currency": { "id": "…", "code": "COP" }, "amount": 0.00 },
                   { "currency": { "id": "…", "code": "USD" }, "amount": 300.00 } ] }
  ]
}
```

**`amounts` va en el orden de `currencies`** —por código— en todos los tramos, para que el frontend indexe sin buscar. `@Schema(name = "SalesSeries")` y `SalesSeriesBucket`; `IndicatorPeriod`, `IndicatorAmount` e `IndicatorCurrency` son los de `RF-IN-001`.

### 4.3 La sentencia

La de `RF-IN-001` §4.4 con tres cambios: solo `m.status = 'CONFIRMADA'`; la clave de grupo es `date_trunc(:unidad, m.occurred_at AT TIME ZONE :zona)::date` —`day`, `week` o `month`; `date_trunc('week', …)` de PostgreSQL empieza en **lunes**, que es lo que `spec.md` §2.2 pide—; y el `GROUP BY` lleva el tramo y la moneda. El intervalo sigue siendo el semiabierto del periodo, de modo que el primer y el último tramo **ya salen recortados** sin lógica adicional.

### 4.4 Códigos de respuesta

Los de `RF-IN-001` §4.3, con su permiso.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-sales-series')")`; la ruta en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`, como `RF-IN-001`.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md`, `requirements.md` | Ficha y fila — en este pase; al construir, estado |
| `docs/api/index.md` | La ruta — al construir |

`SalesFigures` gana un método: `requirements/mv.md` y `architecture.md` ya la nombran desde `RF-IN-001`, y no cambian.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Rellenar con `generate_series` en SQL | La regla de los tramos vacíos es de `IN`; y el cruce en Java es trivial con 366 tramos como mucho |
| Agrupar en Java sobre las ventas del periodo | Traería todas las líneas del periodo; la base agrupa con el índice por fecha |
| Agrupar en UTC y desplazar | El corte de un día de Bogotá no es un desplazamiento de un día UTC; `AT TIME ZONE` lo hace bien |
| Una consulta por tramo | 366 sentencias para la serie diaria de un año |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que la semana de PostgreSQL y la de `SalesCalendar` no coincidan | `CA-IN-018` las cruza: un tramo que la base devuelva y el calendario no tenga hace fallar la prueba en lugar de perderse |
| `AT TIME ZONE` sobre `occurred_at` impide usar el índice para agrupar | El índice sigue sirviendo para el intervalo, que es el filtro; el agrupado es sobre lo ya filtrado |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| La suma de los tramos es el confirmado del resumen | Integración, `SalesSeriesIT`, llamando a las dos rutas | `CA-IN-015` |
| Tramos vacíos, monedas rellenas, orden | Integración | `CA-IN-016`, `CA-IN-017` |
| Semana de lunes, mes recortado, venta a las 20:00 de Bogotá | Integración | `CA-IN-018`, `CA-IN-019` |
| Por defecto y alcance | Integración | `CA-IN-020`, `CA-IN-021` |
| Errores y permisos | Integración | `CA-IN-022` |
| `SalesCalendar`: domingo, 31 de enero a 1 de marzo, día único, bisiesto | Unitaria | Aritmética de calendario |
| Coste: una sentencia sea cual sea el número de tramos | Integración, estadísticas de Hibernate | 7 tramos contra 90 |
