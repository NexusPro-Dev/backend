# TASKS — `RF-SP-049` Corregir una tasa de cambio

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-049` |
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
| `T-01` | `application/UpdateExchangeRateRequest` con `Patchable`, **incluidas las dos monedas como `Patchable<Object>`** para poder rechazarlas con su mensaje | `RF-SP-047 · T-05` | Enviar una moneda devuelve `400` con `VAL-007` **nombrando el campo**, no el texto genérico de Jackson (`CA-SP-557`) | Pendiente |
| `T-02` | `ExchangeRate.update(...)`: aplica lo presente y **devuelve el diff** con `before` y `after` | `RF-SP-047 · T-03` | Unitaria: el diff trae solo lo que cambió, y el mismo valor **no** es un cambio | Pendiente |
| `T-03` | Los **tres nulos que se rechazan** —precio, inicio y estado— y **el que sí vacía** —fin— | `T-01`, `T-02` | `CA-SP-555` y `CA-SP-556`. Es donde se cuela el error: tratar los cuatro igual | Pendiente |
| `T-04` | `UpdateExchangeRateService` con el orden de `plan.md` §5: inmutables, bloqueo, existencia, formato, aplicación | `T-03` | Ningún rechazo aplica nada (`CA-SP-558`) | Pendiente |
| `T-05` | **`flush()` explícito tras aplicar**, con traducción de `uq_exchange_rates_vigente` a `409` | `T-04` | Sin él la violación sale en el `commit` como `500`. `CA-SP-558` lo comprueba de extremo a extremo | Pendiente |
| `T-06` | **Activar y suspender contra la restricción**: suspender libera el periodo, activar vuelve a entrar en él | `T-05` | `CA-SP-559` y `CA-SP-560`, las dos mitades | Pendiente |
| `T-07` | Auditoría: evento `UPDATE` con solo lo que cambió, y **ninguno** si el diff está vacío | `T-04` | `CA-SP-561` y `CA-SP-562`, este último comprobando que `updated_at` **no se mueve** | Pendiente |
| `T-08` | `PATCH /api/v1/exchange-rates/{id}` con `exchange-rates:update` sobre el método | `T-04` | `403` sin el permiso, `404` ante una tasa retirada (`CA-SP-563`, `CA-SP-564`) | Pendiente |
| `T-09` | Pruebas de API de los doce criterios de `spec.md` §12 | `T-08` | La suite cubre `CA-SP-553` a `CA-SP-564` | Pendiente |
| `T-10` | **Prueba concurrente 1**: dos correcciones de la **misma** tasa | `T-08` | La última queda **entera**, no una mezcla de las dos | Pendiente |
| `T-11` | **Prueba concurrente 2**: dos correcciones de tasas **distintas** hacia el mismo periodo | `T-08` | Una queda y la otra recibe `409`. **Es la que demuestra que el bloqueo no sustituye al `EXCLUDE`**: cada una bloquea su propia fila | Pendiente |
| `T-12` | Documentación OpenAPI, declarando **qué campos admite, cuáles rechaza y cuál de ellos se vacía con nulo** | `T-09` | El contrato no lista las monedas como corregibles, y su prosa dice que **solo la fecha de fin** admite el nulo | Pendiente |
| `T-13` | Actualizar la matriz de `docs/requirements.md` | `T-09` | La fila de `RF-SP-049` refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-01` y `T-02` son independientes. `T-03` es la que decide el comportamiento del `PATCH` y conviene escribirla con las dos a la vista.

**`T-05` va antes que `T-06`**: activar sin el volcado explícito produciría un `500` y se leería como un fallo de la activación, cuando el problema sería otro.

`T-10` y `T-11` van al final y **no son opcionales**: la segunda es la única prueba del proyecto que distingue lo que el bloqueo pesimista protege de lo que no.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-SP-553`, `CA-SP-554` | `T-02`, `T-04`, `T-09` |
| `CA-SP-555`, `CA-SP-556` | `T-03` |
| `CA-SP-557` | `T-01` |
| `CA-SP-558` | `T-05`, `T-11` |
| `CA-SP-559`, `CA-SP-560` | `T-06` |
| `CA-SP-561`, `CA-SP-562` | `T-07` |
| `CA-SP-563`, `CA-SP-564` | `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-SP-047`: sin tabla ni agregado no hay nada que corregir | 07-09-2026 | Responsable técnico | Abierto |

## 5. Definición de terminado

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, y la que no cambia nada **no lo emite**.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real, **prosa incluida**.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.
