# SPEC — `RF-PM-021` Cambiar el estado de un paquete

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-021` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Decidir si el paquete **se ofrece**, sin borrarlo: activarlo cuando está listo y desactivarlo sin tocar nada de lo que contiene.

## 2. Contexto

Es `RF-PM-005` para paquetes, con una condición más al activar: **al menos dos productos** (`RN-PM-040`), además de la descripción que el producto ya exigía (`RN-PM-014`). Y con una diferencia que conviene ver escrita: **activar no exige que los productos estén activos hoy**. Eso lo mira la oferta en cada lectura (`RN-PM-039`), porque un paquete puede activarse mientras se repone uno de sus productos, y exigirlo aquí obligaría a activar las cosas en un orden que nadie recordaría.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Activa o desactiva el paquete |

## 4. Alcance

### 4.1 Incluye

- Pasar un paquete vivo a `ACTIVO` o a `INACTIVO`.
- Exigir, al activar, **descripción** y **dos productos asociados como mínimo**.
- Devolver el detalle.

### 4.2 No incluye

- **Motivo.** Ni al activar ni al desactivar, como el producto (resolución de `RF-PM-005`).
- **Comprobar que los productos estén activos.** Es de la oferta (`RN-PM-039`) y el detalle lo dice.
- **Activar en cascada los productos.** El paquete no manda sobre sus productos.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-040` | **Dos productos y descripción para publicarse** | `requirements/pm.md` §5.1 |
| `RN-PM-041` | Nace inactivo; el estado decide si se ofrece | `requirements/pm.md` §5.1 |
| `RN-PM-039` | Que se ofrezca de verdad lo decide la oferta en cada lectura | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador | Sí | Cuál | Ruta. Paquete **vivo** |
| `status` | Sí | `ACTIVO` o `INACTIVO` | Dominio cerrado |

### 6.2 Salida

`200` con el detalle. Si el estado pedido es el que ya tiene, `200` sin escribir ni auditar — como el producto.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:update`; paquete vivo; para activar, descripción no vacía y al menos dos filas de asociación.

**Postcondiciones:** el estado nuevo, `updated_at` avanzado si cambió, fila `UPDATE` en la auditoría con antes y después.

## 8. Flujo principal

1. Llega la petición con el estado.
2. El sistema resuelve el paquete **vivo**, bloqueándolo (`EX-001`).
3. Si el estado es el actual, devuelve `200` sin escribir.
4. Si es `ACTIVO`, comprueba descripción (`EX-002`) y cuenta de productos (`EX-003`) — **juntos**: si faltan las dos cosas, lo dice de una vez.
5. Escribe, audita y devuelve el detalle.

## 9. Flujos alternativos

### FA-001 — Activar con un producto inactivo dentro

**Comportamiento:** **se activa.** El detalle devuelve `offerable: false` nombrando el producto, y la oferta no lo enseña hasta que ese producto vuelva. Es la diferencia con exigirlo aquí (§2).

### FA-002 — Desactivar

**Comportamiento:** sin condiciones. Sale de la oferta y del hotlink; sus productos no cambian.

## 10. Excepciones

### EX-001 — El paquete no existe o está retirado

**Respuesta del sistema:** `404`.

### EX-002 — Activar sin descripción

**Respuesta del sistema:** `409` — *«El paquete no tiene descripción: no se publica lo que no se explica.»*

### EX-003 — Activar con menos de dos productos

**Respuesta del sistema:** `409` — *«El paquete tiene N productos y necesita al menos dos: uno solo con descuento es una promoción, no un paquete.»*

Los dos van **juntos** en la misma respuesta cuando ocurren a la vez, como `RF-PM-001` devuelve juntas sus validaciones.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | `status` presente y en el dominio | El estado es obligatorio y debe ser ACTIVO o INACTIVO. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-291` | El sistema activa un paquete con descripción y dos productos, y devuelve el detalle `ACTIVO` con `updatedAt` avanzado |
| `CA-PM-292` | El sistema rechaza con `409` activar **sin descripción**, y con `409` activar con **cero** y con **un** producto |
| `CA-PM-293` | Sin descripción **y** con un producto, la respuesta trae **los dos** motivos |
| `CA-PM-294` | El sistema **activa** un paquete con un producto **inactivo** dentro, y el detalle lo devuelve `offerable: false` nombrándolo |
| `CA-PM-295` | Desactivar no exige nada, saca el paquete de la oferta y no toca sus productos |
| `CA-PM-296` | Pedir el estado que ya tiene responde `200` sin avanzar `updatedAt` ni auditar; un cambio real deja la fila `UPDATE` |
| `CA-PM-297` | Un paquete retirado responde `404`; un `status` fuera de dominio, `400` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| Activar y **después** desasociar hasta uno | El paquete sigue `ACTIVO` y deja de ofrecerse (`RN-PM-040`); nadie lo desactiva solo |
| Dos activaciones simultáneas | El bloqueo las ordena; la segunda no escribe |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Debería activar exigir que **todos** los productos estén activos? | **No** (§2). Se exige lo que es del paquete —descripción y tamaño— y no lo que es de otras filas y cambia con el tiempo. La oferta lo mira en cada lectura, y el detalle lo dice |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. Hereda `RF-PM-005` con la condición de `RN-PM-040` —dos productos y descripción, y los dos motivos juntos—. **Activar no exige productos activos**: eso cambia con el tiempo, lo mira la oferta y lo dice el detalle. | Responsable técnico |
| 0.2.0 | 15-09-2026 | **Construida** (`PackageStatusIT`). Sin enmiendas de comportamiento. Nota de construcción: la cuenta de productos para `EX-003` sale de la misma lectura de hermanas que usa la asociación (`findSiblings`), y los dos motivos viajan como dos `errors` en un solo `409` (`CA-PM-293`). | Responsable técnico |
