# PLAN — `RF-IN-007` Consultar el resumen de lotes de comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-007` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 07-10-2026 |
| Versión | 0.2.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-10-2026 |

!!! warning "Enmendado el 07-10-2026 — `sellerId`, `from` y `to`"

    `spec.md` v0.2.0. **`byStatus` recibe un `BatchFilter(UUID currencyId, UUID userId, OffsetDateTime from, OffsetDateTime to)`**, todos opcionales: `CM` no conoce `SalesFigures.Interval`, que es de `MV`, y por eso recibe los dos instantes sueltos. La sentencia gana `b.user_id = :persona`, `b.period_start < :hasta` y `(b.period_end IS NULL OR b.period_end > :desde)`: el periodo del lote es semiabierto, como el del indicador. **El servicio pasa por `SalesPeriodResolver`**, como los demás indicadores —días en la zona del negocio, «desde» nulo sin límite, «hasta» nulo hoy, `VAL-002`—, y la respuesta gana **`period`** (`IndicatorPeriod`). Sin fechas el intervalo va del principio a mañana, y todo lote empezó antes: es la misma foto de antes. **El vendedor no pasa por `SalesScopeResolver`**: no es alcance (`RN-IN-011`). `granularity` se sigue ignorando. **Ampliación**: la respuesta gana un campo y ninguno cambia. Revierte, en §4.1 y §4.2, «ni `from` ni `to`» y «sin `period`».

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Tablas, clases, endpoints, transacciones, pruebas.

    Lo que decide el negocio está en `spec.md` y no se repite aquí.

---

## 1. Enfoque

**`CM` publica una lectura agregada de sus lotes, y `IN` la presenta.** Es el reparto de `SalesFigures` (`RF-IN-001` · `plan.md` §3.1) trasladado al dueño de los lotes: `CM` sabe qué es un lote, en qué estado está y cuánto vale, y **no sabe qué es un indicador**. Quien pregunta le da, como mucho, una moneda, y recibe sumas, nunca filas.

**Una sentencia, sin periodo, sin alcance.** El servicio no llama a `SalesPeriodResolver` (`RN-IN-012`) ni a `SalesScopeResolver` (`RN-IN-011`). La única puerta es el permiso.

**`IN` pasa a depender de `CM`.** Es la dependencia que `requirements/in.md` §1.4 anticipaba al descartar que cada módulo publicara sus indicadores. **No hay ciclo**: `CM` consume `SP`, `PM` y `MV`, y ninguno de los cuatro consume a `IN`.

---

## 2. Cambios de esquema

**Ninguno.** La sentencia recorre `commission_batches` entera agrupando por `status` y `currency_id`; el número de lotes es el de personas por moneda por cierre, y no justifica un índice nuevo mientras `RN-IN-006` siga en pie.

**`V79__in_permiso_de_lotes_de_comisiones.sql`**: siembra `indicators:read-commission-batches-summary` (`01a10e82-9000-7007-9c4f-5e7ad8000007`: la marca de `V74`, secuencia 7007, y la serie de `IN` donde `V78` la dejó) **solo** a `SUPERADMIN` y `ADMIN`, explícitos, como `V78`. Guardas: catálogo **204**, `SUPERADMIN` 204, `ADMIN` 202, **ningún otro rol** con él, contención (`RN-SEG-003`). Sin auditoría, como `V74`, `V76` y `V78`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `commissions/application` | `CommissionBatchFigures` | **Nuevo** | `List<StatusTotals> byStatus(UUID currencyId)`; `StatusTotals(String status, UUID currencyId, String currencyCode, long batches, BigDecimal amount)` |
| `commissions/domain/repository` | `JpaCommissionBatchFigures` | **Nuevo** | La sentencia de §4.3 |
| `indicators/application` | `CommissionBatchesSummaryResponse` | **Nuevo** | §4.2 |
| `indicators/domain/service` | `GetCommissionBatchesSummaryService` | **Nuevo** | Reparte las filas en los tres estados y suma el total por moneda |
| `indicators/interfaces` | `CommissionIndicatorsController` | **Nuevo** | `GET /api/v1/indicators/commissions/batches/summary`. Un controlador por tanda, como los de ventas y puntos |

**`byStatus` no recibe alcance ni intervalo**, a propósito, como `SalesFigures.unassigned`: que la firma no los admita impide aplicarlos por descuido.

**Los estados viajan como texto**, el código de `BatchStatus`, y no como el `enum`: `BatchStatus` es de `commissions/domain/models`, y `IN` solo puede ver la capa `application` de otro módulo ([`architecture.md` §15.2](../../../architecture.md#152-como-consume-un-modulo-los-datos-de-otro-cierre-de-d-25)). `IN` reconoce los tres que conoce; uno nuevo que `CM` añadiera **no se pierde en silencio**: el servicio lo cuenta en el total y lo registra en el log como desconocido, y la prueba de `T-03` lo cubre.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/commissions/batches/summary` | `indicators:read-commission-batches-summary` |

**Bajo `/commissions/batches`** porque abre la tanda de comisiones, y los lotes son lo primero que se cuenta de ella, como `/sales/lines` lo es de las ventas.

### 4.1 Parámetros

`currencyId`, opcional. **Desde el 07-10-2026, `sellerId` (UUID), `from` y `to` (días ISO)**, opcionales (§ enmienda). **Ni `granularity`**: si llega, se ignora como cualquier parámetro desconocido —que es `CA-IN-076`—, en lugar de responder `400`. Un cliente que pinta todos los indicadores con el mismo periodo no tiene por qué saber cuál no lo usa.

### 4.2 La respuesta

```json
{
  "open":    { "batches": 14, "amounts": [ { "currency": { "id": "…", "code": "USD" }, "amount": 1830.50 } ] },
  "pending": { "batches": 3,  "amounts": [ … ] },
  "paid":    { "batches": 41, "amounts": [ … ] },
  "total":   { "batches": 58, "amounts": [ … ] }
}
```

Esquemas `CommissionBatchesSummary` y `CommissionBatchesBlock`; los importes, `IndicatorAmount`, el de los demás indicadores, ordenados por código de moneda como en `GetSalesSummaryService.importes`. **Sin `period`**: no lo hay, y un `period` nulo invitaría a leerlo como «toda la historia» (`RN-IN-010`), que no es lo que mide.

### 4.3 La sentencia

```sql
SELECT b.status, b.currency_id, c.code, count(*), sum(b.total_amount)
  FROM commission_batches b
  JOIN currencies c ON c.id = b.currency_id
 WHERE (CAST(:moneda AS uuid) IS NULL OR b.currency_id = CAST(:moneda AS uuid))
 GROUP BY b.status, b.currency_id, c.code
```

**`total_amount` y no la suma de las comisiones vivas**: es el valor que el lote tiene y el que se paga (`requirements/cm.md` §7.5), y `RN-CM-046` y `RN-CM-047` lo ajustan en la misma transacción que mueve la comisión, de modo que `CA-IN-077` sale sin recalcular nada. Las centésimas se convierten **una vez, al mapear** (`MinorUnits`, ADR-006).

### 4.4 Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` | El resumen |
| `400` | `currencyId` mal formado (`VAL-001` del manejador común) |
| `401` | Sin token |
| `403` | Sin el permiso |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-commission-batches-summary')")`; la ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`. **Ni `commission-batches:read` ni ningún otro `indicators:` lo abren.**

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. Una sola sentencia: los tres estados y el total salen de la misma lectura, de modo que un cierre que corre a la vez **no puede** dejar un lote contado en dos estados.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md` | `RN-IN-012`, `RN-IN-011` ampliada, el submódulo Comisiones, `CM` en §3 y §8, la ficha y la ruta — **en este pase** |
| `modules.md` | §4 y §5.6: `IN` depende también de `CM`; el mapa de §3 gana `IN --> CM` — **en este pase** |
| `security.md` | §4.4: el permiso declarado — **en este pase**; sembrado — al construir |
| `requirements.md` | La fila — **en este pase** |
| `requirements/cm.md` | §3: `CM` publica `CommissionBatchFigures` para `IN` — al construir |
| `docs/api/index.md` | La ruta — al construir |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Que `IN` lea `commission_batches` | `requirements/in.md` §1.4: la excepción de `IN` no autoriza leer tablas ajenas |
| Reutilizar `GET /commission-batches` y sumar en el cliente | Pagina, y el cliente tendría que recorrer todos los lotes para sumar; además es otra operación con otro permiso (`RN-SEG-014`) |
| Un periodo sobre `period_start` | `spec.md` §2.1 |
| Sumar las comisiones vivas en lugar de `total_amount` | Dos definiciones del valor de un lote; la del lote es la que se paga |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que administración dé el permiso a un vendedor sin saber que lo ve todo | La descripción del permiso y la de la ruta lo dicen |
| Que `CM` añada un estado y el indicador no lo muestre | §3: se cuenta en el total y se registra; la prueba lo fija |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Estados, ceros, total, monedas | Integración, `CommissionBatchesSummaryIT`, con lotes sembrados por SQL en los tres estados y dos monedas | `CA-IN-072` a `CA-IN-075` |
| Foto de hoy: cerrar, pagar, fechas ignoradas | Integración, por las rutas de `CM` | `CA-IN-076` |
| Retirar una comisión de un pendiente | Integración, por la ruta de `RF-CM-022` | `CA-IN-077` |
| Un vendedor con el permiso ve lo mismo | Integración | `CA-IN-078` |
| Permisos y siembra | Integración y los recuentos del catálogo | `CA-IN-079` |
| El reparto por estado, uno desconocido | Unitaria, `GetCommissionBatchesSummaryServiceTest` | §3 |
| Una sola sentencia | Estadísticas de Hibernate | `RNF-PERF-*` |

**Las pruebas que siembran lotes los borran al terminar**, en el orden de las claves —comisiones, lotes, cierres— y sin tocar los de otras suites: el cierre programado y las suites de `CM` crean los suyos, y un recuento exacto se hace **antes y después** de cada cambio, no en valor absoluto.
