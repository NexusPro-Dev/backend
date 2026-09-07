# TASKS — `RF-SP-048` Consultar las tasas de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-048` |
| Especificación | [`spec.md`](spec.md) |
| Plan | [`plan.md`](plan.md), aprobado el 07-09-2026 |
| Estado | **En revisión** |
| Issue | Pendiente de crear |
| Rama | `feature/tasas-de-cambio` |
| Autor | Responsable técnico |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | El índice de listado `(valid_from DESC, id DESC)` **dentro de `V62`**, con la tabla | `RF-SP-047 · T-01` | `mvn flyway:info` lo lista. No hay migración propia: el índice es de la tabla y va con ella | Pendiente |
| `T-02` | `application/ExchangeRateSortField`: dominio **cerrado** con `validFrom` y `price` | — | Unitaria: un campo fuera de la lista no es representable, y el analizador devuelve `VAL-003` en lugar de un valor por omisión | Pendiente |
| `T-03` | `application/ListExchangeRatesRequest`: los seis filtros, la paginación y el orden, con `includeDeleted` en `Boolean` | `T-02` | La petición **sin** `includeDeleted` no falla, y los `400` se devuelven **juntos** | Pendiente |
| `T-04` | `application`: `ExchangeRateItem` y la envoltura `PageResponse` con `totalIsExact` en `true` | — | Compila y serializa; el precio sale con **ocho decimales** | Pendiente |
| `T-05` | `ExchangeRateQueryRepository` y su adaptador: **una sentencia**, predicado dinámico, **dos `JOIN`** a `currencies` y orden total con `id` de desempate | `T-01`, `T-04` | La consulta cuesta **dos** sentencias con y sin filtros, también con los seis puestos | Pendiente |
| `T-06` | **El predicado de `validOn`**, con los dos extremos incluidos: `valid_from <= :fecha AND (valid_to IS NULL OR valid_to >= :fecha)` | `T-05` | `CA-SP-546`. **Es la tarea de más riesgo del requerimiento**: escrita con `<` produce días que el `EXCLUDE` da por cubiertos y esta consulta declara libres, sin que nada falle | Pendiente |
| `T-07` | Conteo **exacto**, sin el atajo de omitirlo cuando la página no se llena | `T-05` | La página vacía más allá de la última devuelve el **total real** (`FA-002`) | Pendiente |
| `T-08` | `ListExchangeRatesService` con `@Transactional(readOnly = true)` y los cuatro `400` acumulados | `T-05`, `T-07` | `CA-SP-550`: los cuatro llegan juntos | Pendiente |
| `T-09` | `GET /api/v1/exchange-rates` con `exchange-rates:read` sobre el método | `T-08` | `403` sin el permiso (`CA-SP-552`) | Pendiente |
| `T-10` | Pruebas de API de los diez criterios de `spec.md` §12 | `T-09` | La suite cubre `CA-SP-543` a `CA-SP-552` | Pendiente |
| `T-11` | **Prueba del día sin tasa**: `validOn` sobre una fecha que ninguna cubre devuelve **vacío** y no la más cercana | `T-06` | `CA-SP-547`. Sin ella, «devolver la más cercana» es una optimización que alguien añadiría creyendo que ayuda | Pendiente |
| `T-12` | **Prueba de paginación estable**: varias tasas con el mismo `valid_from`, recorriendo todas las páginas | `T-05` | Ninguna se repite ni se salta. Sin el desempate por `id` esta prueba falla, y es la única que lo detecta | Pendiente |
| `T-13` | Documentación OpenAPI con los ocho parámetros. **La prosa dice que `validOn` incluye los dos extremos** | `T-10` | El contrato declara los filtros y los estados | Pendiente |
| `T-14` | Actualizar la matriz de `docs/requirements.md` | `T-10` | La fila de `RF-SP-048` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-02`, `T-03` y `T-04` son independientes y baratas. `T-05` es la pieza central, y **`T-06` se escribe con ella a la vista**: las dos deciden la misma sentencia.

`T-11` y `T-12` van al final y **no son opcionales**: son las dos que fallan cuando alguien «mejora» el filtro por vigencia o simplifica el orden.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-543` | `T-05`, `T-07`, `T-10` |
| `CA-SP-544` | `T-05` |
| `CA-SP-545` | `T-03`, `T-05`, `T-10` |
| `CA-SP-546` | `T-06` |
| `CA-SP-547` | `T-11` |
| `CA-SP-548`, `CA-SP-549` | `T-03`, `T-05`, `T-10` |
| `CA-SP-550` | `T-03`, `T-08` |
| `CA-SP-551` | `T-04`, `T-10` |
| `CA-SP-552` | `T-09` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-SP-047`: sin tabla no hay nada que consultar | 07-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
