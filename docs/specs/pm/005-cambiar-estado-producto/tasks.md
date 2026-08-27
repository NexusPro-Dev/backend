# TASKS — `RF-PM-005` Cambiar el estado de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-005` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `Product.activate()` y `Product.deactivate()`: devuelven **si hubo cambio** | `RF-PM-001 · T-03` | Unitaria: activar uno ya activo devuelve «sin cambio», no una excepción | Hecha |
| `T-02` | Comprobación de `RN-PM-014`: **no se activa sin descripción** | `T-01` | Integración: `400` con `VAL-003`, y admitido en cuanto `RF-PM-004` la pone | Hecha |
| `T-03` | Comprobación de `RN-PM-004` al activar un upgrade, **con el nombre del producto que ocupa el destino** en el mensaje | `T-01` | El `409` dice cuál desactivar. Un `409` sin ese nombre no es accionable | Hecha |
| `T-04` | Traducción de `uq_products_upgrade_target` **por nombre de restricción** a `EX-002`, con `flush` explícito | `RF-PM-001 · T-09` | Integración: la violación produce el mismo `409` que la comprobación previa. **La restricción decide, la comprobación redacta** | Hecha |
| `T-05` | `domain/service/ChangeProductStatusService` con el orden de `plan.md` §5 | `T-02`, `T-03`, `T-04` | Desactivar **no ejecuta** la comprobación del destino | Hecha |
| `T-06` | Auditoría: evento `UPDATE` con `status` y su valor anterior; **ninguno** si no hubo cambio | `T-05` | `audit_change_log` no crece con una petición que no cambia nada (`CA-PM-044`) | Hecha |
| `T-07` | `interfaces`: `PATCH /api/v1/products/{id}/status` con `products:update` | `T-05`, `T-06` | Recurso propio, no un campo del `PATCH` general | Hecha |
| `T-08` | Pruebas de API de los criterios de `spec.md` §12 | `T-07` | Cubre `CA-PM-040` a `CA-PM-047`, `CA-PM-072`, `CA-PM-073` y `CA-PM-085` | Hecha |
| `T-09` | Prueba de que en un **servicio** la comprobación del destino **no se ejecuta** | `T-07` | Número de sentencias: se comprueba la ausencia, no solo que no falle | Hecha |
| `T-10` | **Prueba concurrente: dos activaciones simultáneas hacia el mismo destino** | `T-07` | **Exactamente uno** queda activo y el otro recibe `409`. No basta con que hubiera un `409`: hay que contar cuántos quedaron activos | Hecha |
| `T-11` | Prueba concurrente: desactivar el que ocupa el destino mientras otro se activa | `T-07` | Cualquiera de los dos desenlaces vale; lo que no vale es que no quede **ninguno** activo | Hecha |
| `T-12` | Documentación OpenAPI del endpoint | `T-08` | El contrato declara el `200`, el `400` y el `409` | Hecha |
| `T-13` | Actualizar la matriz de trazabilidad | `T-08` | La fila refleja el estado | Hecha |

## 2. Orden de ejecución

`T-04` **antes** que `T-03`: la garantía es el índice, y escribir primero la comprobación previa invita a creer que con ella basta — que es exactamente la escritura sesgada que `RN-SP-018` acaba de costar en `SP`.

`T-10` es la tarea que decide si este requerimiento está bien hecho. Las demás pueden pasar con la implementación equivocada.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-040`, `CA-PM-041` | `T-01`, `T-05`, `T-08` |
| `CA-PM-042` | `T-03`, `T-04`, `T-08` |
| `CA-PM-043` | `T-08` |
| `CA-PM-044` | `T-06` |
| `CA-PM-045` | `T-05` |
| `CA-PM-046` | `T-06` |
| `CA-PM-047` | `T-07` |
| `CA-PM-072`, `CA-PM-073` | `T-02`, `T-08` |
| `CA-PM-085` | `T-08` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-02` necesita `RF-PM-004` para poder **poner** la descripción y comprobar que entonces sí se activa | 26-08-2026 | Responsable técnico | **Cerrado el 27-08-2026** — lo que `T-02` tiene que demostrar es que la regla **no es una puerta cerrada para siempre**: se rechaza sin descripción, se pone, y entonces se admite. Que la descripción la ponga la edición o una escritura directa no cambia lo que se comprueba. La prueba la escribe en la base y **lo dice en su comentario**; cuando `RF-PM-004` exista, esa línea se sustituye por su endpoint |

## 5. Definición de terminado

El requerimiento no está terminado hasta cumplir **todas** las condiciones de la constitución §16:

- [ ] Todas las tareas en estado `Hecha`.
- [ ] Todos los criterios de aceptación con prueba automatizada en verde.
- [ ] `mvn verify` en verde en local.
- [ ] Toda escritura emite su evento de auditoría, en la transacción que corresponde.
- [ ] Los endpoints nuevos declaran su permiso.
- [ ] El contrato OpenAPI coincide con el comportamiento real.
- [ ] Documentación afectada actualizada en el mismo Pull Request.
- [ ] Matriz de trazabilidad actualizada.
- [ ] Pull Request aprobado por alguien distinto del autor e integrado.

## 6. Notas de implementación

**Se comprobó que la garantía es el índice y no la comprobación previa**, y no de palabra: se desactivó `verificarDestinoLibre` y se volvió a correr la suite. El resultado es exactamente el reparto que `plan.md` §6 describe.

| Con la comprobación previa desactivada | Qué pasó |
|---|---|
| `ProductStatusConcurrencyIT` | **Sigue en verde.** Con dos y con tres activaciones simultáneas queda **exactamente una**, y el `409` llega traducido. La garantía la da `uq_products_upgrade_target` |
| `ProductStatusIT.destinoOcupado` | **Falla**, y falla en `$.detail`: el `409` sigue siendo correcto pero deja de **nombrar** al producto que ocupa el destino |

Es la prueba de que las dos piezas hacen cosas distintas: **la restricción decide, la comprobación redacta**. Quitar cualquiera de las dos rompe algo, y lo que rompe cada una es distinto.

| # | Qué se hizo distinto | Por qué |
|---|---|---|
| 1 | La respuesta es `ProductDetailResponse`, la misma que devuelve `RF-PM-003` | `plan.md` §4 dice «devuelve `200` con el producto» sin fijar la forma. Sale de **una** consulta —el destino y la moneda resueltos en la misma sentencia— en lugar de dos llamadas a los puertos de `SP`, y el mismo recurso se lee igual por donde se pida |
| 2 | `T-09` se comprueba como **diferencia** entre activar un servicio y activar un upgrade, no como número absoluto | Un número absoluto obligaría a reescribir la prueba cada vez que cambie cuánto cuesta el resto de la operación, y lo que se quiere afirmar es solo que **la comprobación del destino no se ejecuta** cuando no hay destino. Se añadió además la simétrica: desactivar cuesta lo mismo en los dos tipos |
| 3 | La traducción de `uq_products_upgrade_target` **no nombra** al producto que ocupa el destino | Es inevitable y está acotado: por ese camino se llega solo en la **carrera**, donde la comprobación previa no vio a nadie. Averiguarlo entonces exigiría una consulta dentro del fallo. El mensaje accionable lo da el camino normal |
| 4 | Una petición **sin cambio** no exige descripción | Si ya está activo, pedir `ACTIVO` no publica nada. Exigirla ahí rechazaría una petición que no cambia el estado, y `FA-001` dice que eso responde `200` |
