# SPEC — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 28-08-2026 — ver §15 |
| Enmendada el | 07-09-2026 — **el alcance y la implementación son corregibles** (`RN-PM-019`, `RN-PM-020`), y **no admiten vaciarse**. Ver §15 |
| Enmendada el | 08-09-2026 — **el precio público es corregible Y SÍ admite vaciarse** (`RN-PM-023`), y `RN-PM-006` deja de exigir «mayor que cero». Ver §15 |
| Enmendada el | 12-09-2026 — **el segundo precio es el de COMPRA** (`RN-PM-023`): `purchasePrice` sustituye a `publicPrice`, corregible y vaciable igual, y es **donde se guarda lo que costó**. Ver §15 |
| Enmendada el | 14-09-2026 — **la respuesta de la edición devuelve `rating`, que la edición no toca** (`RN-PM-031`, `RF-PM-009`). Ver §15 |
| Enmendada el | 14-09-2026 — **el enlace del video se corrige y SÍ admite vaciarse** (`RN-PM-032`), en los dos tipos. Ver §15 |
| Enmendada el | 14-09-2026 — **el icono de un upgrade solo se vacía si hay PORTADA** (`RN-PM-034`, `RF-PM-014`); la portada no se corrige por aquí, y la respuesta gana `coverImageUrl`. Ver §15 |

---

## 1. Objetivo

Corregir lo que se puede corregir de un producto, sin reescribir lo que ya se vendió.

## 2. Contexto

Un producto se equivoca de nombre, se le escapa una falta en la descripción o cambia de precio. Sin este requerimiento la única salida sería retirarlo y crear otro, lo que rompe la referencia de todo lo que apunte al producto anterior.

**Lo que no se puede corregir es lo que define qué se compró.** El **tipo**, el **código** y **las dos membresías** quedan fuera: cambiarlos convierte lo comprado en otra cosa, y quien pagó por subir a un nivel se encontraría con un derecho distinto del que adquirió. Quien necesite otro destino registra otro producto y retira el anterior.

**El precio se puede cambiar siempre, y eso obliga a la compra futura.** Resuelto el 26-08-2026: corregir el precio nunca reescribe lo vendido, porque **cada compra guardará el importe que se pagó** en el momento de comprar. Es una condición que este requerimiento **impone a un módulo que todavía no existe**, y por eso queda escrita aquí y en `requirements/pm.md` §1.4: si la compra leyera el precio del producto, esta operación pasaría a reescribir el pasado sin que nadie la hubiera tocado.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Corrige los datos comerciales de un producto |

## 4. Alcance

### 4.1 Incluye

- Corregir el **nombre**, la **descripción**, **los dos precios**, la **moneda** y la **vigencia**.
- Aplicar solo lo que llegue: lo que no se envía no se toca.
- Dejar constancia en la auditoría de cambios de **qué cambió**, con su valor anterior y el nuevo.

### 4.2 No incluye

- **Cambiar el tipo** (`RN-PM-001`), **el código** (`RN-PM-013`) ni **ninguna de las dos membresías**. Ver §2.

    **El origen entra en esa lista por el mismo motivo que el destino**, y conviene decirlo porque es nuevo: cambiar de quién sale un upgrade **reescribe a quién iba dirigido lo que ya se vendió**. Quien necesite otra pareja registra otro producto y retira el anterior.
- **Exigir un motivo del cambio.** El Art. V.13 solo lo obliga en las eliminaciones, y la auditoría ya registra qué cambió, de cuánto a cuánto, quién y cuándo. Resuelto el 26-08-2026.
- **Activar o desactivar**, que es `RF-PM-005`: el estado no es un dato comercial, es una decisión de publicación.
- **Retirar el producto**, que es `RF-PM-006`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-013` | El código no se libera nunca, y es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-005` | Nombre único entre los vivos | `requirements/pm.md` §5.1 |
| `RN-PM-006` | **Ningún precio es negativo**, y el cero se admite | `requirements/pm.md` §5.1 |
| `RN-PM-007` | **Los dos precios respetan** los decimales de su moneda | `requirements/pm.md` §5.1 |
| `RN-PM-023` | **El precio de compra es opcional, se corrige y se puede vaciar** — es lo que NEXUS paga por el producto, y esta edición es donde hoy se registra | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |
| `RN-PM-015` | La vigencia se mide en días y es opcional | `requirements/pm.md` §5.1 |
| `RN-PM-019` | El alcance dice hasta dónde se muestra, y **se corrige** | `requirements/pm.md` §5.1 |
| `RN-PM-020` | La implementación dice si lo comprado se aplica solo, y **se corrige** | `requirements/pm.md` §5.1 |
| `RN-PM-032` | Un producto puede enlazar un video — **se corrige y se vacía**, en los dos tipos, con la misma comprobación de forma que en el alta | `requirements/pm.md` §5.1 |
| `RN-PM-034` | **Un upgrade siempre tiene con qué pintarse: portada o icono** — aquí, que **el icono de un upgrade sin portada no se vacía** | `requirements/pm.md` §5.1 |
| `RN-PM-016` | El icono solo existe en el upgrade — sigue rechazándose en el bot, y **enmendada**: en el upgrade es obligatorio mientras no haya portada | `requirements/pm.md` §5.1 |
| `RN-PM-033` | **La portada es un archivo** — **no se corrige por aquí**: tiene sus endpoints (`RF-PM-014`, `RF-PM-015`); la respuesta la devuelve en `coverImageUrl` | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Identificador del producto | Sí | Cuál se corrige | Debe existir y no estar retirado |
| Nombre | No | Nombre nuevo | Único entre los vivos (`RN-PM-005`); **no admite vaciarse** |
| Descripción | No | Descripción nueva | **Sí admite vaciarse**, porque es opcional |
| Icono | No | Nombre del icono nuevo | **Sí admite vaciarse, pero en un upgrade solo si tiene portada** (`RN-PM-034`, 14-09-2026): sin portada, el nulo y la cadena vacía se rechazan. Solo en el upgrade: en un producto de tipo bot, cualquier valor distinto de nulo se rechaza (`RN-PM-016`), y el nulo sigue siendo un vaciado sin efecto |
| ~~Portada~~ | — | **No entra por aquí.** Es un archivo, no un campo del cuerpo JSON: se sube con `RF-PM-014` y se quita con `RF-PM-015`. Un `coverImageUrl` en el cuerpo **se rechaza con `400`**, como todo campo desconocido: ignorarlo haría creer que el cambio se aplicó (`CA-PM-033`) | La respuesta la devuelve, como todas las lecturas |
| Enlace del video | No | Dirección nueva del video | **Sí admite vaciarse**, y la cadena vacía vacía igual. **En los dos tipos**, sin la condición cruzada del icono. Misma forma que en el alta: URL absoluta `http` o `https`, sin espacios, hasta 500 caracteres (`RN-PM-032`) |
| Precio **del sistema** | No | Precio nuevo, el que se cobra | **No negativo** y con los decimales de su moneda. **NO admite vaciarse**: la columna es obligatoria, y «bórralo» no tiene ningún estado al que llevar el producto |
| Precio **de compra** | No | Lo que NEXUS pagó por el producto | Mismas condiciones de importe. **SÍ admite vaciarse**, y ahí va con la descripción y la vigencia: su nulo es un estado legítimo —«no se conoce el costo»— de modo que el nulo explícito **es una orden** (`RN-PM-023`). **Es donde se guarda el precio cuando el producto se compra**: hoy lo escribe quien administra, con esta operación |
| Moneda | No | Moneda nueva | Debe existir y estar activa. **Cambiarla reinterpreta los dos importes**, y los dos se miden contra sus decimales |
| Vigencia | No | Vigencia nueva, en días | Mayor que cero. **Sí admite vaciarse**, y hacerlo convierte el producto en uno que no caduca |
| Alcance | No | Alcance nuevo | **NO admite vaciarse**: es obligatorio en la columna, de modo que el nulo explícito se rechaza en lugar de borrar (`RN-PM-019`) |
| Implementación | No | Implementación nueva | Igual. **NO admite vaciarse** (`RN-PM-020`) |

**Ausente y vacío no son lo mismo.** No enviar un campo significa «déjalo como está»; enviarlo vacío significa «bórralo», y solo lo admiten **la descripción, el icono, la vigencia y el precio de compra**. Confundir los dos estados hace que corregir un nombre borre la descripción sin que nadie lo pida.

**Y en el precio de compra esa distinción decide algo que se ve en el margen**: vaciarlo no es ponerlo a cero — es **declarar que el costo no se conoce**. Los dos son estados alcanzables desde esta operación y no significan lo mismo: uno dice «no costó nada» y el otro dice «no sé cuánto costó», y un informe de márgenes los trata distinto.

### 6.2 Salida

**Desde el 14-09-2026 la respuesta lleva `rating`** (`RN-PM-031`): la respuesta de la edición devuelve `rating`, que la edición no toca. Es un objeto **presente siempre**, con `average` —dos decimales, **nulo** cuando no hay reseñas— y `count` —**cero** cuando no hay—. Cuentan solo las reseñas **vivas**: una retirada sale de la cuenta en el acto. La enmienda la construye `RF-PM-009` (`T-10`, `T-11`), y lo que este requerimiento tiene que conservar es su número de sentencias: el agregado viaja **en la misma consulta** que el producto.

| Dato | Descripción |
|---|---|
| Producto | El producto con los cambios ya aplicados, en la misma forma que devuelve `RF-PM-003` |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de productos.
- El producto existe y no está retirado.

**Postcondiciones**

- El producto queda con los valores nuevos y **conserva su identificador**, su tipo y **sus dos membresías**.
- La auditoría de cambios contiene un evento con **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo.

## 8. Flujo principal

1. El actor envía el identificador y los campos que quiere corregir.
2. El sistema comprueba que el producto existe y no está retirado.
3. El sistema valida cada campo recibido según su regla.
4. Si llega un nombre, el sistema comprueba que no lo tiene ya otro producto vivo.
5. Si llega **cualquiera de los dos precios** o una moneda, el sistema resuelve **cuál será la moneda final** y mide contra ella **los importes que vayan a quedar** — el que llega y el que ya estaba—, porque cambiar solo la moneda puede dejar sin caber a un precio que nadie tocó.
6. El sistema aplica los cambios y emite el evento de auditoría con lo que efectivamente cambió.
7. El sistema devuelve el producto corregido.

## 9. Flujos alternativos

### FA-001 — Petición sin ningún cambio efectivo

**Cuándo ocurre:** el actor envía los mismos valores que el producto ya tiene.

1. El sistema responde con normalidad.
2. **No emite evento de auditoría**: un cambio que no cambió nada no es un cambio, y registrarlo llena la línea de tiempo de ruido que oculta lo que sí ocurrió.

### FA-002 — Solo se corrige la descripción

**Cuándo ocurre:** llega únicamente la descripción, quizá vacía.

1. El nombre, el precio y la moneda **no se tocan** ni se revalidan.
2. La descripción queda vacía si así llegó.

## 10. Excepciones

### EX-001 — Producto inexistente o retirado

**Condición:** el identificador no corresponde a ningún producto, o corresponde a uno retirado.
**Respuesta del sistema:** rechaza la corrección. Un producto retirado **no se corrige**: lo que se retiró debe quedar como estaba para que lo que lo referencie siga diciendo la verdad.

### EX-002 — Nombre ya en uso

**Condición:** el nombre nuevo lo tiene otro producto vivo.
**Respuesta del sistema:** rechaza la corrección indicando el campo duplicado, y no aplica **ninguno** de los cambios enviados.

### EX-003 — Moneda inexistente o inactiva

**Condición:** la moneda nueva no existe o está desactivada.
**Respuesta del sistema:** rechaza la corrección entera.

### EX-004 — Se intenta cambiar el tipo, el código o el destino

**Condición:** la petición trae el tipo, el código o la membresía destino.
**Respuesta del sistema:** **rechaza la petición**, y no ignora los campos en silencio. Ignorarlos haría creer al actor que el cambio se aplicó.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Identificador con formato válido | El identificador indicado no tiene un formato válido. |
| `VAL-002` | El nombre no admite vaciarse | El nombre del producto no puede quedar vacío. |
| `VAL-003` | Longitud del nombre y de la descripción | El valor excede la longitud admitida. |
| `VAL-004` | **Ningún precio negativo**, y el del sistema **no admite vaciarse** | El precio no puede ser negativo. **Con el campo que lo incumple** — `price` o `purchasePrice` |
| `VAL-005` | Decimales **de cada precio** según su moneda | El precio no admite más decimales que los de su moneda. **Con el campo que lo incumple** |
| `VAL-006` | El tipo, el código y el destino no se admiten | El tipo, el código y la membresía destino no se pueden modificar. |
| `VAL-007` | El alcance no admite vaciarse, y debe estar dentro del dominio | El alcance del producto es obligatorio y debe ser uno de los admitidos. |
| `VAL-008` | La implementación no admite vaciarse, y debe estar dentro del dominio | La implementación del producto es obligatoria y debe ser una de las admitidas. |
| `VAL-009` | Formato del enlace del video | El enlace del video debe ser una dirección absoluta http o https, sin espacios y de hasta 500 caracteres. |
| `VAL-010` | **El icono de un upgrade sin portada no se vacía** | Un upgrade sin portada no puede quedarse sin icono: suba primero una portada. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-030` | El sistema corrige el nombre, la descripción, el precio y la moneda, y conserva el identificador, el tipo y el destino |
| `CA-PM-031` | El sistema aplica **solo los campos enviados** y deja intactos los ausentes |
| `CA-PM-032` | El sistema distingue un campo **ausente** de uno enviado **vacío**: vaciar la descripción la borra, y enviar el nombre vacío se rechaza |
| `CA-PM-033` | El sistema rechaza la petición que trae el tipo, el código o la membresía destino, en lugar de ignorarlos |
| `CA-PM-034` | El sistema rechaza un nombre que ya tiene otro producto vivo, y **no aplica ninguno** de los demás cambios enviados |
| `CA-PM-035` | El sistema rechaza una moneda inactiva y no aplica ningún cambio |
| `CA-PM-036` | El sistema rechaza corregir un producto retirado |
| `CA-PM-037` | El sistema registra en la auditoría de cambios **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo |
| `CA-PM-038` | El sistema **no registra evento** cuando la petición no cambia nada |
| `CA-PM-039` | El sistema rechaza la corrección a un actor sin el permiso de modificación de productos |
| `CA-PM-083` | El sistema **corrige un producto inactivo**, que es el estado en el que nace: sin esto no habría forma de ponerle la descripción que `RF-PM-005` exige para publicarlo |
| `CA-PM-084` | El sistema **no exige motivo** para corregir, ni siquiera al cambiar el precio |
| `CA-PM-094` | El sistema corrige la **vigencia**, y **vaciarla** convierte el producto en uno que no caduca |
| `CA-PM-099` | El sistema corrige el **icono**, y **vaciarlo** con nulo explícito lo deja sin icono |
| `CA-PM-100` | El sistema rechaza el icono en un producto de tipo bot, también cuando llega en una corrección |
| `CA-PM-119` | El sistema corrige el **alcance** y la **implementación**, y el evento de auditoría registra el valor anterior y el nuevo de cada una |
| `CA-PM-120` | El sistema **rechaza vaciarlas** con nulo explícito, al revés que la descripción, el icono y la vigencia: son obligatorias y no admiten ausencia |
| `CA-PM-121` | El sistema rechaza un valor **fuera del dominio** en cualquiera de las dos, y no aplica ninguno de los demás cambios enviados |
| `CA-PM-122` | El sistema **no registra evento** cuando la corrección envía el mismo alcance o la misma implementación que el producto ya tenía |
| `CA-PM-153` | El sistema **corrige el precio de compra** sin tocar el del sistema, y el evento registra el valor anterior y el nuevo |
| `CA-PM-154` | El sistema **vacía el precio de compra** con nulo explícito, y el producto queda sin costo conocido — **vaciarlo no es ponerlo a cero**, y los dos casos se prueban por separado |
| `CA-PM-155` | El sistema **rechaza vaciar el precio del sistema** con nulo explícito, al revés que el de compra |
| `CA-PM-156` | El sistema **admite corregir cualquiera de los dos a cero**, y no lo confunde con vaciarlo |
| `CA-PM-157` | El sistema rechaza un **precio de compra** que no cabe en los decimales de la **moneda nueva**, aunque el del sistema sí quepa, y **no aplica ninguno** de los demás cambios |
| `CA-PM-225` | El sistema **corrige el enlace del video** —también en un producto de tipo **bot**— y el evento registra el valor anterior y el nuevo |
| `CA-PM-226` | El sistema **vacía el enlace del video** con nulo explícito **o con cadena vacía**, el producto queda sin video, y enviar el mismo enlace que ya tenía **no registra evento** |
| `CA-PM-227` | El sistema rechaza un enlace **sin forma de URL absoluta `http` o `https`** con `VAL-009`, nombra `videoUrl`, y **no aplica ninguno** de los demás cambios enviados |
| `CA-PM-234` | El sistema **rechaza vaciar el icono** —nulo o cadena vacía— de un upgrade **sin portada** con `VAL-010`, nombra `icon`, y **no aplica ninguno** de los demás cambios enviados; y **corregirlo por otro** sigue admitiéndose |
| `CA-PM-235` | El sistema **vacía el icono** de un upgrade **con portada**, y lo audita; y en un **bot**, `icon: null` sigue siendo un vaciado sin efecto y sin `VAL-010` |
| `CA-PM-236` | La respuesta de la corrección trae **`coverImageUrl`** —la dirección cuando hay portada, nulo y presente cuando no—, y un `coverImageUrl` en el cuerpo **se rechaza con `400`** sin cambiar nada, como todo campo desconocido |

## 13. Casos límite

- **Nombre igual al actual:** no es un duplicado consigo mismo. La unicidad debe excluir al propio producto, o corregir la descripción sin tocar el nombre acabaría rechazándose.
- **Nombre que solo difiere en mayúsculas o acentos del actual:** sí es un cambio —`Plan Oro` a `Plan oro`— y debe admitirse, porque el choque es contra **otros** productos, no contra uno mismo.
- **Cambiar la moneda sin cambiar el precio:** el importe se reinterpreta en la moneda nueva y **su valor no se convierte**. Debe quedar escrito que el sistema no hace conversión de divisa: cambiar de moneda es declarar que ese número siempre estuvo en la otra.
- **Precio con más decimales de los que admite la moneda nueva:** cambiar moneda y precio a la vez obliga a validar el precio contra la moneda **nueva**, no contra la anterior.
- **Cambiar solo la moneda con un precio de compra ya guardado:** el que hay que medir contra la moneda nueva **es el que nadie tocó**. Es el caso que se olvida al añadir el segundo importe: se valida el que llega en la petición y se deja pasar el otro, que queda con más decimales de los que su moneda admite y **sin que nada falle**.
- **Vaciar el precio de compra de un producto que nunca lo tuvo:** no cambia nada y **no emite evento**, como cualquier otra corrección que no corrige (`CA-PM-038`).
- **Poner el precio de compra a cero:** es un cambio y se registra. **No equivale a vaciarlo**: uno dice «no costó nada» y el otro dice «no se conoce».
- **Precio de compra por encima del de venta:** se admite y no avisa. Vender por debajo del costo es una decisión comercial, y **ninguna regla compara los dos importes** (`requirements/pm.md` §5.2.6).
- **Dos correcciones simultáneas del mismo producto:** la última debe quedar entera, y no una mezcla de las dos.

## 14. Preguntas abiertas

Ninguna. Dos se resolvieron el 26-08-2026 y **las otras dos quedaron respondidas por la aprobación de `RF-PM-001`**, que es lo que ocurre cuando una decisión anterior alcanza a una spec posterior: no se vuelven a preguntar, se anotan con lo que las cerró.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿Se puede cambiar el precio de un producto ya vendido? | **Sí, y la compra guardará el precio que se pagó.** Cada venta copiará el importe en el momento de comprar, de modo que corregir el catálogo **nunca reescribe el pasado** y esta operación sigue siendo una corrección de verdad. Es una **condición impuesta a un módulo que todavía no existe**, y por eso queda escrita también en `requirements/pm.md` §1.4: si la compra leyera el precio del producto, `RF-PM-004` pasaría a reescribir facturas sin que nadie la hubiera tocado. Se descartaron congelar el precio de lo vendido —llena el catálogo de productos casi idénticos y convierte cada cambio de precio en un alta y un retiro— y versionar el precio con vigencia, que es una tabla más y la puerta de entrada de las promociones que §1.3 deja fuera |
| 2 | ¿Cambiar el precio exige motivo? | **No.** El Art. V.13 solo lo obliga en las eliminaciones, y la auditoría de cambios ya registra **qué** cambió, **de cuánto a cuánto**, **quién** y **cuándo**. Lo único que falta es el «por qué», y exigirlo en cada corrección de una coma es el camino más corto para que ese campo se llene de «ajuste» y deje de significar nada |
| 3 | ¿Se puede corregir un producto inactivo? | **Sí, y no era opcional.** Lo cerró `RN-PM-012` al aprobarse `RF-PM-001`: si el producto **nace inactivo**, prohibir corregirlo en ese estado dejaría a todo producto nuevo sin forma de recibir la descripción que `RN-PM-014` exige para publicarlo. La pregunta se hizo cuando el estado inicial todavía estaba abierto; al cerrarse aquel, esta dejó de tener dos salidas |
| 4 | Si el producto lleva código, ¿se puede corregir? | **No: es inmutable** (`RN-PM-013`, aprobada con `RF-PM-001`). El código es la referencia desde la que una factura dirá qué se vendió; corregirlo rompería exactamente aquello para lo que existe. Se suma al tipo y al destino en `EX-004`, `VAL-006` y `CA-PM-033`: la petición que lo traiga **se rechaza**, no se ignora |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada.** El precio se puede corregir siempre, y eso **impone una condición a la compra futura**: cada venta guardará el importe que se pagó, o esta operación pasaría a reescribir el pasado. **No se exige motivo** para corregir. Las otras dos preguntas las cerró la aprobación de `RF-PM-001`: corregir un producto inactivo no solo se admite —es imprescindible, porque es el estado en el que nace—, y el **código es inmutable**, de modo que se suma al tipo y al destino entre lo que la petición no puede traer. Dos criterios nuevos, `CA-PM-083` y `CA-PM-084`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con cuatro preguntas abiertas. | Responsable técnico |
| 0.3.0 | 27-08-2026 | La **vigencia** se suma a lo corregible (`RN-PM-015`), con el mismo criterio que el precio: corregirla no reescribe lo vendido **porque cada compra guardará la vigencia que compró**. Es el tercer campo que admite vaciarse —junto con la descripción—, y vaciarlo convierte el producto en uno que no caduca. `CA-PM-094`. | Responsable del proyecto |
| 0.4.0 | 28-08-2026 | **El icono se suma a lo corregible** (`RN-PM-016`), y con el criterio opuesto al del tipo: el tipo no se corrige porque define qué otorga el producto, mientras que el icono es su **aspecto** y cambiarlo no reescribe lo comprado. Admite vaciarse con nulo explícito, como la descripción y la vigencia. Lo que **no** admite excepción es la regla: en un producto de tipo bot el icono se rechaza con `VAL-013` aunque llegue en un `PATCH`. Entran `CA-PM-099` y `CA-PM-100`. | Responsable técnico |
| 0.5.0 | 07-09-2026 | **El alcance y la implementación entran del lado corregible** (`RN-PM-019`, `RN-PM-020`), y esa es la única decisión de esta enmienda. La frontera de esta spec era «lo que define **qué derecho otorga** el producto no se toca»: el tipo, el código y las dos membresías. **Ninguna de las dos nuevas lo define** —una dice hasta dónde se muestra y la otra quién lo aplica—, de modo que congelarlas habría obligado a **registrar un producto nuevo para mover un enlace de sitio**, con lo vendido colgando del viejo. **Y no admiten vaciarse**, que es donde se apartan de la descripción, el icono y la vigencia: son obligatorias en la columna, y un nulo explícito no puede ser una orden de borrado de algo que no puede faltar — se rechaza con `VAL-007` y `VAL-008`. **Lo que se corrige aquí no reescribe ninguna venta anterior**, porque `RN-MV-002` obliga a que la venta **copie la implementación en su línea**; esa copia **todavía no está construida** (`requirements/mv.md` §5.4) y hasta que lo esté nadie lee este valor desde `MV`. Entran `CA-PM-119` a `CA-PM-122`. | Responsable del proyecto |
| 0.6.0 | 08-09-2026 | **El precio público entra del lado corregible, y SÍ admite vaciarse** (`RN-PM-023`), por decisión del responsable del proyecto. Ahí va con la descripción, el icono y la vigencia y **no con el precio del sistema**: su nulo es un estado legítimo de la columna —«se anuncia con el precio del sistema»— de modo que el nulo explícito **es una orden** y no un error, mientras que el del sistema no tiene ningún estado al que «bórralo» pueda llevar el producto. **Y esa distinción decide algo que se ve en la tienda**: vaciarlo **no es ponerlo a cero** — uno anuncia lo que cuesta y el otro anuncia «gratis»—, de modo que los dos casos se prueban por separado (`CA-PM-154`, `CA-PM-156`). **`RN-PM-006` se relaja**: `VAL-004` pasa de «mayor que cero» a «no negativo» y **nombra el campo** que incumple, porque con dos importes un mensaje que no distingue obliga a probar los dos. **El paso 5 del flujo cambia de forma y ese es el cambio con más filo del día**: ya no se validan «los campos que llegan» sino **los importes que van a quedar** medidos contra la **moneda final**. Con dos precios aparece un caso que con uno no existía —cambiar **solo la moneda** deja sin caber a un importe que nadie tocó—, y el defecto **no falla**: guarda un precio con más decimales de los que su moneda admite. Entran `CA-PM-153` a `CA-PM-157` y cuatro casos límite. | Responsable del proyecto |
| 0.7.0 | 12-09-2026 | **El segundo precio pasa a ser el de COMPRA**, por decisión del responsable del proyecto (`requirements/pm.md` v0.23.0 §5.2.6): lo que NEXUS paga por el producto cuando tiene que comprarlo. `purchasePrice` **sustituye** a `publicPrice`, y **esta operación es donde hoy se registra lo que costó**: corregible y vaciable exactamente como lo era el público, porque la forma era correcta — lo que cambia es lo que significa cada estado. **El nulo pasa de «se anuncia con el del sistema» a «no se conoce el costo»**, y el cero de «gratis» a «no costó nada»; siguen siendo dos estados distintos y se siguen probando por separado (`CA-PM-154`, `CA-PM-156`). `CA-PM-153` a `CA-PM-157` cambian de nombre de campo sin cambiar de forma; §13 gana el caso del costo por encima del precio, que se admite sin aviso. | Responsable del proyecto |
| 0.8.0 | 14-09-2026 | **Entra `rating` en la respuesta** —el promedio y la cantidad de reseñas vivas del producto— por `RN-PM-031` ([`requirements/pm.md`](../../../requirements/pm.md) v0.24.0 §5.2.7): la respuesta de la edición devuelve `rating`, que la edición no toca. Enmienda de Art. I.7 declarada por el plan de [`RF-PM-009`](../009-resenar-producto/plan.md) §4.1 y construida por sus tareas `T-10` y `T-11`; los criterios que la prueban son `CA-PM-180` a `CA-PM-182` de aquella tripleta. **El promedio no se guarda en `products`**: se cuenta, por un `LEFT JOIN LATERAL` sobre el índice parcial de `product_comments`, para que ninguna copia pueda quedarse atrás. | Responsable del proyecto |
| 0.9.0 | 14-09-2026 | **El enlace del video entra del lado corregible, y SÍ admite vaciarse** (`RN-PM-032`, [`requirements/pm.md`](../../../requirements/pm.md) v0.27.0 §5.2.8), por decisión del responsable del proyecto. Va con la descripción, el icono, la vigencia y el precio de compra: su nulo es un estado legítimo —«no tiene video»— y el nulo explícito es una orden. **En los dos tipos**, sin la condición cruzada del icono, y con la misma forma que en el alta (`VAL-009`). Nacen `CA-PM-225` a `CA-PM-227`. Enmienda de Art. I.7. | Responsable del proyecto |
| 0.11.0 | 14-09-2026 | **Construida la enmienda de la portada** (`T-24`). Una precisión de construcción: un `coverImageUrl` en el cuerpo **se rechaza con `400`** —`FAIL_ON_UNKNOWN_PROPERTIES`— y no se ignora, como todo campo desconocido; ignorarlo haría creer que el cambio se aplicó (`CA-PM-236` reescrito). | Responsable técnico |
| 0.10.0 | 14-09-2026 | **El icono de un upgrade solo se vacía si hay portada** (`RN-PM-034`, [`requirements/pm.md`](../../../requirements/pm.md) v0.29.0 §5.2.9), por decisión del responsable del proyecto: un upgrade siempre tiene portada o icono, **al registrar y en cada corrección**. Es la segunda cara de la regla —la primera es el alta, `RF-PM-001` v0.13.0; la tercera, quitar la portada, `RF-PM-015`— y vive en `Product.update`, que es el único que ve las dos columnas. **Nace `VAL-010`** y se rechaza **sin aplicar nada más**, como todo rechazo de esta operación; en el bot nada cambia. **La portada no se corrige por aquí**: es un archivo con sus endpoints. La respuesta gana `coverImageUrl`. `CA-PM-234` a `CA-PM-236`. Enmienda que construye `RF-PM-014` (Art. I.7). | Responsable del proyecto |
