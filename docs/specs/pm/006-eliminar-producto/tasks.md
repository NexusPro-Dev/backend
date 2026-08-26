# TASKS — `RF-PM-006` Eliminar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-006` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | `domain/models/DeletionReason`: recorta y exige contenido | — | Unitaria: el nulo, el vacío y el de solo espacios se rechazan con `VAL-002`; el largo, con `VAL-003` y **sin recortarse** | Pendiente |
| `T-02` | `Product.delete(ahora)`: marca `deleted_at` y **no toca `status`** | `RF-PM-001 · T-03` | Unitaria: un producto `ACTIVO` retirado sigue diciendo `ACTIVO` | Pendiente |
| `T-03` | Captura del **estado completo antes de tocar nada** | `T-02` | Unitaria: la instantánea se toma sobre el estado previo. Capturarla después dejaría el registro diciendo qué **quedó**, no qué **era** | Pendiente |
| `T-04` | `domain/service/DeleteProductService` con el orden de `plan.md` §5: motivo primero, bloqueo, existencia, instantánea, marca, registro | `T-01`, `T-03` | El motivo vacío se rechaza **sin costar una consulta** | Pendiente |
| `T-05` | Registro en `audit_deletion_log` con el motivo y la instantánea | `T-04` | El registro contiene el motivo **literal** y las ocho claves del producto | Pendiente |
| `T-06` | `interfaces`: `DELETE /api/v1/products/{id}` **con cuerpo**, `products:delete`, respuesta `204` | `T-04`, `T-05` | El motivo viaja en el cuerpo y **no en la URL**, donde quedaría escrito en los registros de acceso de cualquier proxy | Pendiente |
| `T-07` | Pruebas de API de los criterios de `spec.md` §12 | `T-06` | Cubre `CA-PM-048` a `CA-PM-057`, `CA-PM-086` y `CA-PM-087` | Pendiente |
| `T-08` | Prueba de que el retiro **libera destino y nombre pero no el código** | `T-07` | Otro upgrade se activa hacia ese destino, otro producto toma el nombre, y **reutilizar el código devuelve `409`**. Las tres unicidades en una sola prueba, porque su asimetría es el diseño | Pendiente |
| `T-09` | Prueba de que retirar uno **ya retirado** devuelve `409` | `T-07` | No es idempotente a propósito: dos motivos sobre un solo hecho es evidencia contradictoria | Pendiente |
| `T-10` | Prueba concurrente: dos retiros simultáneos del mismo producto | `T-06` | Un solo registro de eliminación | Pendiente |
| `T-11` | Prueba concurrente: retirar y registrar otro con el mismo nombre a la vez | `T-06` | No quedan dos vivos con el mismo nombre | Pendiente |
| `T-12` | Documentación OpenAPI, **incluido el cuerpo del `DELETE`** | `T-07` | El contrato declara el `requestBody` y el `204` | Pendiente |
| `T-13` | Actualizar la matriz de trazabilidad | `T-07` | La fila refleja el estado | Pendiente |

## 2. Orden de ejecución

`T-02` y `T-03` son las dos que definen el requerimiento y las dos que **se hacen mal sin fallar**: marcar el estado «de paso» y capturar la instantánea después producen un sistema que funciona y un registro que miente. Sus pruebas comparan contra el estado **anterior**, no contra el resultado.

`T-05` es además de quien depende `RF-PM-003` · `T-01`: sin eliminaciones registradas no hay motivo que leer.

## 3. Cobertura de los criterios de aceptación

| Criterio | Tareas |
|---|---|
| `CA-PM-048` | `T-02`, `T-07` |
| `CA-PM-049`, `CA-PM-050` | `T-01`, `T-04` |
| `CA-PM-051` | `T-03`, `T-05` |
| `CA-PM-052` | `T-02` |
| `CA-PM-053`, `CA-PM-054` | `T-08` |
| `CA-PM-055` | `T-09` |
| `CA-PM-056` | `T-06` |
| `CA-PM-057` | `T-06` |
| `CA-PM-086` | `T-07` |
| `CA-PM-087` | `T-05` |

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-08` necesita `RF-PM-005` para activar el upgrade que ocupa el destino liberado | 26-08-2026 | Responsable técnico | Abierto |

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
