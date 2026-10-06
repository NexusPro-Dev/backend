# PLAN — `RF-IN-004` Consultar las ventas por vendedor

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-004` |
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

**Es `RF-IN-003` con el vendedor de la línea en lugar del producto** ([`plan.md`](../003-ventas-por-producto/plan.md)): las mismas dos sentencias —el ranking con su total y los importes del corte—, con dos diferencias. **Lo sin asignar** es una tercera suma, que solo se pide con `everything()` sin filtro de vendedor. Y **la identidad** del vendedor no la congela la línea: se pide a `SP` por `UserCatalog.findAll`, **una** sentencia para todos los del corte.

---

## 2. Cambios de esquema

Ninguno. `ix_movement_details_seller` (`V12`) sirve también para agrupar.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `movements/application` | `SalesFigures` | Modificado | Gana `SellerRanking confirmedBySeller(SalesScope, Interval, UUID currencyId, int limit)` y `Totals confirmedUnassigned(Interval, UUID currencyId)`. `SellerRanking(long totalSellers, List<SellerFigure> top)`; `SellerFigure(UUID sellerId, long sales, long lines, long units, List<Amount> amounts)`; `Totals(long sales, long lines, long units, List<Amount> amounts)` |
| `movements/domain/repository` | `JpaSalesFigures` | Modificado | §4.3 |
| `indicators/application` | `SalesBySellerResponse` | **Nuevo** | §4.2 |
| `indicators/domain/service` | `GetSalesBySellerService` | **Nuevo** | `VAL-005`, periodo, alcance, corte, identidades por `UserCatalog`, y lo sin asignar solo si el alcance es `everything()` y no hay `sellerId` |
| `indicators/interfaces` | `SalesIndicatorsController` | Modificado | `GET /api/v1/indicators/sales/by-seller` |

**`confirmedUnassigned` no recibe alcance**: solo tiene sentido con `everything()`, y que la firma no admita otro es lo que impide que un vendedor lo reciba por un descuido de `IN`.

**`MV` no devuelve el nombre del vendedor**, aunque podría unirlo a `users` como hace en otras lecturas: la identidad es de `SP` y `IN` la consume de quien la publica. La sentencia de más es fija.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/sales/by-seller` | `indicators:read-sales-by-seller` |

### 4.1 Parámetros

Los de `RF-IN-001` §4.1, y `limit`: por defecto 20, entre 1 y 100; fuera de rango es `400` `VAL-005`.

### 4.2 La respuesta

```json
{
  "period": { "from": "2026-09-01", "to": "2026-09-30", "zone": "America/Bogota" },
  "orderedBy": "UNITS",
  "totalSellers": 7,
  "sellers": [
    { "seller": { "id": "…", "username": "agente1", "name": "Ana Pérez" },
      "sales": 5, "lines": 6, "units": 8,
      "amounts": [ { "currency": { "id": "…", "code": "USD" }, "amount": 640.00 } ] }
  ],
  "unassigned": { "sales": 1, "lines": 2, "units": 2, "amounts": [ … ] }
}
```

`unassigned` es **nulo** —y se serializa como tal— cuando no aplica, para distinguir «no te corresponde» de «no hay nada sin asignar». `@Schema(name = "SalesBySeller")`, `SalesBySellerRow`, `IndicatorSeller` e `IndicatorTotals`.

### 4.3 Las sentencias

Las de `RF-IN-003` §4.3 agrupando por `d.seller_id` —con `d.seller_id IS NOT NULL` siempre, también con `everything()`— y con el nombre de usuario como último desempate, que se aplica en Java tras pedir las identidades porque la base de `MV` no lo tiene. La tercera, `confirmedUnassigned`: la de `RF-IN-001` §4.4 con `m.status = 'CONFIRMADA'` y `d.seller_id IS NULL`.

**El desempate por nombre en Java reordena solo empates exactos de unidades y ventas**: el corte de la base ya está hecho, y un empate en la frontera del límite puede dejar fuera a uno u otro según el orden de la base. Se acepta y se documenta; desempatar en SQL exigiría unir `users`.

### 4.4 Códigos de respuesta

Los de `RF-IN-001` §4.3, con su permiso.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-sales-by-seller')")`; la ruta en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`: las tres sentencias de `MV` y la de `SP` describen el mismo instante.

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
| El acumulado por rama en cada fila | Cuenta dos veces (`spec.md` §2.1) |
| Lo sin asignar como una fila con vendedor nulo | No es de nadie, y un ranking que lo incluya lo compara con personas |
| Unir `users` en la sentencia de `MV` | La identidad es de `SP`; y el desempate en Java basta |
| Incluir vendedores sin ventas | `spec.md` §2.3 y §14 |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| El empate en la frontera del límite | Documentado en §4.3 y en la prosa de la ruta |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Filas + sin asignar = confirmado del resumen en líneas, unidades e importes | Integración, `SalesBySellerIT` | `CA-IN-030` |
| La fila del director no incluye a sus agentes | Integración | `CA-IN-031` |
| Los cuatro niveles y la frontera entre ramas | Integración, árbol de `SalesIT` | `CA-IN-032` |
| Sin asignar: administración sí, vendedor no, administración con `sellerId` no | Integración | `CA-IN-033` |
| Sin ventas no aparece; retirado sí | Integración | `CA-IN-034` |
| Orden, límite, total | Integración | `CA-IN-035` |
| Fuera del alcance; `ended_at` | Integración | `CA-IN-036` |
| Errores y permisos | Integración | `CA-IN-037` |
| Coste: sentencias fijas con `limit` 2 y 100 | Integración, estadísticas de Hibernate | Sin `N+1` por identidad |
