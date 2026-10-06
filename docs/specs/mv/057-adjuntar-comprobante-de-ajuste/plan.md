# PLAN — `RF-MV-057` Adjuntar el comprobante de un ajuste de puntos

| Campo | Valor |
|---|---|
| Requerimiento | `RF-MV-057` |
| Especificación | [`spec.md`](spec.md) v0.1.0 |
| `spec.md` aprobada el | 06-10-2026 |
| Versión | 0.1.0 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Fecha de aprobación | 06-10-2026 |

---

## 1. Enfoque

**El archivo se valida en el dominio, antes de abrir nada**, con un valor nuevo, `PointsReceipt`: vacío (`VAL-002`), tamaño (`VAL-004`) y firma (`VAL-003`), en ese orden, como `ImageSignature` —leer la firma de cincuenta megas para decir que no es un PDF es trabajo tirado—. **La firma**: `%PDF-` para PDF, la de ocho bytes de PNG y `FF D8 FF` de JPEG. **No se reutiliza `ImageSignature`**: admite WebP, que aquí no, y no conoce el PDF; las firmas de PNG y JPEG se toman de ella para no escribirlas dos veces. El valor calcula el resumen `SHA-256` y limpia el nombre —sin ruta, sin caracteres de control, hasta 255; vacío pasa a `comprobante.<ext>`—.

**Ajustar con archivo es la misma ruta con otro `Content-Type`**: `POST /points-adjustments` admite `multipart/form-data` con una parte `adjustment` —el JSON de siempre— y una parte `file`. **Dos métodos del controlador** por `consumes`, que llaman al mismo servicio: el de JSON pasa el archivo nulo. Así el cliente que ya ajusta no cambia nada (`CA-MV-686`).

**Todo o nada**: `PointsAdjustmentService.adjust` recibe el comprobante ya validado y lo inserta **en la misma transacción** que el movimiento y sus asientos. Si la resta no alcanza, la excepción revierte también el comprobante.

**La petición repetida** compara además **el resumen**: el del archivo recibido —o nulo— con el del comprobante guardado —o nulo—. Iguales, responde el ya hecho; distintos, `EX-005` como cualquier otra petición distinta con la misma clave (`spec.md` §2.1). Un comprobante reemplazado después cambia el resumen guardado, de modo que repetir la petición original después de reemplazarlo es conflicto: correcto, porque esa petición ya no describe el estado.

**Adjuntar después** (`PUT /points-adjustments/{id}/receipt`) **bloquea la fila del movimiento** (`SELECT … FOR UPDATE`) antes de leer el comprobante anterior: dos reemplazos simultáneos auditarían el mismo «anterior». Inserta o actualiza con `INSERT … ON CONFLICT (movement_id) DO UPDATE`. Responde `200` siempre —con o sin comprobante previo—: es `PUT`, y el resultado es el mismo estado.

**La auditoría** es un `ChangeEvent` sobre la entidad `points_adjustment_receipts`, con el identificador del movimiento: `CREATE` con `after` o `UPDATE` con `before` y `after`, cada uno con `fileName`, `contentType`, `sizeBytes` y `sha256`. **Nunca el contenido**.

---

## 2. Cambios de esquema

**`V77`** ([`055 plan.md`](../055-consultar-mis-movimientos-de-puntos/plan.md) §2). De aquí: `movements:attach-points-receipt` a `SUPERADMIN` y `ADMIN`, explícito. **No se marca como sensible** (`requires_recent_mfa`): la lista de dieciocho la confirmó el responsable el 06-10-2026, y un soporte no mueve puntos. **Ajustar sigue siéndolo**, también con archivo.

`spring.servlet.multipart` ya admite 6 MB por archivo y 7 por petición, por encima de los 5 de negocio: el mismo margen que las portadas, para que un archivo de 5,5 MB reciba el `VAL-004` del dominio.

---

## 3. Componentes afectados

| Capa | Componente | Cambio |
|---|---|---|
| `domain/models` | `PointsReceipt` | Nuevo: validación, firma, resumen, nombre |
| `domain/repository` | `PointsReceiptRepository`, `JpaPointsReceiptRepository` | Nuevos: `upsert`, `findInfo`, `lockAdjustment` |
| `domain/service` | `PointsAdjustmentService` | `adjust` recibe el comprobante; la repetición compara el resumen |
| `domain/service` | `AttachPointsReceiptService` | Nuevo |
| `application` | `PointsAdjustmentResponse` | Gana `receipt` (`PointsReceiptInfo`, nulo sin archivo) |
| `interfaces` | `PointsController` | `POST /points-adjustments` multipart; `PUT /points-adjustments/{id}/receipt` |

---

## 4. Contrato de API

| Verbo | Ruta | Permiso | Cuerpo |
|---|---|---|---|
| `POST` | `/api/v1/movements/points-adjustments` | `movements:adjust-points` | JSON, como siempre, **o** `multipart/form-data` con `adjustment` (JSON) y `file` |
| `PUT` | `/api/v1/movements/points-adjustments/{id}/receipt` | `movements:attach-points-receipt` | `multipart/form-data` con `file` |

| Código | Cuándo |
|---|---|
| `201` / `200` | Ajuste nuevo / repetido (`POST`) |
| `200` | Comprobante guardado (`PUT`) |
| `400` | Archivo ausente (`PUT`), vacío, de otro tipo o mayor de 5 MB; y los de `RF-MV-052` |
| `404` | El movimiento no existe o no es un ajuste (`PUT`) |
| `409` | La clave es de otra petición —también por el archivo— (`POST`) |

---

## 5. Autorización

`@PreAuthorize` con el permiso de cada ruta. El `POST` multipart lleva el mismo permiso y la misma reverificación del segundo factor que el JSON: es la misma operación.

---

## 6. Auditoría

§1. El `CREATE` del movimiento de `RF-MV-052` no cambia; el comprobante tiene su propio evento.

---

## 7. Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Guardar el archivo fuera de la base | El sistema no tiene almacenamiento de objetos; las portadas ya viven en la base con el mismo tope |
| Conservar las versiones reemplazadas | Decisión del responsable: uno por ajuste. La auditoría guarda el resumen del anterior |
| Una ruta aparte para ajustar con archivo | Dos rutas para la misma operación con dos permisos posibles; el `consumes` la mantiene en una |
| Confiar en el `Content-Type` de la parte | Lo pone el cliente (`RN-MV-077`) |

---

## 8. Estrategia de prueba

Unitaria, `PointsReceiptTest`: firmas, tamaño, nombre, resumen. Integración, `PointsReceiptIT`: `CA-MV-685` a `CA-MV-695`; las descargas de `RF-MV-055` y `RF-MV-056` se prueban sobre archivos subidos por aquí.
