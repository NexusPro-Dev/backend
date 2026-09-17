# SPEC — `RF-PM-022` Eliminar paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-022` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Retirar un paquete que fue un error o que ya no se arma, **con motivo**, y sin que desaparezca lo que decía contener.

## 2. Contexto

Es `RF-PM-006` para paquetes, y hereda entero su razonamiento: eliminación **lógica con motivo** (Art. V.13, `RN-PM-041`), en cualquier estado y sin desactivar antes, el motivo y la instantánea a `audit_deletion_log`, y la distinción entre «no existe» y «ya está retirado». Lo único propio: **las filas de asociación permanecen**. El paquete retirado sigue diciendo qué contenía y con qué descuento, que es lo que una venta pasada necesitará resolver el día que los paquetes se vendan.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Retira el paquete |

## 4. Alcance

### 4.1 Incluye

- Retirar lógicamente un paquete vivo, en cualquier estado, con motivo.
- Conservar sus filas de asociación.
- Registrar la baja con la instantánea del paquete **y de sus productos con su descuento**.

### 4.2 No incluye

- **Desasociar antes.** Ni se exige ni se hace: las filas se quedan.
- **Retirar los productos.** El paquete no manda sobre ellos; siguen en el catálogo y en otros paquetes.
- **Revivir** un paquete retirado. Se crea otro; el código no se libera.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-041` | Retiro lógico con motivo; el código no se libera | `requirements/pm.md` §5.1 |
| `RN-PM-039` | Retirado, no se ofrece | `requirements/pm.md` §5.1 |
| Art. V.13 | El motivo es obligatorio y viaja con la instantánea | `constitution.md` |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Paquete **vivo** |
| `reason` | Sí | Por qué | Con contenido, tras recortar |

### 6.2 Salida

`204`. Nada que devolver: el paquete retirado se consulta por `RF-PM-019`, con su motivo.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:delete`; paquete vivo; motivo con contenido.

**Postcondiciones:** `deleted_at` puesto y **nada más de la fila cambia** —`status` incluido, para que el registro diga si estaba a la venta—; filas de asociación intactas; `audit_deletion_log` con `LOGICAL`, el motivo y la instantánea con sus productos.

## 8. Flujo principal

1. Llega la petición con el motivo.
2. El sistema valida el motivo **antes de cualquier consulta** (`VAL-002`).
3. El sistema resuelve el paquete **en cualquier estado**, bloqueándolo: si no existe, `EX-001`; si ya está retirado, `EX-002`.
4. El sistema toma la instantánea —paquete y filas—, marca `deleted_at`, y registra la baja en la misma transacción.
5. Devuelve `204`.

## 9. Flujos alternativos

### FA-001 — El paquete está activo y con productos

**Comportamiento:** se retira igual. Sale de la oferta y del hotlink en el acto; sus productos no cambian.

## 10. Excepciones

### EX-001 — El paquete no existe

**Respuesta del sistema:** `404` — *«No existe un paquete con ese identificador.»*

### EX-002 — El paquete ya está retirado

**Respuesta del sistema:** `409` — *«El paquete ya está retirado.»* **Se distingue** del inexistente por lo mismo que en `RF-PM-006`: el catálogo devuelve los retirados a quien tiene `packages:read`, no hay nada que ocultar, y quien retira dos veces merece saber que la primera funcionó.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Motivo presente y con contenido | El motivo de la eliminación es obligatorio. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-298` | El sistema retira el paquete con `204`, y la fila conserva su `status` y sus **filas de asociación** |
| `CA-PM-299` | El sistema rechaza con `400` un motivo ausente, vacío o de solo espacios, **sin consultar nada** |
| `CA-PM-300` | El sistema responde `404` al inexistente y `409` al **ya retirado**, distinguiéndolos |
| `CA-PM-301` | `audit_deletion_log` tiene la fila `LOGICAL` con el motivo, el actor y la instantánea **con los productos y sus descuentos** |
| `CA-PM-302` | El paquete retirado desaparece de la oferta y del hotlink y sigue en el catálogo con `includeDeleted`, y su detalle trae el motivo |
| `CA-PM-303` | Sus productos siguen activos en el catálogo y en los demás paquetes que los contengan; y el código retirado **no se puede reutilizar** en un alta |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Retirar un paquete vacío | Se retira igual: es un error que se corrige retirando |
| Dos retiros simultáneos | El bloqueo los ordena; el segundo recibe `409` |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se borran las filas de asociación al retirar? | **No.** Son lo que el paquete retirado decía contener, y una venta pasada las necesitará. Borrarlas dejaría un paquete que dice «tuve productos» sin decir cuáles. La instantánea de la auditoría las lleva **además**, por si la venta necesita el descuento del momento del retiro |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda `RF-PM-006` entero; lo propio es que **las filas de asociación permanecen** y que la instantánea las lleva, porque una venta pasada las necesitará. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageDeletionIT`). Sin enmiendas de comportamiento. La instantánea lleva el paquete y, bajo `items`, cada fila con producto, forma y valor; `CA-PM-299` comprueba que el motivo inválido no cuesta ni una sentencia. | Responsable técnico |
