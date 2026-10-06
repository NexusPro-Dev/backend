# PLAN — `RF-IN-005` Consultar el resumen de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-IN-005` |
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

**Es el resumen de ventas con otra fuente.** Del módulo se reutiliza todo lo que no es la suma: el periodo (`SalesPeriodResolver`), la traducción del alcance (`SalesScopeResolver`, que da un conjunto de personas o todo) y el corte a ceros fuera del alcance. Lo nuevo es **qué se suma**: los asientos de las cuentas `PUNTOS` de las personas, que son de `MV`.

**Se cuenta sobre el libro de asientos y no sobre los movimientos.** Cada punto que entra o sale de una persona es un asiento en su cuenta `PUNTOS` (`requirements/mv.md` §7.9), y **el evento del asiento ya dice qué clase de hecho fue**: `ABONO` es una compra cobrada, `PAGO` una venta pagada con puntos, `AJUSTE` un ajuste —con el signo diciendo si sumó o restó—. Contar los movimientos obligaría a mirar el estado de la compra, el método de pago de la venta y el signo del ajuste, tres reglas que el asiento ya resolvió al escribirse. Y el instante del asiento **es** cuándo se movieron los puntos (`spec.md` §2.1).

**`MV` publica `PointsFigures`** en su capa `application`, con la forma de `SalesFigures`: recibe un conjunto de personas o todo, un intervalo y una moneda, y devuelve sumas. El saldo lo devuelve la misma interfaz, en otra sentencia, sobre `accounts.balance`, que es la copia de la suma de los asientos (`RN-MV-041`).

---

## 2. Cambios de esquema

**Ninguna tabla, columna ni índice.** `ix_movement_entries_account` (`V49`) —`(account_id, created_at DESC, id DESC)`— sirve el filtro por cuenta y periodo.

**Una migración de datos, `V76__in_permiso_de_puntos.sql`**, que siembra `indicators:read-points-summary` con la serie de `IN` (`5e7ad8000005`) y lo da **por tipo de rol** a `FUNCIONARIO` y `VENDEDOR`, como `V74`. Guardas: catálogo **197**, `SUPERADMIN` 197, `ADMIN` 195, ningún `CONSUMIDOR` con ningún `indicators:` y contención (`RN-SEG-003`). El número lo cedió la sesión del segundo factor, que no lleva migración en `RF-SP-073` a `RF-SP-077`.

---

## 3. Componentes afectados

### 3.1 En `MV`

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `PointsFigures` | **Nuevo** | `List<Movement> movements(Set<UUID> holders, Interval, UUID currencyId)` y `List<Balance> balances(Set<UUID> holders, UUID currencyId)`; `holders` nulo es todo. `Movement(currencyId, currencyCode, kind, points, count)` con `kind` = `PURCHASED` \| `REDEEMED` \| `ADDED` \| `REMOVED`; `Balance(currencyId, currencyCode, points)` |
| `domain/repository` | `JpaPointsFigures` | **Nuevo** | Dos sentencias nativas (§4.4) |

**`Interval` es el de `SalesFigures`**, que ya es de `MV` y no tiene nada de ventas: se reutiliza en lugar de declarar otro igual.

### 3.2 En `IN`

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `application` | `PointsSummaryResponse` | **Nuevo** | §4.2 |
| `domain/service` | `GetPointsSummaryService` | **Nuevo** | Periodo, alcance con corte, las dos lecturas, y la unión por moneda |
| `interfaces` | `PointsIndicatorsController` | **Nuevo** | `GET /api/v1/indicators/points/summary`; un controlador por tanda, como el de ventas |

**El alcance es de personas y no de vendedores**, pero el conjunto es el mismo: `SalesScopeResolver` devuelve «todo» o «estas personas» —él y su red—, y aquí esas personas son los **titulares** de las cuentas en lugar de los vendedores de las líneas. Se reutiliza tal cual; su nombre habla de ventas porque nació con ellas, y no se renombra por una segunda tanda.

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `GET` | `/api/v1/indicators/points/summary` | `indicators:read-points-summary` |

### 4.1 Parámetros

`from`, `to` y `currencyId` como en `RF-IN-001` §4.1; **`userId`** en lugar de `sellerId`, porque aquí la persona es la titular de los puntos y no una vendedora. Mismo corte a ceros fuera del alcance.

### 4.2 La respuesta

```json
{
  "period": { "from": "2026-09-01", "to": "2026-09-30", "zone": "America/Bogota" },
  "currencies": [
    { "currency": { "id": "…", "code": "USD" },
      "purchased": { "points": 1500.00, "count": 3 },
      "redeemed":  { "points": 400.00,  "count": 2 },
      "added":     { "points": 50.00,   "count": 1 },
      "removed":   { "points": 0.00,    "count": 0 },
      "balance": 1150.00 }
  ]
}
```

Ordenadas por código de moneda. **Todo en positivo** (`spec.md` §6.2). Esquemas `PointsSummary`, `PointsSummaryCurrency` y `PointsFlow`; la moneda es `IndicatorCurrency` y el periodo `IndicatorPeriod`.

### 4.3 Códigos de respuesta

Los de `RF-IN-001` §4.3, con su permiso. **Ni `indicators:read-sales-*` ni `movements:read-user-balances` lo abren.**

### 4.4 Las sentencias

```sql
-- El periodo: los asientos de las cuentas PUNTOS de personas, por moneda, evento y signo.
SELECT a.currency_id, c.code, e.event, e.amount > 0, sum(e.amount), count(DISTINCT e.movement_id)
  FROM movement_entries e
  JOIN accounts a ON a.id = e.account_id AND a.kind = 'PUNTOS' AND a.user_id IS NOT NULL
  JOIN currencies c ON c.id = a.currency_id
 WHERE e.created_at >= :desde AND e.created_at < :hasta
   [AND a.user_id IN (:titulares)] [AND a.currency_id = :moneda]
 GROUP BY 1, 2, 3, 4

-- El saldo de hoy.
SELECT a.currency_id, c.code, sum(a.balance)
  FROM accounts a JOIN currencies c ON c.id = a.currency_id
 WHERE a.kind = 'PUNTOS' AND a.user_id IS NOT NULL
   [AND a.user_id IN (:titulares)] [AND a.currency_id = :moneda]
 GROUP BY 1, 2
```

`ABONO` positivo → `PURCHASED`; `PAGO` negativo → `REDEEMED`; `AJUSTE` positivo → `ADDED`, negativo → `REMOVED`. **Un evento que no sea ninguno de esos se rechaza en el mapeo** con un fallo, en lugar de ignorarse: si mañana algo nuevo mueve puntos, este indicador tiene que enterarse, y una cifra que se calla un movimiento no falla, miente.

---

## 5. Autorización

`@PreAuthorize("hasAuthority('indicators:read-points-summary')")`; la ruta en `PERMISO_DE_CADA_OPERACION`.

---

## 6. Auditoría

Ninguna.

---

## 7. Transaccionalidad

`@Transactional(readOnly = true)`: las dos sentencias describen el mismo instante, y es lo que hace que `CA-IN-044` cuadre.

---

## 8. Impacto sobre otros módulos y documentos

| Documento | Cambio |
|---|---|
| `requirements/in.md` | Submódulo Puntos, `RN-IN-009`, `RF-IN-005`, su permiso y su ruta — **en este pase** |
| `modules.md` | §5.6: la ficha gana el submódulo Puntos — **en este pase** |
| `requirements.md` | La fila de `RF-IN-005` — **en este pase** |
| `security.md` | §4.4: el permiso, declarado — **en este pase**; sembrado — al construir |
| `requirements/mv.md` | §3: `MV` publica `PointsFigures` — al construir |
| `architecture.md` | §15.2: `PointsFigures` — al construir |
| `docs/api/index.md` | La ruta — al construir |

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Contar sobre `movements` (`COMPRA_PUNTOS` confirmadas, ventas con `POINTS`, `AJUSTE_PUNTOS`) | Tres reglas para lo que el evento del asiento ya dice; y el periodo sería el de la venta y no el del movimiento de puntos |
| El saldo desde la cuenta `PUNTOS_EMITIDOS` de la empresa | Da el total de la plataforma y no el de un alcance |
| Ignorar los eventos que no se esperan | Una cifra que se calla un movimiento cuadra mal sin fallar (§4.4) |
| Ponerlo dentro del resumen de ventas | Otro permiso: se puede querer dar uno sin el otro (`RN-IN-001`) |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Una red grande hace un `IN (…)` largo | El de `RF-MV-015` §10 |
| Que aparezca un evento nuevo sobre `PUNTOS` | El mapeo falla en lugar de callarlo (§4.4), y `CA-IN-044` lo detectaría |

---

## 11. Estrategia de prueba

| Qué | Nivel | Por qué ahí |
|---|---|---|
| Compra cobrada, pendiente y rechazada; venta pagada con puntos; ajustes de los dos sentidos | Integración, `PointsSummaryIT`, con los movimientos hechos **por las rutas de `MV`** | `CA-IN-041` a `CA-IN-043`: los asientos los escribe `MV`, no la prueba |
| Saldo de hoy y la igualdad con un periodo que lo cubre todo | Integración | `CA-IN-044` |
| Dos monedas | Integración | `CA-IN-045` |
| Alcance y filtros | Integración, con un director, sus agentes y otra rama | `CA-IN-046`, `CA-IN-047` |
| Compra pedida un día y cobrada otro | Integración | `CA-IN-048` |
| Permisos | Integración | `CA-IN-049` |
| `V76` | `PermissionsSeedIT`, `SystemRolesSeedIT` y los recuentos del catálogo | Las guardas |
