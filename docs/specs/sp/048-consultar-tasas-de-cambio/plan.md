# PLAN — `RF-SP-048` Consultar las tasas de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-048` |
| Especificación | [`spec.md`](spec.md), aprobada el 07-09-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 07-09-2026 |

---

## 1. Enfoque

**Dos sentencias y ninguna depende del número de filas**: la página —con las dos monedas resueltas en el mismo `JOIN`— y el conteo. Resolver las monedas fila a fila sería el problema `N+1` con otro nombre.

**El predicado se genera en un solo sitio** y lo usan la página y el conteo. Escritos por separado divergen, y la divergencia se manifiesta como un total que no coincide con lo que se ve.

Hereda entero el diseño de `RF-PM-002`, que es el listado más parecido del sistema, y **no repite su argumentación**: se cita.

## 2. Cambios de esquema

### 2.1 `V65` ya trae los índices

No hay migración propia. La tabla la crea `RF-SP-047`, y **los índices de lectura van con ella**:

| Índice | Sobre | Por qué |
|---|---|---|
| El del `EXCLUDE` | `gist (source, target, daterange)` | Lo crea la restricción, y **sirve también para leer**: el filtro por par y por vigencia es exactamente su forma |
| `ix_exchange_rates_listado` | `(valid_from DESC, id DESC)` | El orden por omisión, con desempate |

**No se indexan `is_active` ni las monedas por separado**: dos valores no dan selectividad, y el par ya está cubierto por el índice del `EXCLUDE`.

## 3. Componentes afectados

| Capa | Elemento |
|---|---|
| `application` | `ListExchangeRatesRequest`, `ExchangeRateItem`, `ExchangeRatePageResponse` |
| `domain/repository` | `ExchangeRateQueryRepository` y su adaptador |
| `domain/service` | `ListExchangeRatesService` |
| `interfaces` | `GET /api/v1/exchange-rates` sobre el controlador de `RF-SP-047` |

**Puerto de lectura separado del de escritura**, como en `PM`: lo que devuelve no son agregados sino proyecciones, y mezclarlos invita a cargar la entidad para responder una consulta.

## 4. Contrato de API

`GET /api/v1/exchange-rates?sourceCurrencyId=&targetCurrencyId=&status=&validOn=&includeDeleted=&sort=&page=&size=`

- **`validOn` es el parámetro que justifica el endpoint.** Se traduce a `valid_from <= :fecha AND (valid_to IS NULL OR valid_to >= :fecha)`, **con los dos extremos incluidos**.
- **`status` llega como texto y no como enumerado**, por lo mismo que en `RF-PM-002`: enlazarlo como enumerado dejaría que Spring rechazara el valor fuera de dominio **antes** del caso de uso, y el rechazo saldría solo en lugar de junto a los demás.
- **`sort` es un dominio cerrado** —`validFrom`, `price`— con `,asc` o `,desc`. Por omisión, `validFrom` descendente con el identificador como desempate: sin un orden **total**, dos tasas del mismo día pueden repetirse o saltarse entre páginas.
- **`includeDeleted` es `Boolean` y no `boolean`**: un primitivo en un `@ModelAttribute` hace que la petición **sin el parámetro** falle con `400`. Es el defecto que el catálogo de monedas ya tuvo que corregir.
- El conteo es **exacto**, no acotado. Esta tabla crece, y el disparador de cambiarlo queda anotado: **si llegara a cientos de miles**, pasa a `BoundedCount` como los listados de auditoría.

!!! danger "Los dos intervalos tienen que ser el mismo, y esto es lo único que hay que vigilar aquí"

    El `EXCLUDE` de `RN-SP-032` usa `daterange(valid_from, valid_to, '[]')` — **cerrado por los dos lados**. Si `validOn` se escribiera con `<` en vez de `<=`, habría días que la restricción considera cubiertos y esta consulta declara libres.

    No fallaría nada: devolvería una colección vacía para un día que sí tiene tasa, y quien lo viera pensaría que falta declararla. `CA-SP-546` prueba **los dos extremos**.

## 5. Autorización

`@PreAuthorize("hasAuthority('exchange-rates:read')")`. **No es `currencies:read`**: aquel abre el catálogo de monedas, que es otro recurso y otra decisión.

## 6. Auditoría

**No audita.** Una consulta de catálogo no es un evento de seguridad; el único listado que se audita a sí mismo es el de seguridad de `RF-SP-014`, donde mirar **es** información.

## 7. Transaccionalidad

`@Transactional(readOnly = true)`. Declara ante el motor que no escribe, y deja escrito que **la consulta no reserva nada**.

## 8. Impacto sobre otros módulos

Ninguno.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| **Un endpoint `/current` que devuelva la tasa de hoy** | Es `validOn` con la fecha de hoy y un nombre distinto. Dos rutas para la misma pregunta acaban con dos predicados que divergen |
| **Devolver la tasa más cercana cuando el día no tiene ninguna** | Es inventar un dato. `FA-003` lo rechaza explícitamente |
| **Sin paginar, como `RF-PM-007`** | Aquella no pagina porque la oferta está acotada por la cadena de membresías; esta tabla **crece sin techo** — una tasa por par y periodo, indefinidamente |
| **Resolver las monedas con el puerto, fila a fila** | `N+1` con otro nombre. Viajan en el `JOIN`, y la frontera se mantiene donde importa: **ninguna regla se decide con ese `JOIN`** |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | **`validOn` con intervalo abierto** mientras el `EXCLUDE` lo usa cerrado | `CA-SP-546` prueba el primer y el último día de la vigencia |
| 2 | El precio **se redondea al serializar** a la escala de alguna moneda | `CA-SP-551` lo comprueba: la tasa **no está expresada en ninguna moneda** y `ProductPrice` no aplica aquí |
| 3 | El total y la página **divergen** | El predicado se genera en un solo sitio, y una prueba cuenta las sentencias |

## 11. Estrategia de prueba

- **Integración**: los diez criterios de `spec.md` §12.
- **De número de sentencias**: la consulta cuesta **dos** con todos los filtros puestos.
- **De paginación estable**: se recorren todas las páginas con varias tasas del mismo `valid_from` y no falta ni se repite ninguna. Sin el desempate por identificador esta prueba falla, y es la única que lo detecta.
