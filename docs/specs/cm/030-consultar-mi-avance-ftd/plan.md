# PLAN — `RF-CM-030` Consultar mi avance de FTD en el periodo abierto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-030` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 17-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 17-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**La liquidación de [`RF-CM-020`](../020-liquidar-comisiones-afftrack/plan.md), sin escribir y para una persona.** Las piezas ya existen y se reutilizan tal cual:

```
ftd       = ProductCatalog.ftdProductIds()                       — vacío → lista vacía
ahora     = BusinessCalendar.ahora()
para cada línea de AfftrackSettlementRepository.newFtdLines(ftd, ahora):
    cadena = SupervisorChain.chainAt(línea.sellerId, línea.deliveredAt)
    si yo ∈ cadena → nivel 0: directo; nivel > 0: de la red   (una vez por línea, como el cierre)
remanente = AfftrackSettlementRepository.carriedOut(ftd), solo mis filas
escalas   = AfftrackSettlementRepository.scales({yo}, ftd, hoy)  — hoy en America/Bogota
productos = los que tienen nuevos, remanente o escala
por producto: AfftrackTierPicker.elegir(disponibles, escala) y AfftrackTierPicker.siguiente(disponibles, escala)
periodStart   = CommissionClosingRepository.lastCut()            — nulo sin cierres (RN-CM-055)
nextClosingAt = ClosingSchedule.enabled() ? ClosingSchedule.next(ahora) : nulo
```

**El mismo código que paga es el que estima**, y esa es la garantía de `RN-CM-056`: si el cierre cambia de regla, el avance cambia con él. **Lo único nuevo de dominio es `AfftrackTierPicker.siguiente`**: el escalón de menor límite estrictamente mayor que los disponibles, y cuántos faltan.

**«Hoy» y no el último día del periodo** (`spec.md` §2.1): `RF-CM-020` usa `calendario.diaDe(corte − 1 µs)`; aquí `calendario.hoy()`.

---

## 2. Cambios de esquema

**`V117__cm_mi_avance_ftd.sql`** siembra `afftrack-settlements:read-own-progress` —identificador literal `01a10e82-9000-701f-9c4f-5e7ad6000036`: la marca v7 de `V79`, la secuencia siguiente a la de `V115` y la serie de `CM` donde `V87` la dejó— a **todo rol que porte `commission-batches:list-own`**, como `V81`. Guardas: catálogo **253**, el permiso en exactamente esos roles y la contención de `RN-SEG-003`. **Sin tablas ni índices**: las consultas son las del cierre.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/service` | `AfftrackTierPicker` | Gana `siguiente(disponibles, escala)` → `Optional<Next>` con el escalón y `faltan` | §1 |
| `domain/service` | `GetOwnAfftrackProgressService` | Nuevo, de solo lectura | §1, §7 |
| `application` | `AfftrackProgressResponse` | Nuevo, con `@Schema(name)` en él y en cada registro anidado | §4 |
| `interfaces` | `AfftrackSettlementController` | Gana `GET /mine/progress` | |

**No cambian** `AfftrackSettlementRepository`, `SupervisorChain`, `ClosingSchedule` ni `CommissionClosingRepository`: se usan como están. La referencia al producto y su moneda es la de `RF-CM-016` (`AfftrackRateResponse.ProductRef`), construida como la construye ese listado.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/afftrack-settlements/mine/progress` | `afftrack-settlements:read-own-progress` |

```json
{
  "periodStart": "2026-10-01T05:00:00Z",
  "nextClosingAt": "2026-11-01T05:00:00Z",
  "products": [
    {
      "product": { "id": "…", "code": "BECA", "name": "Beca", "currency": { "id": "…", "code": "USD", "decimalPlaces": 2 } },
      "carriedIn": 3,
      "ownFtds": 12,
      "networkFtds": 40,
      "newFtds": 52,
      "available": 55,
      "reachedTier": { "threshold": 50, "amountPerFtd": 2.00, "source": "ROL", "estimatedAmount": 100.00 },
      "remainder": 5,
      "nextTier": { "threshold": 60, "amountPerFtd": 2.50, "source": "ROL", "missing": 5 }
    }
  ]
}
```

| Código | Cuándo |
|---|---|
| `200` | Siempre, también con la lista vacía |
| `401` / `403` | Sin token / sin el permiso |

**`ownFtds` y `networkFtds` se llaman como en [`RF-CM-021`](../021-consultar-liquidaciones-afftrack/plan.md)**, y `carriedIn` también: el frontend lee el avance y la liquidación con los mismos nombres. **`estimatedAmount` y no `amount`**: el nombre dice que es una estimación (`RN-CM-056`). `reachedTier` y `nextTier` viajan **nulos** cuando no hay (`@JsonInclude(ALWAYS)`, como `AfftrackSettlementItem`). Orden de `products`: por código de producto. **`/mine/progress` no choca con nada**: `/afftrack-settlements` no tiene `/{id}`.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('afftrack-settlements:read-own-progress')")`; en `PERMISO_DE_CADA_OPERACION`. **La persona es la del token** y no hay parámetro de persona (`CA-CM-408`). **No reutiliza `afftrack-settlements:read`** (`RN-SEG-014`): es la lectura administrativa de lo liquidado, de todas las personas.

---

## 6. Auditoría

Ninguna: es una lectura.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. **No toma ningún bloqueo**: un cierre que corra a la vez no espera a la consulta ni la consulta al cierre. Lee lo que vea (`spec.md` §13).

---

## 8. Impacto sobre otros módulos

Ninguno: `ProductCatalog.ftdProductIds()` y `SupervisorChain` ya se publican para el cierre.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una sentencia propia que cuente mis FTD con un `CTE` recursivo sobre el historial de superiores | Duplicaría la regla del cierre en SQL; si una de las dos cambia, el avance mentiría. Queda como mejora si la medición lo pide (§10) |
| Filtrar las líneas por mi red de hoy | Es la cadena del día de la activación (`RN-CM-042`), no la de hoy: daría mal `CA-CM-405` |
| Ponerlo en `IN` | La regla vive aquí (`requirements/cm.md` §5.13) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| **Una consulta de cadena por cada FTD no contado de toda la empresa**, no solo de la persona: crece con los FTD del periodo | Es lo que hace el cierre una vez al mes; aquí, por consulta. Con los volúmenes de hoy —cientos al mes— cabe. Se deja un `shortcut:` en el servicio, y si la medición lo pide se cambia por la sentencia de §9, con `AfftrackProgressIT` como red |
| Los recuentos del catálogo, 252 → 253 y `ADMIN` 250 → 251 | Las suites que lo cuentan: `PermissionIT`, `PermissionsSeedIT`, `JpaPermissionQueryRepositoryIT`, `ListPermissionsServiceIT`, `TeamsPermissionsSeedIT`, `SaleLinesPermissionSeedIT` y `SignalsIT`. Y, porque va a roles `VENDEDOR`, `SystemRolesSeedIT` y `RoleDetailIT` |

---

## 11. Estrategia de prueba

**`AfftrackTierPickerTest`**, unitaria, gana los casos de `siguiente`: 55, 40 y 130 contra 50/60 (`CA-CM-400`, `CA-CM-401`). **`AfftrackProgressIT`**, por HTTP, con ventas, líneas, superiores y escalas escritos por SQL con `AfftrackFixtures`, como `AfftrackSettlementIT`: `CA-CM-399` a `CA-CM-408`; `CA-CM-406` contando filas de `afftrack_settlements`, `afftrack_ftds`, `commissions` y `commission_batches` antes y después; `CA-CM-405` con un superior cerrado en `user_supervisors`; `CA-CM-407` con y sin un cierre escrito. **`CA-CM-409`**: `EndpointPermissionsIT` y la guarda de `V117`, más un `403` con un token que solo porta `afftrack-settlements:read`.
