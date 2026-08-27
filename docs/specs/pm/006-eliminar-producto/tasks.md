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
| `T-01` | `domain/models/DeletionReason`: recorta y exige contenido | — | Unitaria: el nulo, el vacío y el de solo espacios se rechazan con `VAL-002`; el largo, con `VAL-003` y **sin recortarse** | Hecha |
| `T-02` | `Product.delete(ahora)`: marca `deleted_at` y **no toca `status`** | `RF-PM-001 · T-03` | Unitaria: un producto `ACTIVO` retirado sigue diciendo `ACTIVO` | Hecha |
| `T-03` | Captura del **estado completo antes de tocar nada** | `T-02` | Unitaria: la instantánea se toma sobre el estado previo. Capturarla después dejaría el registro diciendo qué **quedó**, no qué **era** | Hecha |
| `T-04` | `domain/service/DeleteProductService` con el orden de `plan.md` §5: motivo primero, bloqueo, existencia, instantánea, marca, registro | `T-01`, `T-03` | El motivo vacío se rechaza **sin costar una consulta** | Hecha |
| `T-05` | Registro en `audit_deletion_log` con el motivo y la instantánea | `T-04` | El registro contiene el motivo **literal** y las ocho claves del producto | Hecha |
| `T-06` | `interfaces`: `POST /api/v1/products/{id}/deletion` **con cuerpo**, `products:delete`, respuesta `204` | `T-04`, `T-05` | El motivo viaja en el cuerpo y **no en la URL**, donde quedaría escrito en los registros de acceso de cualquier proxy. **El verbo se corrigió el 27-08-2026** — ver `plan.md` §4 | Hecha |
| `T-07` | Pruebas de API de los criterios de `spec.md` §12 | `T-06` | Cubre `CA-PM-048` a `CA-PM-057`, `CA-PM-086` y `CA-PM-087` | Hecha |
| `T-08` | Prueba de que el retiro **libera destino y nombre pero no el código** | `T-07` | Otro upgrade se activa hacia ese destino, otro producto toma el nombre, y **reutilizar el código devuelve `409`**. Las tres unicidades en una sola prueba, porque su asimetría es el diseño | Hecha |
| `T-09` | Prueba de que retirar uno **ya retirado** devuelve `409` | `T-07` | No es idempotente a propósito: dos motivos sobre un solo hecho es evidencia contradictoria | Hecha |
| `T-10` | Prueba concurrente: dos retiros simultáneos del mismo producto | `T-06` | Un solo registro de eliminación | Hecha |
| `T-11` | Prueba concurrente: retirar y registrar otro con el mismo nombre a la vez | `T-06` | No quedan dos vivos con el mismo nombre | Hecha |
| `T-12` | Documentación OpenAPI, **incluido el cuerpo de la petición** | `T-07` | El contrato declara el `requestBody` y el `204` | Hecha |
| `T-13` | Actualizar la matriz de trazabilidad | `T-07` | La fila refleja el estado | Hecha |

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
| 1 | `T-08` necesita `RF-PM-005` para activar el upgrade que ocupa el destino liberado | 26-08-2026 | Responsable técnico | **Cerrado el 27-08-2026** — `RF-PM-005` se construyó justo antes, de modo que la prueba activa el segundo upgrade por su endpoint real y no simulando nada |

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
| 1 | El endpoint es **`POST /{id}/deletion`** y no `DELETE` con cuerpo | El `plan.md` lo justificaba diciendo que era lo que hacían `RF-SP-009` y `RF-SP-029`, y **no es cierto**: los dos exponen `POST /{id}/deletion` porque RFC 9110 no define semántica para el cuerpo de un `DELETE` y un intermediario puede descartarlo. Se corrigió el plan (Art. I.7) y no el sistema |
| 2 | `EX-002` —ya retirado— es un **`409` distinguible** del `404` de `EX-001` | Al eliminar una persona los dos casos comparten el `404` para no revelar que existió. **Aquí no hay nada que ocultar**: el catálogo devuelve los productos retirados a cualquiera con `products:read` (`CA-PM-018`), de modo que quien intenta retirar dos veces merece saber que su primera petición ya funcionó, en lugar de creer que falló |
| 3 | La instantánea la arma **el agregado**, en `Product.instantanea()`, y el alta pasó a usar la misma | `RF-PM-001` armaba su mapa a mano y este iba a armar otro. Con dos mapas, el registro de creación y el de eliminación describirían el mismo producto **con claves distintas**, y comparar los dos —que es para lo que existen— dejaría de ser posible. Hay una prueba de que las dos instantáneas tienen las mismas claves |
| 4 | `Product.delete` mueve `updatedAt` además de `deleted_at` | La fila cambió. La marca de modificación es **de la fila**, no del estado comercial del producto, y `status` sigue sin tocarse — que es lo que `CA-PM-052` exige |

**El recorrido de extremo a extremo que `RF-PM-003` dejó pendiente ya existe**: `ProductDeletionIT.elDetalleDevuelveElMotivoDeEsteRetiro` retira por este endpoint y consulta el detalle, que lee el motivo por el puerto estrecho de `shared/audit`. Aquella prueba sembraba las dos mitades del retiro a mano porque este requerimiento no estaba construido.
