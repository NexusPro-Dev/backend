# PLAN — `RF-IN-008` Consultar el resumen de mis comisiones

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-008` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 07-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye, y por qué así.** Tablas, clases, endpoints, transacciones, pruebas.

    Lo que decide el negocio está en `spec.md` y no se repite aquí.

---

## 1. Enfoque

**El de `RF-IN-007`, con la comisión como unidad y la persona fijada.** `CM` publica una segunda lectura en la misma interfaz, `CommissionBatchFigures` —las comisiones de una persona agregadas por el estado de su lote y su moneda—, e `IN` la presenta en la forma de `RF-IN-007` · `plan.md` §4.2, con `commissions` donde aquel dice `batches`.

**La persona la pone el token** (`AuthenticatedActor`), como `GetPointsSummaryService`, y **no pasa por `SalesScopeResolver`** (`RN-IN-013`): no hay red que resolver. **El periodo sí pasa por `SalesPeriodResolver`**, como `RF-IN-007` desde su enmienda, y se aplica a `commissions.accrued_at`, el mismo campo que filtra `RF-CM-026` (`spec.md` §2.2).

**Sin dependencias nuevas**: `IN` → `CM` existe desde `RF-IN-007`.

---

## 2. Cambios de esquema

**Ninguno.** La sentencia filtra por `user_id` y `accrued_at`, y `ix_commissions_user (user_id, accrued_at DESC, id DESC)`, que `V81` creó para `RF-CM-026`, ya la sirve.

**`V83__in_permiso_de_mis_comisiones.sql`** —`V81` y `V82` son de `CM`—: siembra `indicators:read-own-commissions-summary` (`01a10e82-9000-7009-9c4f-5e7ad8000008`: la marca de `V79`, la secuencia siguiente a la `7008` de `V81`, y la serie de `IN` donde `V79` la dejó) a **todo rol que porte `commission-batches:list-own`**, como `V81`: quien ve sus lotes ve también sus cifras, y la contención (`RN-SEG-003`) se conserva sola. Guardas: catálogo **206**, el permiso exactamente en los roles que portan `list-own`, contención. Sin auditoría, como `V79`.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `commissions/application` | `CommissionBatchFigures` | **Ampliado** | `List<CommissionTotals> commissionsByStatus(CommissionFilter filter)`; `CommissionFilter(UUID userId, UUID currencyId, OffsetDateTime from, OffsetDateTime to)` con `userId` obligatorio; `CommissionTotals(String status, UUID currencyId, String currencyCode, long commissions, BigDecimal amount)` |
| `commissions/domain/repository` | `JpaCommissionBatchFigures` | **Ampliado** | La sentencia de §4.3 |
| `indicators/application` | `OwnCommissionsSummaryResponse` | **Nuevo** | §4.2 |
| `indicators/domain/service` | `GetOwnCommissionsSummaryService` | **Nuevo** | Resuelve el periodo, fija la persona, reparte por estado y suma el total por moneda |
| `indicators/interfaces` | `CommissionIndicatorsController` | **Ampliado** | `GET /api/v1/indicators/commissions/mine/summary` |

**`CommissionFilter` exige la persona** en su constructor: una lectura de comisiones sin persona contaría las de todos, y la firma no debe permitirlo por descuido. **Un registro aparte de `StatusTotals`**, y no el mismo con otro sentido en `batches`: lo que cuenta es otra cosa.

**El reparto por estado es el de `GetCommissionBatchesSummaryService`**: los tres estados siempre, y uno que `IN` no conozca va solo al total y al log.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/commissions/mine/summary` | `indicators:read-own-commissions-summary` |

**Bajo `/commissions/mine`**, como `/commission-batches/mine` en `CM`: lo propio se nombra con `mine`.

### 4.1 Parámetros

`from`, `to` (días ISO) y `currencyId`, opcionales. **Ni `sellerId` ni `granularity`**: si llegan, se ignoran como cualquier parámetro desconocido (`CA-IN-091`).

### 4.2 La respuesta

```json
{
  "period":  { "from": null, "to": "2026-10-07", "zone": "America/Bogota" },
  "open":    { "commissions": 12, "amounts": [ { "currency": { "id": "…", "code": "USD" }, "amount": 84.50 } ] },
  "pending": { "commissions": 3,  "amounts": [ … ] },
  "paid":    { "commissions": 40, "amounts": [ … ] },
  "total":   { "commissions": 55, "amounts": [ … ] }
}
```

Esquemas `OwnCommissionsSummary` y `OwnCommissionsBlock`; `period` es `IndicatorPeriod` y los importes `IndicatorAmount`, ordenados por código de moneda.

### 4.3 La sentencia

```sql
SELECT b.status, b.currency_id, c.code, count(*), sum(k.commission_amount)
  FROM commissions k
  JOIN commission_batches b ON b.id = k.batch_id
  JOIN currencies c ON c.id = b.currency_id
 WHERE k.user_id = :persona
   AND (CAST(:moneda AS uuid) IS NULL OR b.currency_id = CAST(:moneda AS uuid))
   AND (CAST(:desde AS timestamptz) IS NULL OR k.accrued_at >= CAST(:desde AS timestamptz))
   AND (CAST(:hasta AS timestamptz) IS NULL OR k.accrued_at <  CAST(:hasta AS timestamptz))
 GROUP BY b.status, b.currency_id, c.code
```

**La moneda es la del lote**: una comisión no tiene moneda propia, y un lote es de una sola (`RN-CM-028`). **El estado es el del lote donde la comisión está hoy**, de modo que `CA-IN-093` sale sin hacer nada. Las centésimas se convierten **una vez, al mapear** (`MinorUnits`).

### 4.4 Códigos de respuesta

| Código | Cuándo |
|---|---|
| `200` | El resumen, aunque sea de ceros |
| `400` | `currencyId` o una fecha mal formados (`VAL-001`); `from` posterior a `to` (`VAL-002`) |
| `401` | Sin token |
| `403` | Sin el permiso |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-own-commissions-summary')")`; la ruta en `PERMISO_DE_CADA_OPERACION` de `EndpointPermissionsIT`. **Ni `commission-batches:list-own`, ni `commission-batches:list-own-commissions`, ni otro `indicators:` lo abren** (`RN-SEG-014`).

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. Una sola sentencia: los tres estados y el total salen de la misma lectura.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md` | `RN-IN-013`, el submódulo Comisiones, `CM` en §3, la ficha y la ruta — **en este pase** |
| `security.md` | §4.4: el permiso declarado — **en este pase**; sembrado — al construir |
| `requirements.md` | La fila — **en este pase** |
| `requirements/cm.md` | §3: `CommissionBatchFigures` gana la lectura de comisiones por persona — al construir |
| `docs/api/index.md` | La ruta — al construir |
| Pruebas de roles | Los recuentos del catálogo, `SystemRolesSeedIT` y `RoleDetailIT`, que fijan los permisos de `AGENTE` y `DIRECTOR` — al construir |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Que lo calcule el cliente con `RF-CM-026` | Pagina: tendría que recorrer todas mis comisiones para sumar |
| Un `sellerId` con alcance de red | `spec.md` §2.1: cada nivel tiene su comisión, y la de mi agente no es mía |
| Fechas sobre el periodo del lote, como `RF-IN-007` | `spec.md` §2.2: aquí la unidad es la comisión, y su fecha es la de `RF-CM-026` |
| Reutilizar `commission-batches:list-own` | `RN-SEG-014`: listar mis lotes y contar mis comisiones son dos operaciones |
| Un `CommissionFigures` nuevo en `CM` | Una sola interfaz de cifras por dueño basta; dos para el mismo módulo serían dos puertas a lo mismo |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que una lectura sin persona cuente las de todos | `CommissionFilter` no admite persona nula |
| Que las cifras no cuadren con la lista de `RF-CM-026` | La misma fecha, `accrued_at`, y la misma inclusión de días |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Estados, total, ceros, lo de otro | Integración, `OwnCommissionsSummaryIT`, con lotes y comisiones sembrados por SQL para dos personas y dos monedas | `CA-IN-090` a `CA-IN-092` |
| Cerrar, pagar y retirar | Integración, cambiando los lotes por SQL como `CommissionBatchesSummaryIT` | `CA-IN-093` |
| Moneda y fechas | Integración | `CA-IN-094`, `CA-IN-095` |
| Permisos y siembra | Integración y los recuentos del catálogo | `CA-IN-096` |
| El reparto, un estado desconocido, la persona obligatoria | Unitaria, `GetOwnCommissionsSummaryServiceTest` | §3 |
| Una sola sentencia | Estadísticas de Hibernate | `RNF-PERF-*` |
