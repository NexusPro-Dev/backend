# SPEC — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 28-08-2026 — ver §15 |
| Enmendada el | 02-09-2026 — **un upgrade declara tambien su membresia de ORIGEN** (`RN-PM-002`, `RN-PM-017`, `RN-PM-018`). Ver el aviso de cabecera |
| Enmendada el | 07-09-2026 — **el alta declara el alcance y la implementación** (`RN-PM-019`, `RN-PM-020`), las dos obligatorias y en los dos tipos. Ver §15 |
| Enmendada el | 07-09-2026 — **el origen puede ser el destino: la renovación** (`RN-PM-017`). Ver §15 |
| Enmendada el | 07-09-2026 — **la membresía resuelta trae su color** (`RN-SP-024`). Ver §15 |
| Enmendada el | 08-09-2026 — **el alta admite un SEGUNDO precio, el público** (`RN-PM-023`), y **`RN-PM-006` deja de exigir «mayor que cero»**. Ver §15 |
| Enmendada el | 12-09-2026 — **el segundo precio pasa a ser el de COMPRA**: lo que NEXUS paga por el producto (`RN-PM-023`, `RN-PM-024`). `purchasePrice` sustituye a `publicPrice`. Ver §15 |
| Enmendada el | 14-09-2026 — **el alta devuelve `rating` **vacío** — `average` nulo y `count` cero — sin consulta** (`RN-PM-031`, `RF-PM-009`). Ver §15 |
| Enmendada el | 14-09-2026 — **el alta admite el ENLACE DE UN VIDEO** (`RN-PM-032`): opcional, en los dos tipos, validado solo en su forma. Ver §15 |
| Enmendada el | 14-09-2026 — **el icono pasa a ser OBLIGATORIO en un upgrade** (`RN-PM-034`, `RF-PM-014`): la portada llega después del alta, y sin ella el icono es lo único que puede pintar el producto. La respuesta gana `coverImageUrl`, siempre nulo aquí. Ver §15 |
| Enmendada el | 15-09-2026 — **el alcance pasa a cuatro valores explícitos**: `TIENDA`, `HOTLINK`, `AMBOS`, `NINGUNO` (`RN-PM-019` reescrita). `HOTLINKS` deja de admitirse. Ver §15 |
| Enmendada el | 22-09-2026 — **el alta declara los ENLACES del producto, y `links` sustituye a `videoUrl`** (`RN-PM-048`, `RN-PM-049`): uno por tipo, `VIDEO_PRESENTACION` y `CUPON_BOT`, cada uno con su dirección y un identificador externo opcional. Ver §15 |

!!! danger "Un upgrade dice ahora DE DONDE sale, y eso cambia quien puede comprarlo"

    Hasta el 02-09-2026 un upgrade solo declaraba **a donde lleva**, y quien podia comprarlo **se deducia**: cualquiera por debajo de ese nivel. Por decision del responsable del proyecto, ahora declara tambien **de que membresia sale**.

    **Lo que eso compra es el salto.** `BECA → ORO` no se podia expresar: la deduccion ofrecia «subir a ORO» a todo el mundo por debajo, al mismo precio, sin distinguir a quien sube tres escalones de quien sube uno. Ahora **cada salto es un producto** y cada uno tiene su precio.

    **El origen es obligatorio**, y la consecuencia se acepta entera: si nadie declara un upgrade desde `VIP`, quien este en `VIP` **no vera ninguna subida** — sin error y sin aviso. Es el precio de que la oferta sea explicita en lugar de calculada (`pm.md` §5.2.1).


---

## 1. Objetivo

Poner en el sistema algo que se puede vender, con su precio, para que exista un objeto al que una venta pueda referirse.

## 2. Contexto

Hoy la plataforma no tiene **nada que vender**. La membresía de una persona solo cambia porque un administrador se la asigna (`RF-SP-032`), y no existe ningún sitio donde diga cuánto cuesta subir de nivel ni qué bots se ofrecen. Este es el primer requerimiento del módulo y el que crea el objeto del que dependerán después la compra, el cobro y las comisiones.

**Los dos tipos no se mezclan.** Un producto de **upgrade de membresía** da derecho a pasar al nivel que declara; un producto de **bot del sistema** da derecho a una prestación y no toca el nivel de acceso de nadie. La diferencia no es una etiqueta: decide qué datos son obligatorios y qué se adquiere al comprarlo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Define el producto: su tipo, su nombre, su precio y —si es un upgrade— la membresía a la que lleva |

## 4. Alcance

### 4.1 Incluye

- Registrar un producto de tipo **upgrade de membresía**, declarando **de qué membresía sale y a cuál lleva**.
- Registrar un producto de tipo **bot del sistema**, sin ninguna de las dos.
- Verificar que **las dos membresías existen y que el origen está por debajo del destino**, que la moneda existe y está activa, y que ni el nombre ni el código chocan con los de otro producto.
- Dejar constancia del alta en la auditoría de cambios.
- **Registrarlo `INACTIVO`**: existe, y no se ofrece hasta que alguien lo publique con `RF-PM-005`.

### 4.2 No incluye

- **Comprar el producto ni cobrarlo.** No hay orden, ni pago, ni pasarela (`requirements/pm.md` §1.4).
- **Aplicar el upgrade sobre una persona.** Cambiar el nivel de alguien es escribir en la membresía del usuario, que pertenece a `SP` (`RF-SP-032`).
- **Corregir un producto ya registrado**, que es `RF-PM-004`, ni activarlo o desactivarlo, que es `RF-PM-005`.
- **Definir qué incluye el bot que se vende.** Este módulo vende el derecho; el contenido pertenece a Academia o a Señales.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-002` | Destino obligatorio en el upgrade, prohibido en el bot | `requirements/pm.md` §5.1 |
| `RN-PM-003` | Origen y destino son membresías reales de la cadena | `requirements/pm.md` §5.1 |
| `RN-PM-017` | **El origen no está por encima del destino**; el mismo **sí** se admite | `requirements/pm.md` §5.1 |
| `RN-PM-018` | **Se admite saltar niveles** | `requirements/pm.md` §5.1 |
| `RN-PM-005` | Nombre único entre los vivos | `requirements/pm.md` §5.1 |
| `RN-PM-006` | **Ningún precio es negativo**, y el cero se admite | `requirements/pm.md` §5.1 |
| `RN-PM-007` | **Los dos precios respetan** los decimales de su moneda | `requirements/pm.md` §5.1 |
| `RN-PM-023` | **El precio de compra es opcional y no se cobra** — es lo que NEXUS paga por el producto | `requirements/pm.md` §5.1 |
| `RN-PM-024` | **El precio de compra no sale de administración** — el alta lo devuelve porque exige `products:create` | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |
| `RN-PM-012` | El producto nace inactivo | `requirements/pm.md` §5.1 |
| `RN-PM-013` | El código no se libera nunca | `requirements/pm.md` §5.1 |
| `RN-PM-015` | La vigencia se mide en días y es opcional | `requirements/pm.md` §5.1 |
| `RN-PM-016` | El icono solo existe en el upgrade — **enmendada el 14-09-2026**: en el upgrade es obligatorio mientras no haya portada | `requirements/pm.md` §5.1 |
| `RN-PM-034` | **Un upgrade siempre tiene con qué pintarse: portada o icono** — en el alta no puede haber portada, de modo que **el icono es obligatorio en un upgrade** | `requirements/pm.md` §5.1 |
| `RN-PM-033` | **La portada es un archivo y se publica por su identificador** — aquí solo que la respuesta trae `coverImageUrl`, nulo: la portada se sube después (`RF-PM-014`) | `requirements/pm.md` §5.1 |
| `RN-PM-019` | **El alcance dice hasta dónde se muestra, y es acumulativo** | `requirements/pm.md` §5.1 |
| `RN-PM-020` | **La implementación dice si lo comprado se aplica solo o espera autorización** | `requirements/pm.md` §5.1 |
| `RN-PM-032` | **Un producto puede enlazar un video, y el enlace sale en toda lectura** — aquí, que se admite en el alta, en los dos tipos, y que se valida **solo la forma**. **Desde el 22-09-2026 no es un campo propio**: es el enlace de tipo `VIDEO_PRESENTACION` dentro de `links` | `requirements/pm.md` §5.1 |
| `RN-PM-048` | **Los enlaces viven aparte, y hay uno por tipo** — aquí, que el alta los declara en una colección, que los tipos son dos, que **repetir un tipo o usar uno desconocido se rechaza** y que la colección puede venir ausente o vacía | `requirements/pm.md` §5.1 |
| `RN-PM-049` | **El identificador se pega al final del enlace, y administración ve el crudo** — aquí, que el alta **devuelve lo guardado sin componer** (quien registra tiene `products:read`) y que **con identificador la dirección no admite `?` ni `#`** | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Referencia corta y estable del producto | Letras mayúsculas, dígitos y guion bajo, empezando por letra. **Único para siempre**, incluso frente a los eliminados, e **inmutable** (`RN-PM-013`) |
| Tipo | Sí | Upgrade de membresía o bot del sistema | Uno de los dos, y **no se podrá cambiar después** (`RN-PM-001`) |
| Nombre | Sí | Cómo se llama el producto de cara a quien lo compra | Único entre los productos vivos, sin distinguir mayúsculas ni acentos (`RN-PM-005`) |
| Descripción | No | Qué se lleva quien lo compra | Con longitud acotada. Opcional al registrar; **sin ella el producto no podrá publicarse** (`RN-PM-014`) |
| Membresía **de origen** | **Depende del tipo** | Nivel desde el que se compra el upgrade | **Obligatoria** si el tipo es upgrade, **prohibida** si es bot (`RN-PM-002`). Debe existir (`RN-PM-003`) y **no estar por encima** del destino (`RN-PM-017`) — **puede ser la misma**, y entonces el producto es una renovación |
| Membresía **destino** | **Depende del tipo** | Nivel al que lleva el upgrade | Mismas condiciones. **No tiene por qué ser el inmediatamente superior al origen** (`RN-PM-018`) |
| Icono | **Depende del tipo** | **Nombre** del icono con el que el frontend pinta el producto, no una imagen | Minúsculas, dígitos y guion medio, empezando por letra, hasta 50 caracteres. **Obligatorio en el upgrade y prohibido en el bot** (`RN-PM-016`, `RN-PM-034`). **Decía «opcional incluso ahí» hasta el 14-09-2026**: desde que existe la portada, un upgrade sin portada —y en el alta ninguno la tiene— necesita el icono para pintarse |
| Enlaces | No | **Las direcciones** que el producto declara, cada una con su tipo. **Sustituye al «enlace del video» el 22-09-2026** | Una colección de hasta **un enlace por tipo** (`RN-PM-048`), y los tipos son `VIDEO_PRESENTACION` —el video que lo presenta (`RN-PM-032`)— y `CUPON_BOT` —dónde registra su cuenta quien ya compró—. **Ausente o vacía significan lo mismo**: no declara ninguno. **En los dos tipos de producto**, sin la condición cruzada del icono. **El sistema no sigue ningún enlace**: comprueba la forma y nada más |
| ├ Tipo del enlace | Sí, en cada uno | Cuál de los dos es, y con él **dónde se publica** | `VIDEO_PRESENTACION` o `CUPON_BOT`. **Un tipo desconocido se rechaza** y **un tipo repetido en la misma petición también** (`RN-PM-048`): la clave es la pareja producto-tipo |
| ├ Dirección | Sí, en cada uno | Adónde lleva el enlace | URL **absoluta** `http` o `https`, **sin espacios**, hasta 500 caracteres, de cualquier dominio. **Obligatoria**: no hay enlace sin enlace, de modo que quitar uno es **no declararlo**, y no declararlo vacío |
| └ Identificador externo | No | Un código **de un sistema ajeno** —quien aloja el bot o el video— que NEXUS guarda y devuelve **sin interpretar** | Hasta 100 caracteres, **sin espacios** y sin cadena vacía. **Se pega al final de la dirección** al publicarla (`RN-PM-049`), de modo que **si se declara, la dirección no puede llevar `?` ni `#`** |
| Precio **del sistema** | Sí | Cuánto cuesta, y **lo que se cobra** | **No negativo** (`RN-PM-006`) —el cero se admite desde el 08-09-2026, porque una renovación de una membresía gratuita vale eso—, con los decimales que admita su moneda (`RN-PM-007`) |
| Precio **de compra** | **No** | Lo que NEXUS paga por el producto cuando tiene que comprarlo; ahí se guarda lo que costó | Mismas condiciones que el anterior y **en la misma moneda** (`RN-PM-007`). **Ausente o nulo significan lo mismo**: no se conoce todavía —el producto se registra antes de comprarse— (`RN-PM-023`). **No se cobra**, ningún cálculo lo lee y **no sale de administración** (`RN-PM-024`). **Decía «precio público» hasta el 12-09-2026** |
| Moneda | Sí | En qué moneda se expresan **los dos** precios | Debe existir y estar **activa** (`RN-PM-008`). **No hay una segunda moneda para el precio de compra** |
| Vigencia | No | Cuántos días dura lo que el producto otorga, contados desde la compra | Entero mayor que cero. **Sin ella, lo adquirido no caduca** (`RN-PM-015`) |
| Alcance | **Sí** | En qué vistas de venta se ofrece el producto | `TIENDA`, `HOTLINK`, `AMBOS` o `NINGUNO`, **en los dos tipos y sin valor por omisión** (`RN-PM-019`, reescrita el 15-09-2026: dejó de ser una escala). **`HOTLINKS` se rechaza** desde ese día como cualquier valor fuera del dominio (`400`, `CA-PM-112`) |
| Implementación | **Sí** | Si lo comprado se aplica solo o espera a que alguien lo autorice | `AUTOMATICA` o `MANUAL`, **en los dos tipos y sin valor por omisión** (`RN-PM-020`) |

### 6.2 Salida

**Y desde el 14-09-2026 lleva el enlace del video** (`RN-PM-032`), que **desde el 22-09-2026 viaja en `links`** y no en un campo propio (`RN-PM-048`). La colección llega **siempre presente** y **vacía** cuando no se declaró ninguno — y aquí se aparta del precio de compra a propósito: aquel usa el nulo para decir «no se conoce», y una colección vacía ya dice «no hay» sin necesidad de inventar una entrada. Cada enlace lleva su **tipo**, su **dirección tal cual se guardó** —recortada, sin normalizar nada más— y su **identificador externo**, nulo cuando no lo tiene. **Se devuelven crudos y sin componer** (`RN-PM-049`): quien registra el producto es quien va a corregirlo, y tiene que recibir lo que luego mandará en el `PATCH`.

**Y `coverImageUrl`, que en el alta es siempre nulo y presente** (`RN-PM-033`): la portada no entra por aquí —el alta sigue siendo JSON— y se sube después con `RF-PM-014`. El campo va igualmente, para que la respuesta del alta tenga **la misma forma** que el detalle y el cliente no distinga dos productos.

**Desde el 14-09-2026 la respuesta lleva `rating`** (`RN-PM-031`): el alta devuelve `rating` **vacío** — `average` nulo y `count` cero — sin consulta. Es un objeto **presente siempre**, con `average` —dos decimales, **nulo** cuando no hay reseñas— y `count` —**cero** cuando no hay—. Cuentan solo las reseñas **vivas**: una retirada sale de la cuenta en el acto. La enmienda la construye `RF-PM-009` (`T-10`, `T-11`), y lo que este requerimiento tiene que conservar es su número de sentencias: el agregado viaja **en la misma consulta** que el producto.

| Dato | Descripción |
|---|---|
| Producto | El producto registrado, con su identificador, su código, su tipo, **sus dos precios** tal como quedaron almacenados y su estado, que es siempre `INACTIVO`. El público llega **presente y nulo** cuando no se declaró: un campo que falta es indistinguible de uno que el cliente no conoce |
| Membresías resueltas | Cuando es un upgrade: el código, el nombre y el nivel **de las dos**, y no solo sus identificadores |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de productos.
- Si el producto es un upgrade, la cadena de membresías tiene al menos un eslabón: sin niveles no hay a dónde subir.
- Existe al menos una moneda activa.

**Postcondiciones**

- El producto queda registrado **`INACTIVO`**, con su tipo y su código fijados para siempre.
- La auditoría de cambios contiene un evento de creación con el estado inicial completo del producto.
- **Ningún producto queda a la venta por haberse registrado.** Ponerlo a la venta es `RF-PM-005`, y es allí donde se comprueba que ningún otro upgrade activo apunta a su destino (`RN-PM-004`).

## 8. Flujo principal

1. El actor envía el código, el tipo, el nombre, el precio, la moneda y —si es un upgrade— **las dos membresías, la de origen y la de destino**.
2. El sistema comprueba que los datos obligatorios de **ese tipo** están presentes y que no llegan los que ese tipo prohíbe.
3. El sistema comprueba que la moneda existe y está activa, y que **cada precio informado** —el del sistema, siempre; el público, si llega— **no es negativo** y no tiene más decimales que los que esa moneda admite.
4. Si es un upgrade, el sistema comprueba que **las dos membresías existen** y que **el origen no está por encima del destino** (`RN-PM-017`). **Que sean la misma se admite**: es una renovación.
5. El sistema comprueba que el código no lo ha tenido nunca otro producto, y que el nombre no lo tiene ya otro producto vivo.
6. El sistema registra el producto **inactivo** y emite el evento de auditoría de creación.
7. El sistema devuelve el producto registrado.

## 9. Flujos alternativos

### FA-001 — Producto de bot

**Cuándo ocurre:** el tipo es bot del sistema.

1. El sistema **exige que no llegue** membresía destino.
2. Se omite el paso 4 del flujo principal: no hay destino que validar.
3. El resto del flujo es idéntico.

### FA-002 — Primer producto del sistema

**Cuándo ocurre:** no hay ningún producto registrado.

1. Ninguna comprobación de unicidad tiene con qué chocar.
2. El producto queda registrado con normalidad. **No es un caso especial**, y se enumera para que quede escrito que no lo es.

## 10. Excepciones

### EX-001 — Nombre ya en uso

**Condición:** ya existe un producto vivo con ese nombre, comparado sin distinguir mayúsculas ni acentos.
**Respuesta del sistema:** rechaza el alta indicando **qué campo** está duplicado, y no registra nada.

### EX-002 — Membresía destino inexistente

**Condición:** el tipo es upgrade y la membresía indicada no existe.
**Respuesta del sistema:** rechaza el alta diciendo que la membresía indicada no existe. **No es un «no encontrado»**: lo que no existe es un dato que el actor envió, no el recurso que estaba pidiendo.

### EX-003 — Moneda inexistente o inactiva

**Condición:** la moneda no existe, o existe y está desactivada.
**Respuesta del sistema:** rechaza el alta. El mensaje distingue los dos casos: una moneda que no existe es un dato equivocado; una desactivada es una decisión del sistema que el actor no puede saltarse.

### ~~EX-004 — Ya hay un upgrade activo hacia ese destino~~

**Retirada el 26-08-2026 al aprobar la spec.** El producto nace inactivo (`RN-PM-012`), de modo que registrarlo no puede chocar con ningún upgrade activo. La excepción **existe, pero en `RF-PM-005`**, que es donde el producto se pone a la venta. El identificador no se reutiliza para otra cosa.

### EX-005 — Código ya usado

**Condición:** otro producto lleva ese código, esté vivo o eliminado.
**Respuesta del sistema:** rechaza el alta señalando el código. **No se libera al eliminar**, al revés que el nombre: el código es la referencia desde la que una factura dirá qué se vendió, y reutilizarlo haría que dos facturas de años distintos apuntaran a cosas distintas con la misma palabra.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Tipo obligatorio y dentro del dominio | El tipo de producto es obligatorio y debe ser uno de los admitidos. |
| `VAL-002` | Nombre obligatorio | El nombre del producto es obligatorio. |
| `VAL-003` | Longitud del nombre y de la descripción | El nombre no puede exceder la longitud admitida. |
| `VAL-004` | Precio del sistema obligatorio, y **ningún precio negativo** | El precio no puede ser negativo. **El campo del error dice cuál de los dos** —`price` o `purchasePrice`—, porque un mensaje que no lo distinga obliga a probar los dos |
| `VAL-005` | Decimales **de cada precio** según su moneda | El precio no admite más decimales que los de su moneda. **Con el campo que lo incumple**, por lo mismo |
| `VAL-006` | Moneda obligatoria | La moneda es obligatoria. |
| `VAL-007` | **Origen y destino** obligatorios en el upgrade | Un producto de upgrade debe declarar su membresía de origen y su membresía destino. |
| `VAL-014` | **El origen no está por encima del destino** | Un upgrade no puede bajar de nivel: la membresía de origen no puede estar por encima de la de destino. |
| `VAL-008` | Destino prohibido en el bot | Un producto de bot no puede declarar membresía destino. |
| `VAL-009` | Código obligatorio | El código del producto es obligatorio. |
| `VAL-010` | Formato del código | El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra. |
| `VAL-011` | Vigencia mayor que cero | La vigencia debe ser un número de días mayor que cero. |
| `VAL-012` | Formato del icono | El icono solo admite minúsculas, dígitos y guion medio, debe empezar por letra y no puede exceder 50 caracteres. |
| `VAL-013` | Icono prohibido en el bot | Un producto de tipo bot no puede declarar icono. |
| `VAL-015` | Alcance obligatorio y dentro del dominio | El alcance del producto es obligatorio y debe ser uno de los admitidos. |
| `VAL-016` | Implementación obligatoria y dentro del dominio | La implementación del producto es obligatoria y debe ser una de las admitidas. |
| `VAL-017` | Formato de la dirección de un enlace. **Decía «del enlace del video» hasta el 22-09-2026**; vale igual para los dos tipos, y el error **nombra el índice y el tipo** del enlace que lo incumple | La dirección del enlace debe ser una dirección absoluta http o https, sin espacios y de hasta 500 caracteres. |
| `VAL-018` | **Icono obligatorio en el upgrade** | Un producto de upgrade debe declarar su icono mientras no tenga portada. |
| `VAL-019` | **Tipo de enlace obligatorio y dentro del dominio** (22-09-2026) | El tipo del enlace es obligatorio y debe ser uno de los admitidos. |
| `VAL-020` | **Tipo de enlace repetido** (22-09-2026). Se comprueba **sobre el cuerpo recibido y antes de escribir**, para que el choque no lo dé la clave primaria con un `500` | Un producto no puede declarar dos enlaces del mismo tipo. |
| `VAL-021` | **Dirección del enlace obligatoria** (22-09-2026). No hay enlace sin enlace: la ausencia de un tipo es lo que significa «no tiene» | La dirección del enlace es obligatoria. |
| `VAL-022` | **Formato del identificador externo** (22-09-2026) | El identificador externo no admite espacios ni puede exceder 100 caracteres. |
| `VAL-023` | **Identificador externo sobre una dirección con cadena de consulta** (22-09-2026). El identificador se pega como **último segmento de ruta**, y detrás de un `?` o un `#` daría un enlace roto que responde `200` | Un enlace con identificador externo no admite una dirección con `?` ni `#`. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-001` | El sistema registra un producto de upgrade con su membresía destino y devuelve el destino resuelto con su nivel |
| `CA-PM-002` | El sistema registra un producto de bot sin membresía destino |
| `CA-PM-003` | El sistema rechaza un upgrade **sin** membresía destino |
| `CA-PM-004` | El sistema rechaza un bot **con** membresía destino |
| `CA-PM-005` | El sistema rechaza un precio **negativo**. **Reescrito el 08-09-2026**: decía «de cero o negativo», y el cero pasó a admitirse (`RN-PM-006`) |
| `CA-PM-006` | El sistema rechaza un precio con más decimales de los que admite su moneda, y acepta el mismo importe con los decimales correctos |
| `CA-PM-007` | El sistema rechaza una moneda inactiva, y lo distingue de una moneda inexistente |
| `CA-PM-008` | El sistema rechaza un nombre que solo difiere de otro existente en mayúsculas o acentos |
| `CA-PM-096` | El sistema registra un upgrade con su icono, **normalizado a minúsculas** |
| `CA-PM-097` | El sistema rechaza un producto de tipo bot **con** icono |
| ~~`CA-PM-098`~~ | ~~El sistema registra un upgrade **sin** icono, que llega nulo y presente~~ **Invertido el 14-09-2026** por `RN-PM-034`: ver `CA-PM-230`. La forma «nulo y presente» del icono sigue viva en el bot (`CA-PM-231`) |
| ~~`CA-PM-009`~~ | **Retirado el 26-08-2026**: el producto nace inactivo, de modo que el alta no puede chocar con un upgrade activo. El criterio vive en `RF-PM-005` con número propio. El identificador no se reutiliza |
| `CA-PM-010` | El sistema rechaza un upgrade cuya membresía destino no existe, y lo hace como dato inválido y no como recurso no encontrado |
| `CA-PM-011` | El sistema registra en la auditoría de cambios un evento de creación con el estado inicial completo del producto |
| `CA-PM-012` | El sistema rechaza el alta a un actor sin el permiso de creación de productos, y no registra nada |
| `CA-PM-068` | El sistema registra todo producto **`INACTIVO`**, sea cual sea el tipo, y enviar un estado en la petición devuelve `400` en lugar de ignorarse |
| `CA-PM-069` | El sistema rechaza un código que ya usó otro producto, **incluido uno eliminado**, y lo distingue del nombre duplicado |
| `CA-PM-070` | El sistema rechaza un código que no cumple el formato de mayúsculas, dígitos y guion bajo |
| `CA-PM-071` | El sistema **admite registrar sin descripción**, y el producto queda inactivo a la espera de que `RF-PM-005` la exija para publicarlo |
| `CA-PM-092` | El sistema **admite registrar sin vigencia**, y ese producto otorga su derecho sin caducidad |
| `CA-PM-093` | El sistema rechaza una vigencia de cero, negativa o no entera, en cualquiera de los dos tipos |
| `CA-PM-101` | El sistema registra un upgrade **con su origen y su destino**, y la respuesta resuelve **las dos** membresías |
| `CA-PM-102` | El sistema registra un **salto**: `BECA → ORO` con dos niveles de por medio, sin exigir que sean contiguos |
| `CA-PM-103` | El sistema rechaza un upgrade **sin origen**, y otro **sin destino** |
| `CA-PM-104` | El sistema rechaza un upgrade cuyo origen está **por encima** del destino — un descenso vendido como upgrade |
| `CA-PM-125` | El sistema **admite** un upgrade cuyo origen **es** el destino: es una **renovación**, y lo que vende es tiempo y no nivel |
| `CA-PM-141` | El sistema devuelve el **color** de cada membresía resuelta, junto a su código, su nombre y su nivel |
| `CA-PM-105` | El sistema rechaza un **bot** que declare cualquiera de las dos membresías |
| `CA-PM-110` | El sistema **rechaza un alta sin alcance**, en los dos tipos, y el rechazo nombra el campo |
| `CA-PM-111` | El sistema **rechaza un alta sin implementación**, en los dos tipos, y el rechazo nombra el campo |
| `CA-PM-112` | El sistema rechaza un valor **fuera del dominio** en cualquiera de las dos, y no lo interpreta como ausente |
| `CA-PM-113` | El sistema registra un **bot** con alcance `AMBOS` (`HOTLINKS` hasta el 15-09-2026) e implementación `MANUAL` sin queja: ninguna de las dos depende del tipo |
| `CA-PM-114` | La respuesta del alta devuelve las dos, y el **evento de creación las incluye en la instantánea** |
| `CA-PM-145` | El sistema registra un producto **con los dos precios** y la respuesta devuelve los dos, cada uno con los decimales de la moneda |
| `CA-PM-146` | El sistema registra un producto **sin precio de compra**, y `purchasePrice` llega **presente y nulo** — no ausente, y no cero |
| `CA-PM-147` | El sistema rechaza un **precio de compra negativo**, y el error **nombra `purchasePrice`** y no `price` |
| `CA-PM-148` | El sistema rechaza un **precio de compra con más decimales** de los que admite la moneda, aunque el del sistema sí quepa |
| `CA-PM-149` | El sistema **admite un precio de cero** en los dos importes: es lo que hace registrable una renovación de una membresía gratuita |
| `CA-PM-150` | La **instantánea del evento de creación incluye `purchase_price`**, y lo distingue del ausente escribiéndolo nulo |
| `CA-PM-219` | El sistema registra un producto **con un enlace de tipo `VIDEO_PRESENTACION`** —también uno de tipo **bot**, al revés que el icono— y la respuesta lo devuelve **tal cual**, recortado y sin normalizar, dentro de `links`. **Reescrito el 22-09-2026**: hasta ese día el enlace era el campo `videoUrl` |
| `CA-PM-220` | El sistema registra un producto **sin ningún enlace**, y `links` llega **presente y vacía** — no ausente, y no nula. **Reescrito el 22-09-2026**: hasta ese día se comprobaba que `videoUrl` llegara presente y nulo |
| `CA-PM-221` | El sistema rechaza una dirección **que no tiene forma de URL absoluta `http` o `https`** —relativa, sin esquema, con otro esquema, con espacios, o de más de 500 caracteres— con `VAL-017`, y el error **nombra el enlace que lo incumple por su índice y su tipo**. **Reescrito el 22-09-2026**: hasta ese día nombraba `videoUrl` |
| `CA-PM-222` | La **instantánea del evento de creación incluye los enlaces**, y **no incluye una entrada** por el tipo que no se declaró. **Reescrito el 22-09-2026**: hasta ese día incluía `video_url` nulo |
| `CA-PM-230` | El sistema **rechaza un upgrade sin icono** —ausente, nulo o vacío— con `VAL-018` nombrando `icon`, y no registra nada |
| `CA-PM-231` | El sistema registra un **bot sin icono**, que llega nulo y presente; y la respuesta del alta trae **`coverImageUrl` presente y nulo** en los dos tipos, y la instantánea `cover_image_id` nulo |
| `CA-PM-380` | El sistema registra un producto **con los dos enlaces a la vez** —`VIDEO_PRESENTACION` y `CUPON_BOT`— y la respuesta devuelve **los dos**, cada uno con su tipo, su dirección y su identificador externo |
| `CA-PM-381` | El sistema registra un enlace **con identificador externo** y la respuesta lo devuelve **crudo y sin componer**: la dirección tal cual y el identificador en su campo, **no pegados**. Es lo que `RF-PM-004` espera recibir de vuelta |
| `CA-PM-382` | El sistema **rechaza dos enlaces del mismo tipo** en la misma petición con `VAL-020`, nombrando el tipo repetido, y **no registra nada** — ni el producto ni el primer enlace |
| `CA-PM-383` | El sistema **rechaza un tipo de enlace desconocido** con `VAL-019`, y **un enlace sin dirección** —ausente, nulo o vacío— con `VAL-021` |
| `CA-PM-384` | El sistema **rechaza un identificador externo con espacios o de más de 100 caracteres** con `VAL-022`, y **rechaza un identificador sobre una dirección que lleva `?` o `#`** con `VAL-023`; la misma dirección **sin** identificador se admite |
| `CA-PM-348` | El sistema registra un producto con **cada uno de los cuatro alcances** —`TIENDA`, `HOTLINK`, `AMBOS`, `NINGUNO`— y rechaza **`HOTLINKS`** con `400`, como cualquier valor fuera del dominio (`CA-PM-112`) |

## 13. Casos límite

- **Nombre con espacios al inicio o al final:** se recortan antes de comparar la unicidad. Sin ese recorte, un espacio burlaría la regla y el catálogo mostraría dos productos que se leen igual.
- **Precio con muchos decimales sobre una moneda sin fracción:** el rechazo debe existir aunque la moneda por defecto tenga dos decimales; el caso se prueba con una moneda de cero. **Vale igual para el precio de compra**, que se mide contra la misma moneda.
- **Precio de compra idéntico al del sistema:** se admite y **no se normaliza a nulo**. Es un producto que se revende sin margen, y convertirlo en nulo por parecerse borraría un costo que sí se conoce.
- **Precio de compra mayor que el del sistema:** se admite. **Ninguna regla compara los dos importes** (`requirements/pm.md` §5.2.6): vender por debajo del costo es una decisión comercial, y el sistema la registra en vez de impedirla.
- **Precio del sistema en cero con precio de compra informado:** se admite. Es el producto que costó algo y se concede gratis — y es el caso que obliga a `CM` a dejar de dividir a ciegas (`RN-CM-019`).
- **Precio de compra en cero:** se admite y **no es lo mismo que nulo**. Cero dice «no costó nada»; nulo dice «no se conoce».
- **Dirección de un enlace con espacios alrededor:** se recortan, como el nombre; **`" "` es una dirección ausente** —y por tanto `VAL-021`—, no una con forma inválida. **Un enlace con forma y que no lleva a ningún sitio se admite**: el sistema no lo sigue, y esto no es un descuido sino la decisión de `pm.md` §5.2.8.
- **Colección de enlaces vacía frente a ausente (22-09-2026):** significan lo mismo en el **alta** —no declara ninguno— y **no** en la corrección, donde `RF-PM-004` las separa: allí ausente no toca nada y vacía los quita todos. Que aquí coincidan no es casualidad: un producto que nace no tiene enlaces que conservar.
- **Dirección terminada en `/` con identificador externo:** se admite, y la barra **no se duplica** al componer (`RN-PM-049`). El caso se prueba en las lecturas que publican el enlace resuelto, no aquí: el alta lo guarda tal cual.
- **Identificador externo sobre una dirección con `?` o `#`:** se rechaza (`VAL-023`), y **la misma dirección sin identificador se admite**. Es una restricción **cruzada** y no una prohibición sobre la dirección — la diferencia importa, porque un producto puede querer enlazar un video de YouTube, que lleva `?v=`, mientras no le pegue nada detrás.
- **Dos altas simultáneas del mismo upgrade:** dos administradores registran a la vez un upgrade hacia el mismo destino. Una debe quedar y la otra ser rechazada; que las dos queden activas es el desenlace que `RN-PM-004` existe para impedir.
- **Dos altas simultáneas con el mismo nombre:** mismo caso sobre la unicidad de nombre.
- **La membresía destino se elimina mientras se registra:** las membresías no se eliminan (`RN-SP-008`), de modo que este caso no existe. Se escribe para que nadie lo busque.
- **Producto de bot con nombre de un upgrade retirado:** el nombre de un producto eliminado **queda libre**, porque la unicidad es entre los vivos.

## 14. Preguntas abiertas

Ninguna. Las cinco se resolvieron el 26-08-2026, antes de aprobar la especificación. Se conservan con su resolución y su motivo, porque el motivo es lo que impide volver a abrirlas por olvido.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El producto nace `ACTIVO` o `INACTIVO`? | **`INACTIVO`** (`RN-PM-012`). El motivo no es la prudencia sino dónde vive `RN-PM-004`: naciendo activo, «un solo upgrade activo por destino» habría que comprobarla **en dos sitios** —el alta y la activación—, y dos copias de una regla acaban divergiendo; la que se quedara atrás no fallaría, **admitiría**. Naciendo inactivo, la comprobación vive solo en `RF-PM-005`. Se acepta el coste declarado: quien registre un producto y no vuelva a entrar creerá que lo publicó, y lo único que lo acota es que el catálogo muestre el estado. **Se descartó por ahora el tercer valor `BORRADOR`**: la distinción entre «nunca publicado» y «retirado de la venta» es fina, no urge, y añadirla después es una migración sobre un `varchar` con `CHECK` |
| 2 | ¿El producto lleva un código corto y estable además del nombre? | **Sí** (`RN-PM-013`), con el formato de `roles` y `memberships`, **inmutable** y **único incluso frente a los eliminados** — al revés que el nombre, que sí se libera al retirar un producto. La asimetría es deliberada: el nombre es una etiqueta comercial y `RF-PM-004` lo deja corregir, de modo que no puede ser la referencia desde la que una factura o una comisión digan qué se vendió. El día que una factura diga `UPGRADE_ORO`, esa palabra tiene que resolver a un solo producto para siempre |
| 3 | ¿La descripción es obligatoria? | **Opcional al registrar, obligatoria para publicar** (`RN-PM-014`). Exigirla en el alta la llena de ruido, porque en ese momento el producto se está preparando; no exigirla nunca deja publicar algo que el cliente no entiende. La regla se apoya en la resolución 1: si el producto nace inactivo, hay un momento posterior donde exigirla, y ese momento es `RF-PM-005` |
| 4 | ¿Puede el mismo destino tener un upgrade activo y varios inactivos? | **Sí.** Es lo que `RN-PM-004` ya dice al acotar **solo los activos**, y lo que permite preparar el precio nuevo antes de retirar el viejo. El coste declarado es que el catálogo se llena de borradores olvidados, y lo acota el filtro por estado de `RF-PM-002` |
| 5 | ¿El alta emite evento de seguridad además del de cambios? | **No.** Un producto no concede privilegios sobre el sistema, y el catálogo de `security.md` §8.1 es cerrado: es la misma postura que `RF-SP-016` tomó con las membresías. La pregunta que motivaba la duda —quién puso este precio— **ya la responde la auditoría de cambios**, que registra la creación con el estado inicial completo, precio incluido |

---

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.2.0 | 26-08-2026 | **Aprobada.** Las cinco preguntas abiertas se resuelven y la spec cruza su primera compuerta. Entran tres reglas nuevas al documento del módulo: `RN-PM-012` —el producto nace inactivo—, `RN-PM-013` —el código no se libera nunca— y `RN-PM-014` —no se publica lo que no se explica—. El **código** aparece en §6.1 y trae `VAL-009`, `VAL-010` y `EX-005`. Se **retira `EX-004`** y con él `CA-PM-009`: registrar ya no puede chocar con un upgrade activo, porque el producto nace inactivo, y esa excepción pasa a `RF-PM-005` con identificadores propios — los retirados no se reutilizan. Cuatro criterios nuevos, `CA-PM-068` a `CA-PM-071`. | Responsable del proyecto |
| 0.1.0 | 26-08-2026 | Redacción inicial, con cinco preguntas abiertas. | Responsable técnico |
| 0.3.0 | 27-08-2026 | **El producto gana vigencia de adquisición** (`RN-PM-015`), medida en días y **opcional**: sin ella, lo adquirido no caduca. Entra como dato de entrada, sale en la respuesta, y trae `VAL-011`, `CA-PM-092` y `CA-PM-093`. La spec vuelve a su compuerta y se reaprueba el mismo día (Art. I.7); no había código escrito, de modo que el cambio costó una edición y no una migración. | Responsable del proyecto |
| 0.4.0 | 28-08-2026 | **El tipo `SERVICIO` pasa a llamarse `BOT`, y el upgrade gana icono** (`RN-PM-016`), por decisión del responsable del proyecto. El renombrado **no cambia la semántica** del tipo —sigue siendo el producto que no toca el nivel de acceso de nadie— y fue posible porque todavía no existe ninguna tabla de compras que apunte a un producto. El icono es un **identificador y no una imagen**, es **opcional** y **solo el upgrade puede llevarlo**: `RN-PM-016` tiene una sola mitad, al revés que `RN-PM-002`, y lo que rechaza es el icono de más. Entran `VAL-012`, `VAL-013` y `CA-PM-096` a `CA-PM-098`, y la tabla de §6.1 gana la fila del icono. | Responsable técnico |
| 0.5.0 | 02-09-2026 | **Un upgrade declara su membresía de ORIGEN**, y no solo el destino, por decisión del responsable del proyecto. Hasta hoy quién podía comprarlo **se deducía** —cualquiera por debajo del destino—, y esa deducción hacía **imposible el salto**: «subir a `ORO`» era el mismo producto y el mismo precio para quien sube un escalón y para quien sube tres. Ahora **cada salto es un producto**. El alta pasa de una membresía a dos, las dos obligatorias en el upgrade y **las dos prohibidas en el bot** (`RN-PM-002`), y el paso 4 del flujo gana lo que el destino solo no podía comprobar: que **no sean la misma** y que **el origen esté por debajo** (`RN-PM-017`, `VAL-014`). `RN-PM-018` declara que **saltar niveles es legítimo** —es la razón de que el origen se declare en lugar de deducirse de la cadena—, de modo que `CA-PM-102` prueba `BECA → ORO` con dos niveles de por medio. `EX-002` deja de hablar de «la membresía» y pasa a decir **cuál de las dos** no existe: con dos campos, un mensaje que no distingue obliga a probar los dos. **La consecuencia que la cabecera acepta entera**: el origen obligatorio significa que **un origen sin producto no falla, no se ofrece** — quien esté en `VIP` sin un upgrade declarado desde `VIP` no verá ninguna subida, y el catálogo se verá perfectamente bien desde administración. | Responsable del proyecto |
| 0.6.0 | 07-09-2026 | **El alta declara el ALCANCE y la IMPLEMENTACIÓN**, por decisión del responsable del proyecto (`RN-PM-019`, `RN-PM-020`). Son **obligatorias, en los dos tipos y sin valor por omisión**, y ahí se apartan de todo lo que esta spec tenía: `RN-PM-002` y `RN-PM-016` obligan o prohíben **según el tipo**, y estas dos no distinguen — un bot también se muestra en algún sitio y también se entrega de alguna forma. **Sin `DEFAULT` a propósito**: un valor por omisión sería una decisión comercial tomada por la columna, y el defecto no se vería porque un producto con el valor supuesto se ve exactamente igual que uno declarado. El alcance es **acumulativo** —`HOTLINKS` incluye la tienda—, de modo que esta spec **no valida ninguna combinación**: los dos valores son legítimos en cualquier producto. La implementación es la que cruza a otro módulo: `RN-MV-020` concede la membresía comprada **solo** si el producto es `AUTOMATICA`. Entran `VAL-015`, `VAL-016` y `CA-PM-110` a `CA-PM-114`, y §6.1 gana las dos filas. **La instantánea de auditoría crece con las dos**, y eso no es cosmético: es el único sitio donde queda escrito con qué configuración nació un producto que después se corrige. | Responsable del proyecto |
| 0.7.0 | 07-09-2026 | **El origen puede ser el destino: nace la RENOVACIÓN** (`RN-PM-017`, `requirements/pm.md` §5.2.3). Aquella regla tenía **dos mitades metidas en una** —«no bajes» y «no repitas»— y solo la primera protegía algo: la segunda impedía cobrar por **tiempo**, que es un producto legítimo. La comparación del caso de uso pasa de `<=` a `<`, `VAL-014` estrecha su mensaje y `CA-PM-104` **se parte**: se queda con el descenso, que sigue rechazándose, y nace `CA-PM-125` para el mismo nivel, que ahora se admite. **`V61` retira `ck_products_origen_distinto`**, con lo que **de `RN-PM-017` no queda nada declarado en el esquema**: la mitad superviviente necesita el `level` de dos filas de `memberships` y un `CHECK` no consulta otra tabla, de modo que la regla vive **entera en el caso de uso**, sin la red que tenía. Es el mismo reparto que `RN-PM-007` con los decimales de la moneda, con la diferencia de que aquel nunca tuvo red. **El agregado pierde una comprobación y no la gana en otro sitio**: comparar dos identificadores dejó de decir nada, y quien decide es `RegisterProductService`, que es el único que conoce los dos niveles. | Responsable del proyecto |
| 0.8.0 | 07-09-2026 | **La membresía resuelta trae su COLOR** (`RN-SP-024`), por decisión del responsable del proyecto. `MembershipView` —el puerto que `SP` publica por **D-25**— gana el campo, y con él la referencia que las cinco respuestas del módulo comparten. **Es un dato puramente estético y aun así cruza por el puerto y no por un `JOIN` de conveniencia**: quien decide qué se sabe de una membresía es `SP`, y abrir una excepción «porque solo es un color» sería la primera grieta en la única regla que sostiene D-25. **El cambio es aditivo**: quien ya consume el puerto sigue leyendo los cuatro campos que leía. Entra `CA-PM-141`. | Responsable del proyecto |
| 0.9.0 | 08-09-2026 | **El alta admite un SEGUNDO precio, el público** (`RN-PM-023`), por decisión del responsable del proyecto, y **`RN-PM-006` deja de exigir «mayor que cero»**. El precio público es **opcional** —y ahí se aparta del alcance y la implementación, que entraron obligatorias el día anterior: omitirlo no deja ninguna decisión sin tomar, porque un producto sin él **se anuncia con el del sistema**, que es lo que hoy hacen todos—. Se expresa en **la misma moneda**, obedece a las mismas dos reglas de importe, y **no se cobra**: ningún cálculo lo lee. **Su nulo significa algo y no es cero**, de modo que §6.2 lo devuelve **presente y nulo**. `VAL-004` y `VAL-005` dejan de hablar de «el precio» y pasan a **nombrar el campo** que incumple, porque con dos importes un mensaje que no distingue obliga a probar los dos. **`CA-PM-005` se reescribe**: decía «rechaza un precio de cero o negativo» y el cero pasó a admitirse — lo que tumbó aquella mitad no fue este cambio sino la **renovación**, porque un `BECA → BECA` es un producto legítimo que vale cero y prohibirlo obligaba a inventarle un céntimo. Entran `CA-PM-145` a `CA-PM-150`, y §13 gana **cuatro casos límite que son decisiones**: el precio público **igual** al del sistema se admite y no se normaliza a nulo; **menor**, también, porque ninguna regla los compara; y el del sistema en **cero** con público informado es el caso que obliga a `CM` a dejar de dividir a ciegas. | Responsable del proyecto |
| 0.10.0 | 12-09-2026 | **El segundo precio pasa a ser el de COMPRA**, por decisión del responsable del proyecto (`requirements/pm.md` v0.23.0 §5.2.6): lo que NEXUS paga por el producto cuando tiene que comprarlo, y donde se guarda lo que costó. `purchasePrice` **sustituye** a `publicPrice` en el cuerpo y en la respuesta —ruptura de contrato aceptada—, con la misma forma: opcional, no negativo, decimales de la moneda del producto. **El nulo cambia de significado**: de «se anuncia con el del sistema» a «no se conoce todavía», que es el estado natural de un producto que se registra antes de comprarse. Entra `RN-PM-024` en las reglas aplicables porque el alta lo devuelve **solo** porque exige `products:create`. `CA-PM-146` a `CA-PM-148` y `CA-PM-150` cambian de nombre de campo sin cambiar de forma; los casos límite del par se reescriben —**mayor** que el del sistema en vez de menor, porque lo que se admite a conciencia ahora es vender por debajo del costo— y nace el del cero frente al nulo. | Responsable del proyecto |
| 0.11.0 | 14-09-2026 | **Entra `rating` en la respuesta** —el promedio y la cantidad de reseñas vivas del producto— por `RN-PM-031` ([`requirements/pm.md`](../../../requirements/pm.md) v0.24.0 §5.2.7): el alta devuelve `rating` **vacío** — `average` nulo y `count` cero — sin consulta. Enmienda de Art. I.7 declarada por el plan de [`RF-PM-009`](../009-resenar-producto/plan.md) §4.1 y construida por sus tareas `T-10` y `T-11`; los criterios que la prueban son `CA-PM-180` a `CA-PM-182` de aquella tripleta. **El promedio no se guarda en `products`**: se cuenta, por un `LEFT JOIN LATERAL` sobre el índice parcial de `product_comments`, para que ninguna copia pueda quedarse atrás. | Responsable del proyecto |
| 0.12.0 | 14-09-2026 | **El alta admite el ENLACE DE UN VIDEO** (`RN-PM-032`, [`requirements/pm.md`](../../../requirements/pm.md) v0.27.0 §5.2.8), por decisión del responsable del proyecto: **opcional, en los dos tipos** —sin la condición cruzada del icono— y validado **solo en su forma**, URL absoluta `http` o `https`, sin espacios, hasta 500 caracteres. **Es una dirección, no un archivo, y el sistema no la sigue.** Ausente y nulo significan lo mismo; la respuesta lo devuelve presente y nulo, como el precio de compra. Nacen `VAL-017` y `CA-PM-219` a `CA-PM-222`. Enmienda de Art. I.7. | Responsable del proyecto |
| 0.13.0 | 14-09-2026 | **El icono pasa a ser OBLIGATORIO en un upgrade** (`RN-PM-034`, [`requirements/pm.md`](../../../requirements/pm.md) v0.29.0 §5.2.9), por decisión del responsable del proyecto: un upgrade siempre tiene portada o icono, **al registrar y en cada corrección**, y como la portada llega **después** del alta (`RF-PM-014`), en el alta el icono es lo único que puede estar. `RN-PM-016` pierde su «opcional incluso ahí»; el bot sigue sin declararlo. **`CA-PM-098` se invierte** y lo sustituye `CA-PM-230`; nace `VAL-018`. La respuesta gana `coverImageUrl`, aquí **siempre nulo y presente**, para que el alta y el detalle tengan la misma forma (`CA-PM-231`). Es una enmienda que construye `RF-PM-014` (Art. I.7), y la instantánea del alta gana `cover_image_id`. | Responsable del proyecto |
| 0.14.0 | 15-09-2026 | **El alcance pasa a cuatro valores explícitos** (`RN-PM-019` reescrita, [`requirements/pm.md`](../../../requirements/pm.md) v0.35.0 §5.2.11), por decisión del responsable del proyecto: `TIENDA`, `HOTLINK`, `AMBOS` y `NINGUNO`. El alta los admite todos y **rechaza `HOTLINKS`** con `400`, que dejó de existir — las filas que lo declaraban pasaron a `AMBOS` en `V92`. `CA-PM-113` se reescribe con `AMBOS`; nace `CA-PM-348`. | Responsable del proyecto |
| 0.15.0 | 22-09-2026 | **Los enlaces del producto entran en el alta, y `links` sustituye a `videoUrl`** (`RN-PM-048`, `RN-PM-049`, [`requirements/pm.md`](../../../requirements/pm.md) v0.43.0 §5.2.14), por decisión del responsable del proyecto. El alta deja de admitir **un** enlace en un campo y admite **una colección con hasta un enlace por tipo**: `VIDEO_PRESENTACION` —lo que `videoUrl` era— y **`CUPON_BOT`**, dónde registra su cuenta quien ya compró. Cada enlace declara **tipo**, **dirección** —obligatoria: no hay enlace sin enlace, de modo que «no tener» es **no declarar el tipo**, y ahí se aparta del nulo de `videoUrl`— y un **identificador externo** opcional de un sistema ajeno, que NEXUS guarda **sin interpretar**. La respuesta los devuelve **crudos y sin componer** (`RN-PM-049`): quien registra es quien corrige, y tiene que recibir lo que luego mandará en el `PATCH`; y devuelve `links` **presente y vacía** cuando no hay, en lugar del presente-y-nulo del precio de compra, porque una colección vacía ya dice «no hay». `VAL-017` se reescribe para los dos tipos y nacen **`VAL-019`** (tipo obligatorio y en el dominio), **`VAL-020`** (tipo repetido, comprobado **sobre el cuerpo y antes de escribir** para que el choque no lo dé la clave primaria con un `500`), **`VAL-021`** (dirección obligatoria), **`VAL-022`** (formato del identificador) y **`VAL-023`** (identificador sobre una dirección con `?` o `#`, que daría un enlace roto **respondiendo `200`**). `CA-PM-219` a `CA-PM-222` se **reescriben** —decían `videoUrl`— y nacen **`CA-PM-380`** a **`CA-PM-384`**. §13 gana tres casos límite, entre ellos el que separa la colección vacía de la ausente aquí y en `RF-PM-004`. Enmienda de Art. I.7. | Responsable del proyecto |
