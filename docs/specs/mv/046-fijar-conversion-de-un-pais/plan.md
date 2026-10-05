# PLAN — `RF-MV-046` Fijar la conversión de un país

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-046` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 05-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 05-10-2026 |

!!! info "Qué va en este documento"

    **Cómo se construye.** Esquema, componentes, contrato, autorización y pruebas.

    **Prueba de pertenencia:** si un cambio de negocio lo invalidaría, pertenece a `spec.md`.

---

## 1. Enfoque

**El de `RF-MV-025`, por país y con dos precios**: un `INSERT` en `country_conversion_rates`, precedido de la lectura de la vigente. Si la vigente del país tiene la misma moneda local y los mismos dos precios —comparados por valor y no por escala—, no se inserta (`FA-001`); si no, se inserta con `valid_from = now()`, `created_by = actor` y `base_currency_id` = la moneda por omisión de ese instante. **La vigente se resuelve en un solo sitio**, `CountryConversionRateRepository.current(país, instante)`, que reutilizarán `RF-MV-047` y la pasarela local.

**Este requerimiento carga la migración** de la conversión, con la tabla y los dos permisos, como `RF-MV-025` cargó la de la etapa 3.

---

## 2. Cambios de esquema

`V67__mv_conversion_por_pais.sql`:

| Elemento | Definición | Por qué |
|---|---|---|
| `country_conversion_rates` | Las columnas de [`requirements/mv.md` §7.15](../../../requirements/mv.md) | `RN-MV-062` |
| `fk_country_conversion_rates_country` | `country_id` → `countries(id)` `RESTRICT` | Un país con conversiones no se borra |
| `fk_country_conversion_rates_currency`, `fk_country_conversion_rates_base_currency` | → `currencies(id)` `RESTRICT` | Las monedas no se borran |
| `fk_country_conversion_rates_created_by` | `created_by` → `users(id)` **`ON DELETE CASCADE`** | Como `points_rates`: una FK sin `ON DELETE` rompe las suites que limpian personas. En producción nadie borra personas |
| `ck_country_conversion_rates_precios`, `ck_country_conversion_rates_monedas`, `uq_country_conversion_rates_vigencia` | Los de `requirements/mv.md` §7.6 | |
| `ix_country_conversion_rates_vigente` | `(country_id, valid_from DESC)` | La lectura de la vigente es un `LIMIT 1` sobre él |
| Dos permisos | `movements:set-conversion-rate` a `SUPERADMIN` y `ADMIN` explícito; `movements:read-conversion-rates` por tipo de rol (`FUNCIONARIO`, `VENDEDOR`, `CONSUMIDOR`) | `requirements/mv.md` §6. Como `V58` |

**Los dos precios son `numeric(14,4)`**: no son importes y no pasan a centésimas ([`ADR-006`](../../../architecture/ADR-006-importes-en-unidades-minimas.md)), como `points_per_unit` y `exchange_rates.price`. **No se siembra ninguna conversión**: la primera la fija administración. **Guardas al final**: los dos permisos existen y cada rol del sistema porta los que le tocan.

---

## 3. Componentes afectados

| Capa | Componente | Cambio | Nota |
|---|---|---|---|
| `domain/models` | `CountryConversionRate` | Nuevo | `fijar(país, moneda, base, cobro, retiro, actor, instante)` y `mismaQue(moneda, cobro, retiro)` |
| `domain/repository` | `CountryConversionRateRepository`, `JpaCountryConversionRateRepository` | Nuevos | `current(país, instante)`, `currentAll(instante)` —para `RF-MV-047`— e `insert` |
| `domain/service` | `CountryConversionRateService` | Nuevo | `set` y, con `RF-MV-047`, `current`. País por `CountryCatalog` y monedas por `CurrencyCatalog` (`SP`) |
| `application` | `SetCountryConversionRateRequest`, `CountryConversionRateResponse` | Nuevos | La respuesta: `id`, `country` (id, código, nombre), `currency` y `baseCurrency` (id, código, decimales), `payInPrice`, `payoutPrice`, `validFrom` |
| `interfaces` | `CountryConversionRateController` | Nuevo | `POST /movements/conversion-rates`; `RF-MV-047` añade su `GET` |

**Fijar y consultar en un servicio y un controlador**, como quedaron los de la tasa de puntos (`RF-MV-025` · `tasks.md` §3.1).

---

## 4. Contrato de API

| Verbo | Ruta | Permiso |
|---|---|---|
| `POST` | `/api/v1/movements/conversion-rates` | `movements:set-conversion-rate` |

**Cuerpo**: `{ "countryId", "currencyId", "payInPrice", "payoutPrice" }`. **Sin `Idempotency-Key`**: repetir la petición cae en `FA-001`.

| Código | Cuándo |
|---|---|
| `201` | Conversión nueva, vigente desde ahora |
| `200` | Ya era la vigente (`FA-001`) |
| `400` | Datos ausentes o malformados, **todos juntos** (`EX-001`) |
| `401` / `403` | Sin token / sin `movements:set-conversion-rate` |
| `409` | País o moneda inactivos (`EX-003`), o la moneda local es la base (`EX-004`) |
| `422` | El país o la moneda no existen (`EX-002`) |

---

## 5. Autorización

`@PreAuthorize("hasAuthority('movements:set-conversion-rate')")`. La consulta de `RF-MV-047` lleva el suyo (`RN-SEG-014`).

---

## 6. Auditoría

Un `ChangeEvent` sobre `country_conversion_rates`, `CREATE`, con el país, la moneda local, la base, los dos precios nuevos y **los que regían antes** (nulos si no había). `FA-001` no audita.

---

## 7. Transaccionalidad

`@Transactional`: la lectura de la vigente y el `INSERT`. Dos peticiones simultáneas insertan las dos con distinto `valid_from`, y eso es correcto (`spec.md` §13). **Si chocan en `uq_country_conversion_rates_vigencia`** —el mismo microsegundo—, la segunda responde `409` como conflicto genérico: no se prueba, como en `RF-MV-025`.

---

## 8. Impacto sobre otros módulos

**`SP`**: se leen el país —existe, activo— por `CountryCatalog` y las monedas —la local y la por omisión— por `CurrencyCatalog`, interfaces publicadas que ya existen. Ninguna escritura.

**El frontend**: una pantalla de administración. **El contrato OpenAPI se regenera y la prosa de la `@Operation` se relee**.

---

## 9. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Reutilizar `exchange_rates` de `SP` | Una sola tasa por par de monedas, para mostrar precios; esta lleva dos y la elige el país (`requirements/mv.md` §4.9) |
| Una fila por precio, con un tipo | Descartada por el responsable: los dos se revisan juntos |
| La moneda local en `countries` | Es de `SP` y obligaría a una migración de otro módulo para algo que solo usa la pasarela |
| No guardar la moneda base | Una fila antigua dejaría de decir de qué convertía si la moneda por omisión cambiara |

---

## 10. Riesgos

| Riesgo | Mitigación |
|---|---|
| Que la vigente se resuelva distinto aquí y en la pasarela | Un solo método, `current`, y un solo índice |
| Recuentos del catálogo de permisos | Catálogo 179 → **181**: tocar todos los sitios que lo cuentan (`PermissionIT` y las suites de siembra) |
| Que no haya moneda por omisión | `CurrencyCatalog.findDefault` vacío es un defecto de configuración: `IllegalStateException`, no un error de negocio |

---

## 11. Estrategia de prueba

Integración, `CountryConversionRatesIT`: `CA-MV-548` a `CA-MV-556`. `CA-MV-549` lee la tabla y comprueba que la anterior **no cambió**. **La migración**: `PermissionIT` con el catálogo en 181, y una prueba de esquema para los dos `CHECK`.
