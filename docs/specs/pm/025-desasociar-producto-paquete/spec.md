# SPEC — `RF-PM-025` Desasociar un producto de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-025` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Sacar un producto del paquete, **sin motivo** y dejando escrito qué había.

## 2. Contexto

Es `RF-CM-008` para paquetes. La fila `(paquete, producto, descuento)` es una **asociación** en el sentido del Art. V.13 —su significado se agota en el par que vincula—, de modo que quitarla no pide motivo, **se borra físicamente** y se registra como `ASSOCIATION` con su instantánea (`RN-PM-042`). Lo único que la distingue de la de `CM` es que la fila lleva datos —el descuento— y la instantánea los conserva.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Saca el producto |

## 4. Alcance

### 4.1 Incluye

- Quitar la asociación de un producto con un paquete **vivo**.
- Registrarla en la auditoría de eliminación como `ASSOCIATION`, con forma, valor y precio del producto en ese instante.
- Devolver el paquete entero con su cuenta rehecha.

### 4.2 No incluye

- **Motivo.** Es una asociación.
- **Desactivar el paquete** si queda con menos de dos: `RN-PM-040` lo saca de la oferta sin tocarlo.
- **Retirar el producto.** Sigue en el catálogo y en los demás paquetes.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-042` | Desasociar no pide motivo; se borra y se registra como asociación | `requirements/pm.md` §5.1 |
| `RN-PM-040` | Con menos de dos, el paquete deja de ofrecerse y no se desactiva | `requirements/pm.md` §5.1 |
| `RN-PM-036` | La respuesta trae la cuenta rehecha | `requirements/pm.md` §5.1 |
| `RN-PM-044` | Sin upgrades dentro, el origen del paquete queda libre | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del paquete y del producto | Sí | Qué fila | Ruta. Paquete vivo; producto asociado |

**Sin cuerpo.** Un `DELETE` con cuerpo se ignora.

### 6.2 Salida

`200` con el paquete entero en la forma del detalle — y no `204`, al revés que el retiro de la reseña: aquí **sí hay algo que devolver**, el precio nuevo del paquete, y obligar a una segunda llamada para verlo sería lo que `RF-CM-007` evitó devolviendo todas las asociaciones.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:update`; paquete vivo; el producto está en él.

**Postcondiciones:** la fila **no existe**; `audit_deletion_log` tiene la fila `ASSOCIATION` con la instantánea; el paquete vale la nueva suma; su estado no cambió.

## 8. Flujo principal

1. Llega la petición.
2. El sistema resuelve el paquete **vivo**, bloqueándolo (`EX-001`), y la fila del producto en él (`EX-002`).
3. El sistema toma la instantánea, borra la fila y registra la baja como `ASSOCIATION`, en la misma transacción.
4. Devuelve `200` con el paquete.

## 9. Flujos alternativos

### FA-001 — Queda con uno o con cero

**Comportamiento:** se desasocia igual. El paquete sigue en su estado, `offerable: false` con «menos de dos», y la oferta no lo enseña.

### FA-002 — Se quita el único upgrade

**Comportamiento:** el origen del paquete queda **libre**: el siguiente upgrade que entre puede ser de otro origen (`RN-PM-044`).

## 10. Excepciones

### EX-001 — El paquete no existe o está retirado

**Respuesta del sistema:** `404`.

### EX-002 — El producto no está en el paquete

**Respuesta del sistema:** `404` — *«Ese producto no está en el paquete.»* No es `409`: con el borrado físico no queda nada que distinga «nunca estuvo» de «ya se quitó», como en `RF-CM-008`.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificadores con formato válido | El identificador indicado no tiene un formato válido. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-321` | El sistema desasocia con `200` **sin motivo ni cuerpo**, la fila desaparece y el paquete vuelve con `price`, `listPrice` y `savings` rehechos |
| `CA-PM-322` | `audit_deletion_log` tiene la fila `ASSOCIATION` sin motivo, con forma, valor y precio del producto en la instantánea |
| `CA-PM-323` | El sistema responde `404` al producto que **no está** —también al que **ya se quitó**— y al paquete retirado |
| `CA-PM-324` | Desasociar hasta dejar **uno** no cambia el estado del paquete y lo deja `offerable: false` por «menos de dos» |
| `CA-PM-325` | Quitado el único upgrade, entra otro de **otro origen** |
| `CA-PM-326` | El producto sigue activo en el catálogo y en los demás paquetes que lo contengan |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Dos `DELETE` simultáneos de la misma fila | El bloqueo del paquete los ordena; el segundo recibe `404` y no escribe auditoría |
| Desasociar un producto **retirado** del catálogo | Se puede: es la forma de arreglar un paquete que quedó `offerable: false` por él |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿`200` con el paquete o `204`? | **`200` con el paquete.** Lo que cambió es su precio, y `RF-CM-007` ya decidió que las operaciones sobre asociaciones devuelven el conjunto. El retiro de la reseña responde `204` porque ahí no queda nada que enseñar |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda `RF-CM-008`: asociación, sin motivo, borrado físico, `ASSOCIATION` con instantánea —que aquí lleva el descuento—. Responde `200` con el paquete porque lo que cambió es su precio. Dejar el paquete con menos de dos no lo desactiva: lo saca de la oferta y el detalle lo dice. | Responsable técnico |
