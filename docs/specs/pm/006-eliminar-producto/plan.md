# PLAN — `RF-PM-006` Eliminar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-006` |
| Especificación | [`spec.md`](spec.md) v0.2.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobado por | — |
| Fecha de aprobación | — |

---

## 1. Enfoque

Eliminación **lógica y con motivo** (Art. V.13). Lo que define esta operación no es lo que hace, es **el orden en el que lo hace** y lo que deliberadamente no toca.

## 2. Cambios de esquema

**Ninguno.** `deleted_at` se creó con la tabla en `V39`. **No hay columna de motivo**, y es a propósito: el motivo viaja al registro de eliminación con la instantánea (`requirements/pm.md` §10.1).

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/models` | `Product.delete(ahora)` | Marca la fila. **No toca `status`** |
| `domain/models` | `DeletionReason` | Recorta y exige contenido |
| `domain/service` | `DeleteProductService` | Orden de §5 |
| `interfaces` | `ProductController` | `DELETE /api/v1/products/{id}` con motivo en el cuerpo |

**`DeletionReason` se escribe aquí y no se reutiliza el de `SP`**: el de `RF-SP-029` vive en `modules/system/users/domain` y traerlo cruzaría la frontera que D-25 acaba de fijar. Son diez líneas de recorte y validación; compartirlas exigiría promoverlas a `shared/`, y eso se hace cuando haya un tercer cliente, no antes.

## 4. Contrato de API

`DELETE /api/v1/products/{id}` con `{"reason": "…"}` y respuesta `204`.

**`DELETE` con cuerpo**, como `RF-SP-009` y `RF-SP-029`. Está permitido por la especificación de HTTP y es lo que el Art. V.13 obliga a transportar; la alternativa —el motivo en la URL— lo dejaría escrito en los registros de acceso de cualquier proxy.

**No devuelve el producto** (`spec.md` §6.2): lo que se acaba de retirar no es algo que el sistema deba seguir ofreciendo a quien lo pidió.

## 5. Orden de verificación, que es el contrato

1. **El motivo tiene contenido** (`VAL-002`). Va primero: rechazar por motivo vacío no debe costar ni una consulta.
2. **Bloqueo** de la fila.
3. Existe y **no está ya retirado** (`EX-001`, `EX-002`).
4. **Se captura el estado completo ANTES de tocar nada.**
5. Se marca `deleted_at`. **`status` no se toca.**
6. Se registra la eliminación con el motivo y la instantánea.

!!! important "Los pasos 4 y 5 son el requerimiento"

    **La instantánea va antes de tocar nada.** Si se capturara después, el registro no diría qué era el producto sino qué quedó de él. Es el mismo orden que `RF-SP-029` fijó al eliminar una persona, y por el mismo motivo: después de tocar, ya no hay qué capturar.

    **El estado no se toca**, y es lo que hace útil al registro: `CA-PM-052` exige que diga si el producto **estaba a la venta** cuando se retiró. Desactivarlo «de paso» haría que todos los registros dijeran «inactivo» y ese dato dejaría de significar nada — la salvaguarda habría destruido la evidencia que protege.

## 6. Lo que el retiro libera, y lo que no

| Recurso | ¿Se libera? | Por qué |
|---|---|---|
| El **destino** del upgrade | **Sí** | `uq_products_upgrade_target` es parcial `WHERE … deleted_at IS NULL`: la fila retirada deja de contar y otro upgrade puede activarse (`CA-PM-053`) |
| El **nombre** | **Sí** | `uq_products_name` también es parcial (`CA-PM-054`) |
| El **código** | **No, nunca** | `uq_products_code` es una restricción **total** (`RN-PM-013`): el día que una factura diga `UPGRADE_ORO` tiene que resolver a un solo producto para siempre |

Que dos de las tres unicidades sean parciales y la tercera no **es el diseño**, no un descuido, y las tres tienen prueba.

## 7. Auditoría

Un registro en `audit_deletion_log` con el motivo y la instantánea completa. **Ningún evento de seguridad** (`spec.md` §14, resolución 3): un producto no concede privilegios, y el catálogo de `security.md` §8.1 es cerrado.

Este registro es además **el que lee `RF-PM-003`** para devolver el motivo, por el puerto estrecho que su plan §3 propone.

## 8. Transaccionalidad

Una transacción: marca, registro de eliminación y nada más. **No es idempotente a propósito** (`EX-002`): retirar dos veces con dos motivos distintos dejaría el segundo escrito sobre un hecho que ocurrió antes y por otra razón.

Dos retiros simultáneos los serializa el bloqueo; el segundo ve `deleted_at` ya escrito y responde `EX-002`. **Un solo registro de eliminación**: dos registros con dos motivos para un único hecho es evidencia contradictoria.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| Borrado físico | Rompería lo vendido y contradice `RN-PM-010` |
| Exigir desactivar antes de retirar | Haría que todos los registros dijeran «inactivo» (`spec.md` §14, resolución 1) |
| Motivo en la URL | Quedaría escrito en los registros de acceso de cualquier proxy |
| Idempotencia en el segundo retiro | Sobrescribiría el motivo del hecho real |
| Reutilizar `DeletionReason` de `SP` | Cruzaría la frontera de D-25 por diez líneas |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | La instantánea se captura después de marcar, y nadie lo nota porque el registro «tiene datos» | Prueba que compara el contenido del registro con el estado **anterior**, no con el posterior |
| 2 | Retirar y registrar otro con el mismo nombre en carrera deja dos vivos | Lo impide `uq_products_name`; la prueba concurrente lo ejercita |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los diez criterios de `spec.md` §12 | API | |
| Motivo ausente, vacío o solo espacios | API | `400` y **no retira nada** |
| **La instantánea es la anterior al retiro** | Integración | Se retira un producto `ACTIVO` y el registro dice `ACTIVO` |
| El estado no se modifica | Integración | `status` sigue siendo el que era |
| El destino y el nombre se liberan | API | Otro upgrade se activa; otro producto toma el nombre |
| **El código no se libera** | API | Reutilizarlo devuelve `409` (`CA-PM-069`) |
| Retirar uno ya retirado | API | `409`, no `204` |
| Dos retiros simultáneos | Concurrencia | Un solo registro de eliminación |
