# TASKS — `RF-PM-003` Consultar el detalle de un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-003` |
| Plan | [`plan.md`](plan.md), aprobado el 26-08-2026 |
| Estado | **Aprobadas** |
| Autor | Responsable técnico |
| Aprobadas por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Tareas

| ID | Tarea | Depende de | Verificación | Estado |
|---|---|---|---|---|
| `T-01` | **En `shared/audit`**: puerto `DeletionReasonReader` y su adaptador. Recibe módulo, entidad e identificador, y devuelve el motivo o vacío | `RF-PM-006 · T-05` | Integración: devuelve el motivo literal de una eliminación registrada y **vacío** si no hay ninguna. **No devuelve actor ni instantánea** | Pendiente |
| `T-02` | Prueba de que ese puerto **no alcanza lo ajeno**: pedir el motivo de una entidad de otro módulo no devuelve nada | `T-01` | Es lo que impide que la lectura estrecha se convierta en la puerta trasera de la auditoría | Pendiente |
| `T-03` | `application/ProductDetailResponse`: producto, destino y moneda resueltos, y marca de retiro **con motivo** | `RF-PM-001 · T-12` | El destino llega **vacío y presente** en los servicios, no ausente | Pendiente |
| `T-04` | `ProductQueryRepository.findDetail(UUID)`: una sentencia con dos uniones externas | `RF-PM-002 · T-05` | Integración: **una** sentencia, y el nivel del destino es el **actual** | Pendiente |
| `T-05` | `domain/service/GetProductService`: pide el motivo al puerto **solo si el producto está retirado** | `T-01`, `T-04` | Prueba de número de sentencias: un producto vivo cuesta **una**, no dos | Pendiente |
| `T-06` | `interfaces`: `GET /api/v1/products/{id}` con `products:read` | `T-05` | `404` ante identificador inexistente; `403` sin permiso | Pendiente |
| `T-07` | Pruebas de API de los criterios de `spec.md` §12 | `T-06` | Cubre `CA-PM-023` a `CA-PM-029` y `CA-PM-080` a `CA-PM-082` | Pendiente |
| `T-08` | Prueba del identificador **no canónico**: `400` con `VAL-001`, no `404` | `T-06` | Lo resuelve `CanonicalUuidConverter`, que ya existe: **hay que probarlo, no escribirlo**. Es el hueco que `RF-SP-018` tuvo abierto dos días | Pendiente |
| `T-09` | Prueba de que la respuesta **no lleva autoría** en ninguna forma | `T-07` | `CA-PM-081`. Ni `createdBy`, ni resuelto desde la auditoría | Pendiente |
| `T-10` | Documentación OpenAPI del endpoint | `T-07` | El contrato declara el `200`, el `404` y el `400` | Pendiente |
| `T-11` | Actualizar la matriz de trazabilidad | `T-07` | La fila refleja el estado | Pendiente |
| `T-12` | La **vigencia** viaja en el detalle | `T-03` | Vacía y presente en los productos que no caducan | Pendiente |

## 2. Orden de ejecución

`T-01` es la única tarea con riesgo real y **la primera que hay que escribir**, porque es la que toca infraestructura compartida. `T-02` va inmediatamente después: una lectura estrecha sin la prueba que la mantiene estrecha deja de serlo en cuanto alguien añada un método.

El resto es rutina y depende de `RF-PM-001` y `RF-PM-002`.

## 3. Cobertura de los criterios de aceptación

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

## 4. Bloqueos

| # | Bloqueo | Desde | Responsable | Estado |
|---|---|---|---|---|
| 1 | `T-01` depende de que exista una eliminación registrada, y eso lo escribe `RF-PM-006` · `T-05`. **La dependencia es al revés de lo que sugiere el orden de los requerimientos** | 26-08-2026 | Responsable técnico | Abierto |
| 2 | `T-01` escribe en `shared/audit`, infraestructura que usan todos los módulos. Una regresión ahí alcanza a `SP` entero | 26-08-2026 | Responsable técnico | Abierto |

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
