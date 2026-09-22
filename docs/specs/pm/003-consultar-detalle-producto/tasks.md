# TASKS — `RF-PM-003` Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-003` |
| Enmendadas | 02-09-2026 — el detalle resuelve **las dos** membresías; 07-09-2026 — `T-13` por el **alcance** y la **implementación**; 08-09-2026 — `T-15` por el **precio público**; 12-09-2026 — `T-18` porque el segundo precio es el **de compra**; 14-09-2026 — `T-19` por el **enlace del video**; 14-09-2026 — `T-20` por la **dirección de la portada** (`RN-PM-033`); 22-09-2026 — `T-21` por **los enlaces en el detalle** (`RN-PM-048` a `RN-PM-050`), que se llevan `videoUrl` — issue [#84](https://github.com/NexusPro-Dev/backend/issues/84) |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **En `shared/audit`**: puerto `DeletionReasonReader` y su adaptador. Recibe módulo, entidad e identificador, y devuelve el motivo o vacío | `RF-PM-006 · T-05` | Integración: devuelve el motivo literal de una eliminación registrada y **vacío** si no hay ninguna. **No devuelve actor ni instantánea** | Hecha |
| `T-02` | Prueba de que ese puerto **no alcanza lo ajeno**: pedir el motivo de una entidad de otro módulo no devuelve nada | `T-01` | Es lo que impide que la lectura estrecha se convierta en la puerta trasera de la auditoría | Hecha |
| `T-03` | `application/ProductDetailResponse`: producto, **origen y destino** resueltos, moneda, y marca de retiro **con motivo** | `RF-PM-001 · T-12` | Las dos membresías llegan **vacías y presentes** en los bots, no ausentes | Hecha |
| `T-04` | `ProductQueryRepository.findDetail(UUID)`: una sentencia con **tres** uniones externas | `RF-PM-002 · T-05` | Integración: **una** sentencia, y el nivel de las dos membresías es el **actual** | Hecha |
| `T-05` | `domain/service/GetProductService`: pide el motivo al puerto **solo si el producto está retirado** | `T-01`, `T-04` | Prueba de número de sentencias: un producto vivo cuesta **una**, no dos | Hecha |
| `T-06` | `interfaces`: `GET /api/v1/products/{id}` con `products:read` | `T-05` | `404` ante identificador inexistente; `403` sin permiso | Hecha |
| `T-07` | Pruebas de API de los criterios de `spec.md` §12 | `T-06` | Cubre `CA-PM-023` a `CA-PM-029` y `CA-PM-080` a `CA-PM-082` | Hecha |
| `T-08` | Prueba del identificador **no canónico**: `400` con `VAL-001`, no `404` | `T-06` | Lo resuelve `CanonicalUuidConverter`, que ya existe: **hay que probarlo, no escribirlo**. Es el hueco que `RF-SP-018` tuvo abierto dos días | Hecha |
| `T-09` | Prueba de que la respuesta **no lleva autoría** en ninguna forma | `T-07` | `CA-PM-081`. Ni `createdBy`, ni resuelto desde la auditoría | Hecha |
| `T-10` | Documentación OpenAPI del endpoint | `T-07` | El contrato declara el `200`, el `404` y el `400` | Hecha |
| `T-11` | Actualizar la matriz de trazabilidad | `T-07` | La fila refleja el estado | Hecha |
| `T-12` | La **vigencia** viaja en el detalle | `T-03` | Vacía y presente en los productos que no caducan | Hecha |
| `T-13` | El **alcance** y la **implementación** en `ProductDetailResponse` y en la proyección `ProductRow`, con la sentencia del detalle seleccionándolas | `RF-PM-001 · T-28` | `CA-PM-118`: las dos llegan en un upgrade, en un bot y en un producto **retirado**, y el detalle sigue costando **una** sentencia | **Hecha el 07-09-2026** |
| `T-14` | El **color** de las dos membresías en el detalle | `RF-PM-001 · T-32` | `CA-PM-143`, y el detalle sigue costando **una** sentencia | **Hecha el 07-09-2026** |
| `T-15` | El **precio público** en `ProductDetailResponse` y en el `SELECT` del detalle, con la escala de la misma moneda | `RF-PM-001 · T-34` | `CA-PM-152`: los dos importes, y el público **nulo y presente** donde no se declaró. El detalle sigue costando **una** sentencia más el motivo | **Hecha el 08-09-2026** |
| `T-18` | **El segundo precio es el de compra**: `purchasePrice` sustituye a `publicPrice` en `ProductDetailResponse` y en el `SELECT` del detalle; la conversión se calcula **sobre `price`**; la prosa de la `@Operation` deja de decir «se anuncia» | `RF-PM-001 · T-38` | `CA-PM-152` y `CA-PM-166` con el nombre nuevo. `ProductDetailIT` comprueba `purchasePrice` presente y nulo | **Hecha el 12-09-2026** |
| `T-16` | **La conversión en el detalle**: el mismo resolutor que el listado (`RF-PM-002 · T-21`), con una sola moneda | `RF-PM-002 · T-21` | `CA-PM-166`: llega presente y nula cuando el producto ya está en la moneda de casa o cuando no hay tasa vigente | **Hecha el 08-09-2026** |
| `T-17` | **Actualizar `GetProductServiceIT`**: el detalle pasa de **una** sentencia a **tres** | `T-16` | El criterio de esa prueba **no cambia** —el motivo de retiro no se consulta en un producto vivo—; cambia el número, y se actualiza en vez de relajar la prueba | **Hecha el 08-09-2026** |
| `T-19` | **El enlace del video en el detalle** (`RF-PM-001` `T-39` trae la columna): `videoUrl` en el `SELECT` del detalle y en `ProductDetailResponse`; y la prosa de la `@Operation` lo nombra | `T-18` | `CA-PM-224` en `ProductDetailIT`. **El contrato regenerado declara `videoUrl` en `ProductDetailResponse`** | **Hecha el 14-09-2026**; **deshecha por `T-21` el 22-09-2026** |
| `T-20` | **La dirección de la portada en el detalle** (`RF-PM-014` `T-01` trae la columna, `T-09` y `T-10` la proyección y el conversor): `p.cover_image_id` en el `SELECT` del detalle y `coverImageUrl` en `ProductDetailResponse`; y la prosa de la `@Operation` lo nombra | `T-19`, `RF-PM-014 · T-10` | `CA-PM-233` en `ProductDetailIT`, con el retirado. **El contrato regenerado declara `coverImageUrl` en `ProductDetailResponse`** | **Hecha el 14-09-2026** |
| `T-21` | **Los enlaces en el detalle** (`RF-PM-001` `T-42` y `T-43` traen la tabla y el modelo): `ProductDetailResponse` **pierde `videoUrl`** y gana `links`; el `SELECT` del detalle pierde la columna; `GetProductService` los lee en **una sentencia más**, los devuelve **crudos y sin filtrar por tipo** —`CUPON_BOT` incluido, también en un producto retirado— y la prosa de la `@Operation` dice que esta lectura los enseña todos porque es donde se administran | `T-20`, `RF-PM-001` `T-43` | `CA-PM-224` reescrito y `CA-PM-387`, `CA-PM-388` en `ProductDetailIT`. **El contrato regenerado declara `links` en `ProductDetailResponse` y ningún `videoUrl`** | **Hecha el 22-09-2026** |

## 2. Orden de ejecución

`T-01` es la única tarea con riesgo real y **la primera que hay que escribir**, porque es la que toca infraestructura compartida. `T-02` va inmediatamente después: una lectura estrecha sin la prueba que la mantiene estrecha deja de serlo en cuanto alguien añada un método.

El resto es rutina y depende de `RF-PM-001` y `RF-PM-002`.

## 3. Cobertura de los criterios de aceptación

> `CA-PM-166` → `T-16` (añadido el 08-09-2026 con la conversión).

| Criterio | Tareas |
|---|---|
| `CA-PM-023`, `CA-PM-024` | `T-04`, `T-07` |
| `CA-PM-025` | `T-03` |
| `CA-PM-026` | `T-05`, `T-07` |
| `CA-PM-027`, `CA-PM-028` | `T-06`, `T-08` |
| `CA-PM-029` | `T-06` |
| `CA-PM-080` | `T-01`, `T-05` |
| `CA-PM-081` | `T-09` |
| `CA-PM-082` | `T-03` |
| `CA-PM-118` | `T-13` |
| `CA-PM-143` | `T-14` |
| `CA-PM-152` | `T-15`, `T-18` |
| `CA-PM-224` | `T-19` |
| `CA-PM-233` | `T-20` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-01` depende de que exista una eliminación registrada, y eso lo escribe `RF-PM-006` · `T-05`. **La dependencia es al revés de lo que sugiere el orden de los requerimientos** | 26-08-2026 | Responsable técnico | **Cerrado el 27-08-2026** — el bloqueo era menor de lo que parecía: lo que `T-01` necesita es una **fila** en el registro, no el caso de uso que la escribe. Se siembra en la prueba y la lectura queda comprobada. Lo que sigue faltando —y se anota, no se disimula— es el **recorrido de extremo a extremo**: retirar por el endpoint de `RF-PM-006` y ver el motivo en el detalle. Cuando ese requerimiento entre, la siembra de `ProductDetailIT.retirar` sobra |
| 2 | `T-01` escribe en `shared/audit`, infraestructura que usan todos los módulos. Una regresión ahí alcanza a `SP` entero | 26-08-2026 | Responsable técnico | **Cerrado el 27-08-2026** — no se tocó nada de lo existente: se **añadió** una interfaz y su adaptador, sin modificar `AuditWriter` ni las entidades. La suite de `SP` pasó sin cambios |

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

| # | Qué se hizo distinto | Por qué |
|---|---|---|
| 1 | El **componente de escala del precio** se extrajo a `application/ProductPrice`, y con él cambió el trato de un caso que `RF-PM-001` resolvía al revés | `plan.md` §10 pedía «un solo componente compartido», y hasta ahora la regla estaba escrita dos veces —en el alta y en el listado—. Al unificarla se resolvió la contradicción entre las dos specs: `RF-PM-001` recortaba a la baja un precio con más decimales de los que su moneda admite, y `RF-PM-003` §13 pide **devolver lo almacenado**. Manda la segunda: recortar **escondería el dato inválido** justo donde alguien podría verlo. Con los valores válidos —los únicos que la API deja entrar— el resultado es idéntico al anterior, de modo que ninguna prueba existente cambió de expectativa |
| 2 | `ProductRow` ganó `updatedAt`, **nulo desde el listado** | El detalle debe devolver cuándo se modificó por última vez (`spec.md` §6.2) y el listado no. Seleccionarlo en la página para descartarlo sería pagar por un dato que nadie lee; es el mismo trato que `UserRow` da a los suyos |
| 3 | El puerto descarta las eliminaciones de tipo `ASSOCIATION` | El Art. V.13 no les exige motivo y el `CHECK` las deja con el motivo en nulo. Sin ese filtro, una asociación registrada **después** de la eliminación real ganaría el `ORDER BY occurred_at DESC` y la lectura devolvería vacío teniendo el motivo delante. Tiene su prueba |

**Lo que queda pendiente y no se disimula**: el recorrido de extremo a extremo del motivo —retirar por el endpoint de `RF-PM-006` y verlo en el detalle— no existe todavía, porque ese requerimiento no está construido. La prueba siembra las dos mitades del retiro a mano y lo dice en su cabecera.
