# PLAN — `RF-PM-004` Editar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-004` |
| Especificación | [`spec.md`](spec.md) v0.3.0 |
| `spec.md` aprobada el | 26-08-2026 |
| Estado | **Aprobado** |
| Autor | Responsable técnico |
| Aprobado por | Responsable del proyecto |
| Enmendado el | 27-08-2026 — `RN-PM-015`; 02-09-2026 — la membresía de **origen** (`RN-PM-017`, `RN-PM-018`); 07-09-2026 — el **alcance** y la **implementación**, corregibles y **no vaciables** (`RN-PM-019`, `RN-PM-020`); 08-09-2026 — el **precio público**, corregible y **sí vaciable** (`RN-PM-023`), y el paso 5 reescrito, §5; 12-09-2026 — **el segundo precio es el de COMPRA** (`purchasePrice`), §4; 14-09-2026 — **el enlace del video**, corregible y **sí vaciable** (`RN-PM-032`), §4; 14-09-2026 — **el icono de un upgrade solo se vacía con portada** (`RN-PM-034`), §4; 15-09-2026 — **el alcance de cuatro valores** (`RN-PM-019`); 22-09-2026 — **los enlaces se corrigen EN BLOQUE** (`RN-PM-048`, `RN-PM-049`), §4 |
| Fecha de aprobación | 26-08-2026 |

---

## 1. Enfoque

Una corrección parcial: se aplica lo que llega y se deja intacto lo que no. El problema de fondo de esta operación **no es la validación, es distinguir tres estados de un campo** —ausente, presente y nulo, presente con valor—, y ese problema ya está resuelto en el sistema.

**Se reutiliza `Patchable` de `shared/patch`**, que `RF-SP-027` creó. No se vuelve a intentar con `Optional`: allí quedó escrito que **falló en silencio**, porque al deserializar Jackson entrega `Optional.empty()` tanto para el campo ausente como para el nulo explícito —`getAbsentValue()` delega en `getNullValue()`—, y los dos estados que hay que separar se funden en uno. El síntoma no se parecía a la causa: enviar solo el nombre rechazaba la petición por «apellido vacío».

Aquí la distinción decide dos comportamientos opuestos: **la descripción admite vaciarse** y el **nombre no**.

## 2. Cambios de esquema

**Ninguno.**

## 3. Componentes afectados

| Capa | Componente | Responsabilidad |
|---|---|---|
| `domain/models` | `Product.update(...)` | Aplica lo recibido y **devuelve qué cambió de verdad** |
| `domain/repository` | `ProductRepository.findAliveByIdForUpdate` | Bloqueo pesimista sobre la fila |
| `domain/service` | `UpdateProductService` | Orden de verificación de §5 |
| `application` | `UpdateProductRequest` | Con `Patchable` en los cuatro campos |
| `interfaces` | `ProductController` | `PATCH /api/v1/products/{id}` |

## 4. Contrato de API

`PATCH /api/v1/products/{id}` con los campos a corregir. `200` con el producto en la misma forma que `RF-PM-003`.

- **El tipo, el código y las dos membresías no se admiten**, y su presencia **devuelve `400`** (`CA-PM-033`). El origen es inmutable por el mismo motivo que el destino: cambiarlo convierte el producto en **otro salto**, con otro precio y otra clientela, y quien lo compró ayer compró el anterior. Se rechaza y no se ignora: ignorarlos haría creer al actor que el cambio se aplicó. Se consigue con `FAIL_ON_UNKNOWN_PROPERTIES`, que ya está activo, más un mensaje propio de `VAL-006` si llegan con nombre conocido.
- **`description: null` la borra; `name: null` se rechaza** (`VAL-002`).
- **No se exige motivo** (`spec.md` §14, resolución 2).
- **El alcance y la implementación se corrigen, y el nulo explícito NO los vacía.** Son los dos primeros campos `Patchable` del módulo con **tres** estados en los que el tercero es un rechazo: ausente deja como está, con valor corrige, y **presente con nulo devuelve `400`** — al revés que la descripción, el icono y la vigencia, donde el nulo es una orden de borrado. La diferencia no es de gusto: aquellos admiten faltar en la columna y estos no, de modo que «bórralo» no tiene ningún estado al que llevar el producto.
- **El icono de un upgrade solo se vacía si hay portada** (14-09-2026, `RN-PM-034`, enmienda de `RF-PM-014`): en `Product.update`, en la rama del icono y **después** de normalizar —el nulo y `""` son los dos un vaciado—, si el tipo es upgrade, el valor es nulo y `coverImageId` es nulo, `VAL-010` nombrando `icon`. Va **en el agregado** porque es el único sitio donde el tipo, el icono y la portada están juntos, y porque las otras dos caras de la regla —`create` y `quitarPortada`— viven al lado. **La comprobación mira el estado que queda, no el que había**: un upgrade con portada vacía el icono sin queja, y un upgrade viejo sin icono ni portada que envíe `icon: null` recibe `VAL-010` — que es exactamente lo que `requirements/pm.md` §5.2.9 acepta para lo ya registrado. Como toda excepción de `update`, se lanza antes de que nada se haya persistido, de modo que **no aplica ninguno** de los demás campos (`CA-PM-234`).
- **La portada no se corrige por aquí, y `coverImageUrl` en el cuerpo se rechaza con `400`**: `UpdateProductRequest` no tiene el campo, y `FAIL_ON_UNKNOWN_PROPERTIES` lo rechaza como a cualquier desconocido — ignorarlo haría creer que el cambio se aplicó (`CA-PM-033`). No gana un `VAL` propio como los inmutables (`VAL-006`) porque no es un campo del producto que alguien pueda creer corregible por JSON: es un archivo, y la ruta correcta está en la prosa de la `@Operation`. **La respuesta sí lo trae**: `ProductDetailResponse` gana `coverImageUrl` (`RF-PM-014` §4).
- **`links` se corrige EN BLOQUE, y es el primer campo de este módulo que lo hace** (22-09-2026, `RN-PM-048`; **era `videoUrl`, un `Patchable<String>`, hasta ese día**). `Patchable<List<ProductLinkRequest>>` en el DTO, con los **mismos tres estados** que el resto y **un significado por estado que aquí vale para el conjunto**: ausente **no toca nada**; nula **o vacía** —y la vacía va con la nula, como `""` iba con el nulo en el campo viejo— **quita todos**; y con entradas, **la colección que llega es la que queda**.
- **Por qué el conjunto entero y no un enlace cada vez.** La alternativa era un `PATCH` que corrigiera un tipo y dejara los demás, y **obliga a inventar cómo se dice «quita este»** — un tipo con dirección nula, o un verbo por caso, o tres rutas nuevas con sus tres permisos (`RN-SEG-015`), que es justo lo que `pm.md` §5.2.14 descartó. Con el conjunto entero no hay nada que inventar: la petición **ya enseña el estado final**, y compararlo con lo guardado son dos sentencias —borrar los tipos que no vienen, insertar o reescribir los que sí—. El coste es el de siempre en un reemplazo: **quien mande `links` con un solo enlace borra el otro sin haberlo nombrado**, y por eso `CA-PM-390` existe y la prosa de la `@Operation` lo dice con todas las letras.
- **Las comprobaciones son las mismas que en el alta y viven en el mismo sitio**: `ProductLink` valida su forma (`VAL-009`, `VAL-016` a `VAL-018`) y el caso de uso comprueba **el tipo repetido sobre el cuerpo y antes de escribir** (`VAL-015`). Ninguna se escribe dos veces: `RegisterProductService` y `UpdateProductService` llaman a lo mismo, porque una regla de forma duplicada es una que un día se corrige en un solo lado.
- **Y el diff de auditoría trata los enlaces como un valor** —`links` con su `before` y su `after`, el conjunto entero en cada lado— y no como tres campos sueltos. Enviar **el mismo conjunto que ya estaba no registra evento** (`CA-PM-226`), que es lo que `informaAlgo` ya hacía con el resto: una corrección que no cambia nada no es una corrección.
- **Se corrige en los dos tipos de producto**: no hay `verificarTipoEIcono` que lo acompañe, como tampoco lo había para el video.
- **`purchasePrice` se corrige y el nulo explícito SÍ lo vacía** (08-09-2026, con el nombre nuevo desde el 12-09-2026). Va con la descripción, el icono y la vigencia, **no** con el alcance y la implementación: la columna admite nulo, y ese nulo **significa** «no se conoce el costo». Es el campo donde se guarda **lo que NEXUS pagó** por el producto, y esta operación es hoy la única que lo escribe. `price`, en cambio, se suma a los campos cuyo nulo se rechaza — es `NOT NULL`, y dejarlo pasar produciría un fallo de integridad, un `500` donde corresponde un `400` que nombre el campo. **`publicPrice` es desde el 12-09-2026 una propiedad desconocida** y devuelve `400`.
- **El diff los lleva como cualquier otro campo**, con `before` y `after`, y por el motivo de siempre: el diff lo devuelve **quien aplica el cambio** y no el caso de uso comparando antes y después, de modo que un campo que no entre en el diff es un campo que no se audita — y eso se ve en la misma línea en que se asigna.

## 5. Orden de verificación

Es el contrato de esta operación, y por eso se escribe:

1. **Bloqueo** de la fila. Va primero: validar sobre una fila que otra transacción está corrigiendo produce decisiones tomadas sobre un estado que ya no existe.
2. El producto existe y **no está retirado** (`EX-001`).
3. Cada campo recibido, contra su regla.
4. Si llega nombre: unicidad **excluyendo al propio producto**.
5. Si llega **cualquiera de los dos precios** o la moneda: la moneda existe, está activa, y los decimales cuadran **con la moneda nueva**, no con la anterior.
6. Se aplica, y **solo si algo cambió** se emite el evento.

!!! danger "Con dos precios, el paso 5 deja de medir «lo que llega» y pasa a medir «lo que va a quedar»"

    Con un solo importe, «resolver la moneda final y medir el precio final» ya estaba escrito: cambiar **solo** la moneda obliga a revalidar el precio que nadie tocó. Con dos, ese mismo caso aparece **dos veces**, y el segundo es el que se olvida:

    | Llega | Qué hay que medir contra la moneda final |
    |---|---|
    | `price` | El `price` nuevo **y** el `purchasePrice` que ya estaba |
    | `purchasePrice` | El `purchasePrice` nuevo **y** el `price` que ya estaba |
    | Solo `currencyId` | **Los dos que ya estaban** |
    | `purchasePrice: null` | Solo el `price` que ya estaba: el otro **desaparece**, y un importe que no existe no tiene decimales que medir |

    El defecto que esto evita **no falla**: guarda en la fila un importe con más decimales de los que su moneda admite, y ese producto sale del catálogo con un precio que `RN-PM-007` prohíbe — descubierto meses después, al mirar por qué un total no cuadra.

    Y el rechazo **nombra el campo que no cabe** (`VAL-005` sobre `price` o sobre `purchasePrice`): con dos importes y un solo mensaje, quien lo recibe tiene que probar los dos para saber cuál corregir.

!!! warning "La unicidad del nombre se comprueba ANTES de tocar el agregado"

    Es un defecto que `RF-SP-004` ya pagó: con el nombre nuevo escrito en la entidad gestionada, el propio `SELECT` de la comprobación dispara el vaciado de Hibernate, la violación del índice llega antes que la comprobación y sale un `500` donde corresponde un `409`. Solo aparece con el dato ya duplicado, que es justo cuando importa.

## 6. Auditoría

Un evento `UPDATE` con **solo los campos que cambiaron**, cada uno con su valor anterior y el nuevo. `Product.update(...)` devuelve el diff en lugar de que el bot lo reconstruya comparando: quien aplica el cambio es quien sabe qué cambió.

**Sin evento cuando nada cambió** (`CA-PM-038`): un cambio que no cambió nada no es un cambio, y registrarlo llena la línea de tiempo de ruido que oculta lo que sí ocurrió.

## 7. Transaccionalidad

Una transacción, con bloqueo pesimista sobre la fila del producto. **Dos correcciones simultáneas se serializan**, y la segunda ve lo que la primera confirmó — que es lo que evita que quede una mezcla de las dos.

Es la misma corrección que `SP` aplicó el 26-08-2026 a las cuatro operaciones que cambiaban roles y membresía sin bloquear: no se repite aquí el defecto de validar contra un estado que otra transacción está a punto de cambiar.

## 8. Impacto sobre otros módulos

Consume el **catálogo de monedas** de `SP` cuando llega precio o moneda. Ninguno más: las membresías no se tocan, de modo que `RN-PM-017` no se vuelve a comprobar — no hay nada que pueda haberla roto desde el alta.

## 9. Alternativas consideradas

| Alternativa | Por qué se descartó |
|---|---|
| `Optional` en vez de `Patchable` | Ya falló en `RF-SP-027`, y en silencio |
| `PUT` con el recurso completo | Obligaría a reenviar tipo, código, origen y destino, que son inmutables, y a decidir qué hacer si llegan distintos |
| Ignorar los campos inmutables si llegan | Haría creer que el cambio se aplicó |
| Convertir el importe al cambiar de moneda | El sistema **no hace conversión de divisa**. Cambiar de moneda es declarar que ese número siempre estuvo en la otra, y así queda escrito en `spec.md` §13 |

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| 1 | La unicidad excluyendo al propio producto se olvida, y corregir la descripción sin tocar el nombre acaba rechazándose | Prueba dedicada: enviar el nombre actual no es un duplicado consigo mismo |
| 2 | Cambiar moneda y precio a la vez se valida contra la moneda anterior | El orden de §5 lo fija, y la prueba usa monedas de distinta escala |
| 3 | **Se valida el importe que llega y se deja pasar el otro.** Es el riesgo propio de tener dos precios, y no falla: guarda un importe con más decimales de los que su moneda admite | El paso 5 mide **los dos importes finales** contra la moneda final, y la prueba cambia **solo la moneda** con un precio de compra ya guardado que no cabe en la nueva (`CA-PM-157`) |
| 4 | **Vaciar el precio de compra se confunde con ponerlo a cero** en la implementación o en la prueba | Son dos criterios distintos y separados a propósito (`CA-PM-154` y `CA-PM-156`): uno deja la columna nula y el otro le escribe un cero, y en un informe de márgenes uno dice «no se conoce» y el otro «no costó nada» |

## 11. Estrategia de prueba

| Qué se prueba | Nivel | Cómo |
|---|---|---|
| Los diez criterios de `spec.md` §12 | API | |
| **Ausente ≠ vacío** | API | Vaciar la descripción la borra; el nombre vacío se rechaza; el campo ausente no se toca |
| Tipo, código, origen o destino en la petición | API | `400`, no se ignoran |
| Nombre igual al actual | API | No es duplicado consigo mismo |
| Moneda nueva con otra escala | API | **Los dos** precios se validan contra la **nueva**, y el caso central es cambiar **solo la moneda** con un precio de compra ya guardado |
| El precio de compra, en sus cuatro estados | API | Corregido, **vaciado con nulo**, puesto a **cero** y ausente. Los dos primeros son criterios distintos: uno deja el costo sin conocer y el otro declara que no costó nada |
| `price: null` | API | `400` con `VAL-004` sobre `price`, y **ningún** otro cambio aplicado |
| Número de sentencias | Integración | Bloqueo, unicidad **solo si el nombre cambió**, `UPDATE` y evento |
| Sin evento si nada cambió | Integración | `audit_change_log` no crece |
| Los enlaces, en bloque | API | **Ausente**, que no toca nada (`CA-PM-389`); **nula y vacía**, que los quitan todos (`CA-PM-226`); y **el reemplazo en sus tres caras** — el que se borra por no venir, el que se crea y el que se reescribe (`CA-PM-390`) |
| Las comprobaciones de los enlaces | API | Tipo repetido (`CA-PM-391`), tipo desconocido, dirección ausente e identificador fuera de forma (`CA-PM-392`), y **el identificador sobre una dirección con `?`** junto a la misma sin identificador (`CA-PM-393`). En todos, **ningún otro cambio de la misma petición se aplica** |
| El enlace del video | API | Corregido en un **bot** (`CA-PM-225`); vaciado con `null` y con `""` (`CA-PM-226`); y con forma inválida junto a un cambio de nombre válido, que **no se aplica** (`CA-PM-227`) |
| Dos correcciones simultáneas | Concurrencia | La última queda **entera**, no una mezcla |
