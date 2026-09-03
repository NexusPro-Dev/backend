# TASKS — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Enmendadas | 02-09-2026 — el origen entra en la lista de inmutables |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendadas | 28-08-2026 — `T-16` por el icono corregible |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdateProductRequest` con **`Patchable`** en los cuatro campos corregibles | `RF-PM-001 · T-08` | Unitaria de deserialización: el campo **ausente**, el **nulo explícito** y el **con valor** llegan como tres estados distintos. Es lo que `Optional` no puede hacer | Hecha |
| `T-02` | Rechazo de `type`, `code` y `targetMembershipId` con `VAL-006` | `T-01` | Enviarlos devuelve `400`. **No se ignoran**: ignorarlos haría creer que el cambio se aplicó | Hecha |
| `T-03` | `Product.update(...)`: aplica lo recibido y **devuelve el diff** de lo que cambió de verdad | `RF-PM-001 · T-03` | Unitaria: enviar los mismos valores devuelve un diff **vacío** | Hecha |
| `T-04` | `ProductRepository.findAliveByIdForUpdate`: bloqueo pesimista sobre la fila | `RF-PM-001 · T-09` | La traza muestra `SELECT … FOR UPDATE` sobre `products` | Hecha |
| `T-05` | Unicidad del nombre **excluyendo al propio producto**, comprobada **antes de tocar el agregado** | `T-04` | Integración: enviar el nombre actual no es duplicado; y con el nombre ya escrito en la entidad, el `SELECT` no dispara el vaciado que convierte el `409` en `500` (defecto de `RF-SP-004`) | Hecha |
| `T-06` | Validación del precio contra la **moneda nueva** cuando llegan las dos | `RF-PM-001 · T-06` | Integración: cambiar a una moneda de cero decimales rechaza `49.99` | Hecha |
| `T-07` | `domain/service/UpdateProductService` con el orden de `plan.md` §5 | `T-03`, `T-05`, `T-06` | Cada rechazo deja el producto **intacto**: ninguno de los cambios enviados se aplica | Hecha |
| `T-08` | Auditoría: evento `UPDATE` con **solo lo que cambió**, y **ninguno** si no cambió nada | `T-07` | `audit_change_log` no crece con una petición sin cambios (`CA-PM-038`) | Hecha |
| `T-09` | `interfaces`: `PATCH /api/v1/products/{id}` con `products:update` | `T-07`, `T-08` | `403` sin permiso; `409` con el producto retirado | Hecha |
| `T-10` | Pruebas de API de los criterios de `spec.md` §12 | `T-09` | Cubre `CA-PM-030` a `CA-PM-039`, `CA-PM-083` y `CA-PM-084` | Hecha |
| `T-11` | Prueba de **número de sentencias**: bloqueo, unicidad **solo si el nombre cambió**, `UPDATE` y evento | `T-09` | Corregir solo la descripción **no** consulta la unicidad del nombre | Hecha |
| `T-12` | Prueba concurrente: dos correcciones simultáneas del mismo producto | `T-09` | La última queda **entera**, no una mezcla de las dos | Hecha |
| `T-13` | Documentación OpenAPI, declarando qué campos admite y **cuáles rechaza** | `T-10` | El contrato no lista `type`, `code` ni `targetMembershipId` como corregibles | Hecha |
| `T-14` | Actualizar la matriz de trazabilidad | `T-10` | La fila refleja el estado | Hecha |
| `T-15` | La **vigencia** se suma a lo corregible, con `Patchable` | `T-01`, `T-03` | Corregirla la cambia; **vaciarla** convierte el producto en uno que no caduca (`CA-PM-094`) | Hecha |
| `T-16` | El icono, corregible y vaciable: `Patchable<String>` en el DTO, en `Product.update` y en el diff de auditoría. La comprobación de `RN-PM-016` se hace **antes** de asignar, para que el rechazo no deje el producto a medias | — | `CA-PM-099` y `CA-PM-100` en `ProductUpdateIT`, y el vaciado con nulo explícito en `ProductTest` | **Hecha el 28-08-2026** |

## 2. Orden de ejecución

`T-01` primero y con su prueba de deserialización antes que nada: **es la tarea que ya falló una vez en este proyecto**, en `RF-SP-027`, y falló en silencio. Si los tres estados no se distinguen, todo lo demás se construye sobre arena.

`T-05` es la segunda en riesgo, por el defecto del vaciado de Hibernate que solo aparece con el dato ya duplicado.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-030`, `CA-PM-031` | `T-03`, `T-07`, `T-10` |
| `CA-PM-032` | `T-01`, `T-10` |
| `CA-PM-033` | `T-02` |
| `CA-PM-034` | `T-05`, `T-07` |
| `CA-PM-035` | `T-06`, `T-07` |
| `CA-PM-036` | `T-09` |
| `CA-PM-037`, `CA-PM-038` | `T-08`, `T-11` |
| `CA-PM-039` | `T-09` |
| `CA-PM-083` | `T-10` |
| `CA-PM-084` | `T-10` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | Depende de `RF-PM-001` para la tabla, el agregado y el catálogo de monedas | 26-08-2026 | Responsable técnico | **Cerrado el 27-08-2026** — `RF-PM-001` se construyó el mismo día |

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

**La prueba concurrente encontró un defecto que `plan.md` §5 no cubría, y por poco.** El plan advertía del vaciado de Hibernate disparado por el `SELECT` de unicidad; el hueco real estaba **después**: dos productos **distintos** corrigiéndose hacia el mismo nombre a la vez **no se serializan**, porque cada uno bloquea su propia fila. Los dos pasan la comprobación previa, y la violación de `uq_products_name` llega **en el `commit`** — fuera del caso de uso, donde ya no hay nadie escuchando. Salía `500` donde corresponde `409`.

Se cierra con un **volcado explícito** justo después de aplicar el cambio de nombre, que es la misma corrección que `RF-SP-027` aplicó al correo. Solo se pide **si el nombre cambió**: el resto de campos no tiene índice que morder.

| # | Qué se hizo distinto | Por qué |
|---|---|---|
| 1 | Se escribió un **`PatchableDeserializer` genérico** en `shared/patch`, en lugar de cinco deserializadores por tipo | El que existía solo servía para texto, y aquí hacen falta los tres estados en un importe, un identificador y un número de días. Resuelve el tipo interno por contexto (`ContextualDeserializer`). Convive con `PatchableStringDeserializer`, que es anterior; migrar los dos DTO de `SP` que lo usan queda como deuda de dos anotaciones |
| 2 | El precio se mide con la **escala significativa**, en `ProductPrice.cabeEn` | Lo destapó una prueba: cambiar **solo** la moneda de un producto se rechazaba por decimales que su precio no tiene. El precio leído de la base trae la escala de la columna —`numeric(14,4)`, de modo que `49.99` llega como `49.9900`— y compararla en crudo daba cuatro decimales contra los dos de la moneda. La regla se extrajo y **el alta pasó a usar la misma**, o el mismo importe sería válido por un endpoint e inválido por otro |
| 3 | Los tres campos inmutables se **declaran** en el DTO como `Patchable<Object>` | Sin declararlos, `FAIL_ON_UNKNOWN_PROPERTIES` ya devolvería `400`, pero con el texto genérico de Jackson: quien intente cambiar el código leería «propiedad desconocida» y creería que se equivocó de nombre, en lugar de enterarse de que el código **no se cambia nunca** |
| 4 | El `409` del volcado se **reetiqueta a `EX-002`** | El adaptador traduce por nombre de restricción y no puede saber desde qué operación se le llamó: el alta numera «nombre duplicado» como `EX-001` y esta spec como `EX-002`. Cambia la etiqueta, no el diagnóstico |
| 5 | `T-11` se comprueba como **diferencia** entre corregir la descripción y corregir el nombre | Un número absoluto obligaría a reescribir la prueba cada vez que cambie el coste del resto. Se añadió la simétrica del catálogo de monedas: corregir sin tocar precio ni moneda no lo consulta |

**Con esto queda cerrada la nota de `RF-PM-005`**: la descripción que `RN-PM-014` exige para publicar ya se pone por su endpoint, y no con una escritura directa en la base.
