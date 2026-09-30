# PLAN — `RF-MV-025` Fijar la tasa de puntos de una moneda

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-025` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 30-09-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 30-09-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**Un `INSERT` en `points_rates`, precedido de una lectura de la vigente.** Si la vigente de la moneda tiene el mismo valor, no se inserta (`FA-001`); si no, se inserta con `valid_from = now()` y `created_by = actor`. **La vigente se resuelve siempre igual**, en un solo sitio (`PointsRates.vigente(moneda, instante)`): la fila de esa moneda con el `valid_from` más reciente que no pase del instante. `RF-MV-026`, `RF-MV-027` y `RF-MV-030` la reutilizan.

**Este requerimiento carga la migración de toda la etapa 3**, como `RF-MV-019` cargó `V49` para la 6: una migración por requerimiento solo añadiría números, y las piezas —tabla, tipo, cuenta, evento, permisos— se prueban juntas.

---

## 2. Cambios de esquema

**La siguiente migración libre al construir** —`V58` o superior: `V57` la tomó la semilla de países el 30-09-2026—, `V5x__mv_puntos.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `points_rates` | Las columnas de [`requirements/mv.md` §7.10](../../../requirements/mv.md) | `RN-MV-050` |
| `fk_points_rates_currency` | `currency_id` → `currencies(id)` `RESTRICT` | Una moneda con tasas no se borra; las monedas no se borran igualmente |
| `fk_points_rates_created_by` | `created_by` → `users(id)` **`ON DELETE CASCADE`** | La lección de `product_links`: una FK sin `ON DELETE` rompe las suites que limpian personas. En producción nadie borra personas |
| `ck_points_rates_valor`, `uq_points_rates_vigencia` | Los de `requirements/mv.md` §7.6 | |
| `ix_points_rates_vigente` | `(currency_id, valid_from DESC)` | La lectura de la vigente es un `LIMIT 1` sobre él |
| `movements.points_rate_id`, `movements.points_amount` | `uuid NULL` → `points_rates(id)` `RESTRICT`; `numeric(14,2) NULL` | `RN-MV-051`, `RF-MV-027` |
| `ck_movements_points` | `(points_rate_id IS NULL) = (points_amount IS NULL)` y `points_amount > 0` | `requirements/mv.md` §7.6 |
| `ck_accounts_kind` | **Se sustituye**: añade `PUNTOS_EMITIDOS` entre las de la empresa | `requirements/mv.md` §4.4 |
| `ck_movement_entries_event` | **Se sustituye**: añade `PAGO` | `RN-MV-052` |
| Tipo `COMPRA_PUNTOS` | `movement_types` con prefijo `PTS`, y su estado del tipo `REGISTRADO` | Como los tres de `V49` |
| Seis permisos | `set-points-rate`, `confirm-points-purchase` y `reject-points-purchase` a `SUPERADMIN` y `ADMIN` explícito; `read-points-rates`, `buy-points` y `list-own-points-purchases` por tipo de rol (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`) | `requirements/mv.md` §6. Como `V49` |

**Sustituir los dos `CHECK`** es `DROP CONSTRAINT` y `ADD CONSTRAINT` en la misma migración. `accounts` y `movement_entries` ya tienen filas, y ninguna viola la versión ampliada: se amplía, no se estrecha. **No se siembra ninguna tasa**: la primera la fija administración, y hasta entonces ninguna moneda vende puntos (`RN-MV-050`).

**Guardas al final**, como `V49`: el tipo y su estado existen, los seis permisos existen, y cada rol del sistema porta los que le tocan.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `PointsRate` | Nuevo | `PointsRate.fijar(moneda, valor, actor, instante)`; valida `VAL-002` |
| `domain/models` | `AccountKind`, `EntryEvent`, `MovementTypeCode` (o donde vivan los códigos) | Ganan `PUNTOS_EMITIDOS`, `PAGO` y `COMPRA_PUNTOS` | |
| `domain/repository` | `PointsRateRepository`, `JpaPointsRateRepository` | Nuevos | `vigente(moneda, instante)`, `vigentes(instante)`, `insert` |
| `domain/service` | `PointsRates` | Nuevo | La lectura de la vigente, compartida por la etapa |
| `domain/service` | `SetPointsRateService` | Nuevo | Validación, moneda por `CurrencyCatalog` (`SP`), `FA-001`, `INSERT`, auditoría |
| `application` | `SetPointsRateRequest`, `PointsRateResponse` | Nuevos | La respuesta: `id`, `currency` (id y código), `pointsPerUnit`, `validFrom` |
| `interfaces` | `PointsRateController` | Nuevo | `POST /movements/points-rates`; `RF-MV-026` añade su `GET` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/points-rates` | `movements:set-points-rate` |

**Cuerpo**: `{ "currencyId", "pointsPerUnit" }`. **Sin `Idempotency-Key`**: repetir la misma petición cae en `FA-001` y no escribe nada. Fijar un precio es idempotente por naturaleza, al revés que abonar un saldo.

| Código | Cuándo |
|---|---|
| `201` | Tasa nueva, vigente desde ahora |
| `200` | Ya era la vigente (`FA-001`) |
| `400` | Datos ausentes o malformados, **todos juntos** (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:set-points-rate` |
| `409` | La moneda está inactiva (`EX-003`) |
| `422` | La moneda no existe (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:set-points-rate')")`. **Un permiso por operación** (`RN-SEG-014`): la consulta de `RF-MV-026` lleva el suyo.

---

## 6. Auditoría

Un `ChangeEvent` sobre `points_rates`, `INSERT`, con la moneda, el valor nuevo y **el valor que regía antes** (nulo si no había). `FA-001` no audita: no cambió nada.

---

## 7. Transaccionalidad

`@Transactional`: la lectura de la vigente y el `INSERT`. Dos peticiones simultáneas sobre la misma moneda insertan las dos con distinto `valid_from`, y eso es correcto (`spec.md` §13). **Si chocan en `uq_points_rates_vigencia`** —el mismo microsegundo—, la segunda responde `409` como conflicto genérico: es un caso que no se prueba, porque no se puede provocar sin trampear el reloj.

---

## 8. Impacto sobre otros módulos

**`SP`**: se lee la moneda —existe, activa— por `CurrencyCatalog`, la interfaz publicada que `MV` ya usa. Ninguna escritura.

**El frontend**: una pantalla de administración. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Una columna `points_per_unit` en `currencies` | Es de `SP`, y editarla borra el histórico que el responsable pidió |
| `valid_to` en cada fila | El mismo hecho guardado dos veces (`requirements/mv.md` §7.10) |
| Insertar aunque el valor no cambie | Llena el histórico de filas iguales y hace inútil la auditoría |
| Una migración por requerimiento | Seis números para una sola etapa; `V49` ya lo hizo en una |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que la vigente se resuelva distinto en cada requerimiento | Un solo componente, `PointsRates`, y un solo índice |
| Que sustituir un `CHECK` rechace filas existentes | Se amplía; la migración aborta si no, y se aplica sobre la base de desarrollo antes de subirla |
| Recuentos del catálogo de permisos | Catálogo 163 → **169**: tocar todos los sitios que lo cuentan (memoria del proyecto, `RF-MV-007`) |

---

## 11. Estrategia de prueba

Integración, `SetPointsRateIT`: `CA-MV-293` a `CA-MV-300`. `CA-MV-294` lee la tabla y comprueba que la anterior **no cambió**. **La migración**: `PermissionIT` y las suites de siembra con el catálogo en 169; una prueba de esquema para `ck_movements_points`, `ck_accounts_kind` y `ck_movement_entries_event`.
