# SPEC — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Borrador** |
| Autor | Responsable técnico |
| Aprobada por | — |
| Fecha de aprobación | — |

---

## 1. Objetivo

Poner en el sistema algo que se puede vender, con su precio, para que exista un objeto al que una venta pueda referirse.

## 2. Contexto

Hoy la plataforma no tiene **nada que vender**. La membresía de una persona solo cambia porque un administrador se la asigna (`RF-SP-032`), y no existe ningún sitio donde diga cuánto cuesta subir de nivel ni qué servicios se ofrecen. Este es el primer requerimiento del módulo y el que crea el objeto del que dependerán después la compra, el cobro y las comisiones.

**Los dos tipos no se mezclan.** Un producto de **upgrade de membresía** da derecho a pasar al nivel que declara; un producto de **servicio del sistema** da derecho a una prestación y no toca el nivel de acceso de nadie. La diferencia no es una etiqueta: decide qué datos son obligatorios y qué se adquiere al comprarlo.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Define el producto: su tipo, su nombre, su precio y —si es un upgrade— la membresía a la que lleva |

## 4. Alcance

### 4.1 Incluye

- Registrar un producto de tipo **upgrade de membresía**, declarando su membresía destino.
- Registrar un producto de tipo **servicio del sistema**, sin membresía destino.
- Verificar que el destino existe, que la moneda existe y está activa, y que el nombre y el color de la oferta no chocan con los de otro producto vivo.
- Dejar constancia del alta en la auditoría de cambios.

### 4.2 No incluye

- **Comprar el producto ni cobrarlo.** No hay orden, ni pago, ni pasarela (`requirements/pm.md` §1.4).
- **Aplicar el upgrade sobre una persona.** Cambiar el nivel de alguien es escribir en la membresía del usuario, que pertenece a `SP` (`RF-SP-032`).
- **Corregir un producto ya registrado**, que es `RF-PM-004`, ni activarlo o desactivarlo, que es `RF-PM-005`.
- **Definir qué incluye el servicio que se vende.** Este módulo vende el derecho; el contenido pertenece a Academia o a Señales.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-PM-001` | Dos tipos, y el tipo es inmutable | `requirements/pm.md` §5.1 |
| `RN-PM-002` | Destino obligatorio en el upgrade, prohibido en el servicio | `requirements/pm.md` §5.1 |
| `RN-PM-003` | El destino es una membresía real de la cadena | `requirements/pm.md` §5.1 |
| `RN-PM-004` | Un solo upgrade activo por destino | `requirements/pm.md` §5.1 |
| `RN-PM-005` | Nombre único entre los vivos | `requirements/pm.md` §5.1 |
| `RN-PM-006` | El precio es mayor que cero | `requirements/pm.md` §5.1 |
| `RN-PM-007` | El precio respeta los decimales de su moneda | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Tipo | Sí | Upgrade de membresía o servicio del sistema | Uno de los dos, y **no se podrá cambiar después** (`RN-PM-001`) |
| Nombre | Sí | Cómo se llama el producto de cara a quien lo compra | Único entre los productos vivos, sin distinguir mayúsculas ni acentos (`RN-PM-005`) |
| Descripción | No | Qué se lleva quien lo compra | Con longitud acotada |
| Membresía destino | **Depende del tipo** | Nivel al que lleva el upgrade | **Obligatoria** si el tipo es upgrade, **prohibida** si es servicio (`RN-PM-002`). Debe existir (`RN-PM-003`) |
| Precio | Sí | Cuánto cuesta | Mayor que cero (`RN-PM-006`), con los decimales que admita su moneda (`RN-PM-007`) |
| Moneda | Sí | En qué moneda se expresa el precio | Debe existir y estar **activa** (`RN-PM-008`) |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | El producto registrado, con su identificador, su tipo, su estado inicial y su precio **tal como quedó almacenado** |
| Membresía destino resuelta | Cuando es un upgrade: el código, el nombre y el nivel del destino, y no solo su identificador |

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de productos.
- Si el producto es un upgrade, la cadena de membresías tiene al menos un eslabón: sin niveles no hay a dónde subir.
- Existe al menos una moneda activa.

**Postcondiciones**

- El producto queda registrado con su tipo fijado para siempre.
- La auditoría de cambios contiene un evento de creación con el estado inicial completo del producto.
- Si el producto es un upgrade y queda activo, ningún otro upgrade activo apunta a la misma membresía destino.

## 8. Flujo principal

1. El actor envía el tipo, el nombre, el precio, la moneda y —si es un upgrade— la membresía destino.
2. El sistema comprueba que los datos obligatorios de **ese tipo** están presentes y que no llegan los que ese tipo prohíbe.
3. El sistema comprueba que la moneda existe y está activa, y que el precio es mayor que cero y no tiene más decimales que los que esa moneda admite.
4. Si es un upgrade, el sistema comprueba que la membresía destino existe.
5. El sistema comprueba que el nombre no lo tiene ya otro producto vivo.
6. Si es un upgrade y el producto va a quedar activo, el sistema comprueba que ningún otro upgrade activo apunta a ese mismo destino.
7. El sistema registra el producto y emite el evento de auditoría de creación.
8. El sistema devuelve el producto registrado.

## 9. Flujos alternativos

### FA-001 — Producto de servicio

**Cuándo ocurre:** el tipo es servicio del sistema.

1. El sistema **exige que no llegue** membresía destino.
2. Se omiten los pasos 4 y 6 del flujo principal: no hay destino que validar ni unicidad de destino que comprobar.
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

### EX-004 — Ya hay un upgrade activo hacia ese destino

**Condición:** el tipo es upgrade, el producto quedaría activo y otro upgrade activo apunta a la misma membresía.
**Respuesta del sistema:** rechaza el alta **nombrando el producto que ya ocupa ese destino**, para que el actor sepa cuál desactivar si de verdad quiere sustituirlo.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Tipo obligatorio y dentro del dominio | El tipo de producto es obligatorio y debe ser uno de los admitidos. |
| `VAL-002` | Nombre obligatorio | El nombre del producto es obligatorio. |
| `VAL-003` | Longitud del nombre y de la descripción | El nombre no puede exceder la longitud admitida. |
| `VAL-004` | Precio obligatorio y mayor que cero | El precio debe ser mayor que cero. |
| `VAL-005` | Decimales del precio según su moneda | El precio no admite más decimales que los de su moneda. |
| `VAL-006` | Moneda obligatoria | La moneda es obligatoria. |
| `VAL-007` | Destino obligatorio en el upgrade | Un producto de upgrade debe declarar su membresía destino. |
| `VAL-008` | Destino prohibido en el servicio | Un producto de servicio no puede declarar membresía destino. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-PM-001` | El sistema registra un producto de upgrade con su membresía destino y devuelve el destino resuelto con su nivel |
| `CA-PM-002` | El sistema registra un producto de servicio sin membresía destino |
| `CA-PM-003` | El sistema rechaza un upgrade **sin** membresía destino |
| `CA-PM-004` | El sistema rechaza un servicio **con** membresía destino |
| `CA-PM-005` | El sistema rechaza un precio de cero o negativo |
| `CA-PM-006` | El sistema rechaza un precio con más decimales de los que admite su moneda, y acepta el mismo importe con los decimales correctos |
| `CA-PM-007` | El sistema rechaza una moneda inactiva, y lo distingue de una moneda inexistente |
| `CA-PM-008` | El sistema rechaza un nombre que solo difiere de otro existente en mayúsculas o acentos |
| `CA-PM-009` | El sistema rechaza un upgrade hacia un destino que ya tiene otro upgrade activo, y el mensaje nombra al producto que lo ocupa |
| `CA-PM-010` | El sistema rechaza un upgrade cuya membresía destino no existe, y lo hace como dato inválido y no como recurso no encontrado |
| `CA-PM-011` | El sistema registra en la auditoría de cambios un evento de creación con el estado inicial completo del producto |
| `CA-PM-012` | El sistema rechaza el alta a un actor sin el permiso de creación de productos, y no registra nada |

## 13. Casos límite

- **Nombre con espacios al inicio o al final:** se recortan antes de comparar la unicidad. Sin ese recorte, un espacio burlaría la regla y el catálogo mostraría dos productos que se leen igual.
- **Precio con muchos decimales sobre una moneda sin fracción:** el rechazo debe existir aunque la moneda por defecto tenga dos decimales; el caso se prueba con una moneda de cero.
- **Dos altas simultáneas del mismo upgrade:** dos administradores registran a la vez un upgrade hacia el mismo destino. Una debe quedar y la otra ser rechazada; que las dos queden activas es el desenlace que `RN-PM-004` existe para impedir.
- **Dos altas simultáneas con el mismo nombre:** mismo caso sobre la unicidad de nombre.
- **La membresía destino se elimina mientras se registra:** las membresías no se eliminan (`RN-SP-008`), de modo que este caso no existe. Se escribe para que nadie lo busque.
- **Producto de servicio con nombre de un upgrade retirado:** el nombre de un producto eliminado **queda libre**, porque la unicidad es entre los vivos.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| 1 | **¿El producto nace `ACTIVO` o `INACTIVO`?** Nacer activo lo pone a la venta en el mismo instante en que se crea, y hace que `RN-PM-004` muerda ya en el alta. Nacer inactivo permite prepararlo —revisar precio y texto— y publicarlo después con `RF-PM-005`, a costa de que quien registre un producto y no vuelva a entrar crea que lo publicó | Responsable del proyecto | **Abierta** |
| 2 | **¿El producto lleva un código corto y estable además del nombre?** Los roles, las membresías, los países y las monedas lo llevan. Aquí importa para lo que viene: una factura, una comisión o un informe necesitan referirse a «qué se vendió» sin depender de un nombre que `RF-PM-004` permite corregir | Responsable del proyecto | **Abierta** |
| 3 | **¿La descripción es obligatoria?** Es lo que verá quien compra. Opcional deja publicar un producto sin explicar qué es; obligatoria fuerza a escribir algo que quizá se rellene con ruido | Responsable del proyecto | **Abierta** |
| 4 | **¿Puede el mismo destino tener un upgrade activo y varios inactivos?** `RN-PM-004` solo acota los activos, de modo que sí. Conviene confirmarlo: es lo que permite preparar el precio nuevo antes de retirar el viejo, y también lo que llena el catálogo de borradores olvidados | Responsable del proyecto | **Abierta** |
| 5 | **¿El alta emite evento de seguridad además del de cambios?** Un producto no concede privilegios sobre el sistema —es la misma postura que `RF-SP-016` tomó con las membresías—, pero **sí fija un precio**, y quién puso un precio es una pregunta que alguien acabará haciendo | Responsable técnico | **Abierta** |

**Una spec con preguntas abiertas no puede aprobarse.** Esta sección debe quedar vacía antes de pasar la compuerta.
