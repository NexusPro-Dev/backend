# SPEC — `RF-PM-023` Asociar un producto a un paquete, con su descuento

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-023` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Meter un producto en un paquete **diciendo cuánto se rebaja** —un porcentaje o un importe fijo—, sin dejar al producto por debajo de cero y sin romper lo que el paquete promete a quien lo compre.

## 2. Contexto

**Es la operación que define al paquete.** El alta (`RF-PM-017`) crea un envase vacío; aquí se decide **qué se promete y a qué precio**, y por eso concentra cinco reglas: la moneda (`RN-PM-035`), el descuento (`RN-PM-037`), la unicidad por pareja (`RN-PM-038`), qué se puede asociar (`RN-PM-039`) y el origen común de los upgrades (`RN-PM-044`). Cada una existe para que la oferta pueda enseñar el paquete entero a alguien que pueda comprarlo entero.

## 3. Actores

| Actor | Papel |
|---|---|
| Administrador | Asocia el producto con su descuento |

## 4. Alcance

### 4.1 Incluye

- Asociar un producto **activo, no retirado y en la moneda del paquete** a un paquete **vivo**, activo o inactivo.
- Declarar el descuento: **forma** (`PORCENTAJE` | `FIJO`) y **valor**, con las cotas de `RN-PM-037`.
- Rechazar el producto que ya está en el paquete, y el upgrade cuyo origen no coincide con el de los ya asociados.
- Devolver el paquete entero con su cuenta rehecha.

### 4.2 No incluye

- **Corregir** el descuento de un producto que ya está: `RF-PM-024`.
- **Sacarlo**: `RF-PM-025`.
- **Asociar varios de golpe.** Una petición, un producto: cada uno puede fallar por un motivo distinto, y una lista obligaría a decidir si se aplica a medias.
- **Cantidad.** Un producto entra una vez (`RN-PM-038`).
- **Un descuento sobre el paquete entero.** El descuento es **por producto**; el del paquete es la suma de los de sus productos, que es exactamente lo que `savings` publica.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-035` | El producto debe estar **en la moneda del paquete** | `requirements/pm.md` §5.1 |
| `RN-PM-037` | **El descuento no deja a ningún producto por debajo de cero**: porcentaje `0..100`, fijo `0..precio`; el gratuito solo cero | `requirements/pm.md` §5.1 |
| `RN-PM-038` | **Un producto entra una vez por paquete** | `requirements/pm.md` §5.1 |
| `RN-PM-039` | **Solo se asocia lo que se puede comprar**: activo y no retirado | `requirements/pm.md` §5.1 |
| `RN-PM-044` | **Los upgrades de un paquete comparten origen** | `requirements/pm.md` §5.1 |
| `RN-PM-036` | El precio se calcula — la respuesta trae la cuenta rehecha | `requirements/pm.md` §5.1 |
| `RN-PM-007` | El importe respeta los decimales de su moneda — el descuento fijo también | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción |
|---|---|---|---|
| Identificador del paquete | Sí | Dónde entra | Va en la ruta. Paquete **vivo** |
| Producto (`productId`) | Sí | Qué entra | Existe, **activo**, **no retirado**, **en la moneda del paquete**, **no asociado ya** |
| Forma del descuento (`discountType`) | Sí | `PORCENTAJE` o `FIJO` | Dominio cerrado, sin valor por omisión |
| Valor del descuento (`discountValue`) | Sí | Cuánto se rebaja | `PORCENTAJE`: `0..100` con hasta dos decimales. `FIJO`: `0..precio del producto`, con los decimales de la moneda (`RN-PM-007`). **El cero se admite** en las dos formas |

**Forma y valor van juntos y los dos son obligatorios**, incluso cuando el valor es cero: «este producto entra sin rebaja» es una declaración, no una omisión. Es la misma decisión que `RN-CM-016` tomó con las tasas: el tipo manda y el valor lo acompaña.

### 6.2 Salida

`201` con el paquete en la forma del detalle (`RF-PM-019`): sus `items` **completos** —no solo el nuevo—, cada uno con su producto, su descuento y su **precio dentro del paquete**, y los tres totales rehechos, `offerable` incluido.

**Devuelve el paquete entero y no la fila nueva**, como `RF-CM-007` devuelve todas las asociaciones de la tasa: lo que cambió no es un producto, es **el precio del paquete**, y eso solo se ve con todos.

## 7. Precondiciones y postcondiciones

**Precondiciones:** actor con `packages:update`; paquete vivo; producto activo, no retirado, en la moneda del paquete y no asociado; descuento dentro de cota; si es upgrade, origen igual al de los upgrades ya dentro.

**Postcondiciones:** existe la fila `(paquete, producto)` con su descuento; `audit_change_log` tiene una fila `CREATE` de `product_package_items` con la instantánea de la asociación; el paquete —que **no cambia de estado**— vale la nueva suma en todas sus lecturas, en la misma transacción.

## 8. Flujo principal

1. Llega la petición con el paquete en la ruta y el producto, la forma y el valor en el cuerpo.
2. El sistema valida la forma de los tres (§11).
3. El sistema resuelve el paquete **vivo**, bloqueándolo (`EX-001`).
4. El sistema resuelve el producto (`EX-002`) y comprueba que está **activo y no retirado** (`EX-003`) y **en la moneda del paquete** (`EX-004`).
5. El sistema comprueba que el producto **no está ya** en el paquete (`EX-005`).
6. El sistema comprueba el **descuento contra el precio de hoy** del producto (`EX-006`).
7. Si el producto es un upgrade, el sistema comprueba que **su origen coincide** con el de los upgrades ya asociados (`EX-007`).
8. El sistema inserta la asociación y la registra en la auditoría, en la misma transacción.
9. Devuelve `201` con el paquete entero.

**El paquete se bloquea y el producto no.** Dos asociaciones simultáneas al mismo paquete se ordenan por el bloqueo del paso 3 —la segunda ve a la primera, y `RN-PM-044` se comprueba sobre el estado real—; el producto no se escribe, de modo que no hay nada que proteger en él. La unicidad por pareja tiene además su red en la clave primaria: si dos peticiones pasaran el paso 5 a la vez, el segundo `INSERT` muerde y se traduce al mismo `EX-005`.

## 9. Flujos alternativos

### FA-001 — Descuento cero

**Comportamiento:** se asocia. El producto entra al paquete a su precio, y `savings` no cambia. Es un producto «de relleno» legítimo en un combo.

### FA-002 — El paquete está `INACTIVO`

**Comportamiento:** se asocia igual. Es el estado en el que un paquete **se arma**: exigir que esté activo para meterle productos, cuando activarlo exige tener dos, sería un círculo.

### FA-003 — El primer upgrade del paquete

**Condición:** no hay todavía ningún upgrade dentro.
**Comportamiento:** su origen **fija** el del paquete: los siguientes upgrades tendrán que coincidir con él. Los bots no fijan nada.

### FA-004 — El producto es un bot

**Comportamiento:** entra sin mirar orígenes (`RN-PM-044` es solo sobre upgrades).

## 10. Excepciones

### EX-001 — El paquete no existe o está retirado

**Respuesta del sistema:** `404` — *«No existe un paquete vivo con ese identificador.»*

### EX-002 — El producto no existe

**Respuesta del sistema:** `422` — *«El producto indicado no existe.»* Es el mismo trato que `RF-CM-007` da al producto de una asociación: la ruta señala al paquete, y lo que falla es un dato del cuerpo.

### EX-003 — El producto está inactivo o retirado

**Respuesta del sistema:** `409` — *«El producto no está a la venta: solo se asocia lo activo y no retirado.»* **Se distingue del inexistente**, al revés que en la lista pública y en las reseñas: quien llama es un administrador con `packages:update`, ve el catálogo entero y merece saber que el producto existe y por qué no entra.

### EX-004 — El producto está en otra moneda

**Respuesta del sistema:** `409` — *«El producto está en `COP` y el paquete en `USD`: un paquete solo reúne productos en su moneda.»* Nombra las dos.

### EX-005 — El producto ya está en el paquete

**Respuesta del sistema:** `409` — *«Ese producto ya está en el paquete. Corrija su descuento en lugar de asociarlo de nuevo.»*

### EX-006 — El descuento deja al producto por debajo de cero

**Condición:** `FIJO` mayor que el precio del producto; o cualquier valor mayor que cero sobre un producto **gratuito**.
**Respuesta del sistema:** `409` — *«El descuento supera el precio del producto (`49.00 USD`).»* Nombra el precio para que quien corrige no tenga que buscarlo. El porcentaje mayor que cien no llega aquí: es `VAL-003`.

### EX-007 — El upgrade no comparte origen

**Condición:** el producto es un upgrade y su membresía de origen no es la de los upgrades ya asociados.
**Respuesta del sistema:** `409` — *«Los upgrades de un paquete salen de la misma membresía: este sale de `PLATINO` y el paquete ya tiene upgrades desde `BECA`.»*

!!! danger "Por qué se rechaza aquí y no se resuelve en la oferta"

    Un paquete con upgrades de dos orígenes **no se le puede ofrecer a nadie**: quien está en `BECA` no puede comprar el que sale de `PLATINO`, y al revés. Dejarlo asociar produciría un paquete perfectamente configurado que la oferta oculta siempre, sin que nada lo diga — el defecto que este catálogo llama «no falla, calla». Rechazarlo al asociar es el único sitio donde el error tiene a alguien delante.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador del paquete con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | Producto, forma y valor presentes | El producto, la forma del descuento y su valor son obligatorios. |
| `VAL-003` | Forma dentro del dominio; porcentaje entre cero y cien con hasta dos decimales | La forma del descuento debe ser PORCENTAJE o FIJO, y el porcentaje debe estar entre 0 y 100. |
| `VAL-004` | Valor no negativo y con los decimales admitidos | El valor del descuento no puede ser negativo ni tener más decimales que su moneda. |
| `VAL-005` | Ningún campo desconocido | El cuerpo de la petición contiene campos no admitidos. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-304` | El sistema asocia el producto con `201` y devuelve el paquete **entero**, con el producto nuevo, su descuento y su precio dentro del paquete, y los totales rehechos |
| `CA-PM-305` | Con `FIJO`, el precio dentro del paquete es `precio − valor`; con `PORCENTAJE`, `precio − redondear(precio × p ÷ 100)` a los decimales de la moneda, **a la mitad hacia arriba** |
| `CA-PM-306` | El sistema admite el **descuento cero** en las dos formas, y `savings` no cambia |
| `CA-PM-307` | El sistema rechaza con `409` un `FIJO` **un céntimo mayor** que el precio, y admite el **igual** al precio: el producto queda a cero |
| `CA-PM-308` | Sobre un producto **gratuito**, el sistema rechaza cualquier valor mayor que cero y admite cero, en las dos formas |
| `CA-PM-309` | El sistema rechaza con `400` un porcentaje mayor que cien, negativo o con tres decimales, y un fijo con más decimales que la moneda |
| `CA-PM-310` | El sistema rechaza con `409` un producto **inactivo** y uno **retirado**, distinguiéndolos del **inexistente**, que es `422` |
| `CA-PM-311` | El sistema rechaza con `409` un producto en **otra moneda**, nombrando las dos |
| `CA-PM-312` | El sistema rechaza con `409` el producto que **ya está** en el paquete, y dos asociaciones **simultáneas** del mismo producto dejan **una** fila y un `409` |
| `CA-PM-313` | El sistema rechaza con `409` un upgrade cuyo **origen** no es el de los upgrades ya dentro, y admite bots sin mirar orígenes; el **primer** upgrade fija el origen |
| `CA-PM-314` | El sistema asocia a un paquete **inactivo**, rechaza con `404` uno **retirado**, y registra una fila `CREATE` de `product_package_items` en `audit_change_log` |

## 13. Casos límite

| Caso | Decisión |
|---|---|
| El precio del producto **baja** después por debajo del fijo | El producto cuenta cero (`RN-PM-037`), nadie vuelve a comprobar. Es el hueco temporal declarado |
| El producto se **desactiva** después | La fila permanece; el paquete deja de ofrecerse (`RN-PM-039`) y el detalle dice cuál lo detiene |
| El paquete tiene solo bots y entra el primer upgrade | Fija el origen; los bots ya dentro no se ven afectados |
| Se quita el único upgrade y entra otro de **otro** origen | Se admite: el origen del paquete lo fijan los upgrades **que hay**, no los que hubo |
| Un porcentaje de `100` | Se admite: el producto entra gratis dentro del paquete. Es exactamente lo que un «te regalamos el bot» necesita |
| El mismo producto en **otro** paquete | Se admite con otro descuento: el descuento es del paquete (`RN-PM-038`) |
| El producto tiene precio `0.00` y descuento `0` | Entra y suma cero. Un paquete puede regalar cosas |

## 14. Preguntas abiertas

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Un paquete puede tener **dos upgrades hacia destinos distintos** con el mismo origen —`BECA → VIP` y `BECA → ORO`—? | **Sí se puede asociar**, y queda escrito lo que significa: quien lo compre recibiría dos membresías sucesivas, y qué hace `MV` con eso es de la venta, que no existe. Rechazarlo aquí sería decidir por `MV`. Se anota como la primera pregunta que la venta del paquete tendrá que responder |
| 2 | ¿El descuento puede depender de quién compra? | **No.** Sería una promoción por persona, fuera del alcance desde el 26-08-2026 |
| 3 | ¿Por qué el producto inexistente es `422` y el inactivo `409`? | El inexistente es un dato del cuerpo que no es de nada, como en `RF-CM-007`; el inactivo **existe** y choca con una regla. Distinguirlos aquí es correcto porque quien llama ve el catálogo entero; en la lista pública y en las reseñas no lo es, y por eso allí es un solo `404` |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 15-09-2026 | Redacción inicial. **Es la operación que define al paquete** y concentra cinco reglas. Las decisiones propias: **forma y valor obligatorios incluso en cero**, porque «sin rebaja» es una declaración; **el paquete se bloquea y el producto no**, con la clave primaria como red de la unicidad; **el primer upgrade fija el origen** y el que no coincide se rechaza **aquí** y no en la oferta, porque un paquete que nadie puede comprar es un defecto que calla; **el inactivo se distingue del inexistente** —`409` y `422`— porque quien llama ve el catálogo entero; y se devuelve el paquete entero, porque lo que cambió es su precio. Queda anotada la primera pregunta que la venta tendrá que responder: dos upgrades con el mismo origen y destinos distintos se pueden asociar. | Responsable técnico |
