# TASKS — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `application/UpdateProductRequest` con **`Patchable`** en los cuatro campos corregibles | `RF-PM-001 · T-08` | Unitaria de deserialización: el campo **ausente**, el **nulo explícito** y el **con valor** llegan como tres estados distintos. Es lo que `Optional` no puede hacer | Pendiente |
| `T-02` | Rechazo de `type`, `code` y `targetMembershipId` con `VAL-006` | `T-01` | Enviarlos devuelve `400`. **No se ignoran**: ignorarlos haría creer que el cambio se aplicó | Pendiente |
| `T-03` | `Product.update(...)`: aplica lo recibido y **devuelve el diff** de lo que cambió de verdad | `RF-PM-001 · T-03` | Unitaria: enviar los mismos valores devuelve un diff **vacío** | Pendiente |
| `T-04` | `ProductRepository.findAliveByIdForUpdate`: bloqueo pesimista sobre la fila | `RF-PM-001 · T-09` | La traza muestra `SELECT … FOR UPDATE` sobre `products` | Pendiente |
| `T-05` | Unicidad del nombre **excluyendo al propio producto**, comprobada **antes de tocar el agregado** | `T-04` | Integración: enviar el nombre actual no es duplicado; y con el nombre ya escrito en la entidad, el `SELECT` no dispara el vaciado que convierte el `409` en `500` (defecto de `RF-SP-004`) | Pendiente |
| `T-06` | Validación del precio contra la **moneda nueva** cuando llegan las dos | `RF-PM-001 · T-06` | Integración: cambiar a una moneda de cero decimales rechaza `49.99` | Pendiente |
| `T-07` | `domain/service/UpdateProductService` con el orden de `plan.md` §5 | `T-03`, `T-05`, `T-06` | Cada rechazo deja el producto **intacto**: ninguno de los cambios enviados se aplica | Pendiente |
| `T-08` | Auditoría: evento `UPDATE` con **solo lo que cambió**, y **ninguno** si no cambió nada | `T-07` | `audit_change_log` no crece con una petición sin cambios (`CA-PM-038`) | Pendiente |
| `T-09` | `interfaces`: `PATCH /api/v1/products/{id}` con `products:update` | `T-07`, `T-08` | `403` sin permiso; `409` con el producto retirado | Pendiente |
| `T-10` | Pruebas de API de los criterios de `spec.md` §12 | `T-09` | Cubre `CA-PM-030` a `CA-PM-039`, `CA-PM-083` y `CA-PM-084` | Pendiente |
| `T-11` | Prueba de **número de sentencias**: bloqueo, unicidad **solo si el nombre cambió**, `UPDATE` y evento | `T-09` | Corregir solo la descripción **no** consulta la unicidad del nombre | Pendiente |
| `T-12` | Prueba concurrente: dos correcciones simultáneas del mismo producto | `T-09` | La última queda **entera**, no una mezcla de las dos | Pendiente |
| `T-13` | Documentación OpenAPI, declarando qué campos admite y **cuáles rechaza** | `T-10` | El contrato no lista `type`, `code` ni `targetMembershipId` como corregibles | Pendiente |
| `T-14` | Actualizar la matriz de trazabilidad | `T-10` | La fila refleja el estado | Pendiente |

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
| 1 | Depende de `RF-PM-001` para la tabla, el agregado y el catálogo de monedas | 26-08-2026 | Responsable técnico | Abierto |

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
