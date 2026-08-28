# SPEC — `RF-PM-001` Registrar producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-PM-001` |
| Módulo | `PM` — Productos y Mercadeo |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 26-08-2026 |
| Enmendada el | 27-08-2026 — ver §15 |

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
- Verificar que el destino existe, que la moneda existe y está activa, y que ni el nombre ni el código chocan con los de otro producto.
- Dejar constancia del alta en la auditoría de cambios.
- **Registrarlo `INACTIVO`**: existe, y no se ofrece hasta que alguien lo publique con `RF-PM-005`.

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
| `RN-PM-005` | Nombre único entre los vivos | `requirements/pm.md` §5.1 |
| `RN-PM-006` | El precio es mayor que cero | `requirements/pm.md` §5.1 |
| `RN-PM-007` | El precio respeta los decimales de su moneda | `requirements/pm.md` §5.1 |
| `RN-PM-008` | La moneda debe estar activa al declararla | `requirements/pm.md` §5.1 |
| `RN-PM-012` | El producto nace inactivo | `requirements/pm.md` §5.1 |
| `RN-PM-013` | El código no se libera nunca | `requirements/pm.md` §5.1 |
| `RN-PM-015` | La vigencia se mide en días y es opcional | `requirements/pm.md` §5.1 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Código | Sí | Referencia corta y estable del producto | Letras mayúsculas, dígitos y guion bajo, empezando por letra. **Único para siempre**, incluso frente a los eliminados, e **inmutable** (`RN-PM-013`) |
| Tipo | Sí | Upgrade de membresía o servicio del sistema | Uno de los dos, y **no se podrá cambiar después** (`RN-PM-001`) |
| Nombre | Sí | Cómo se llama el producto de cara a quien lo compra | Único entre los productos vivos, sin distinguir mayúsculas ni acentos (`RN-PM-005`) |
| Descripción | No | Qué se lleva quien lo compra | Con longitud acotada. Opcional al registrar; **sin ella el producto no podrá publicarse** (`RN-PM-014`) |
| Membresía destino | **Depende del tipo** | Nivel al que lleva el upgrade | **Obligatoria** si el tipo es upgrade, **prohibida** si es servicio (`RN-PM-002`). Debe existir (`RN-PM-003`) |
| Precio | Sí | Cuánto cuesta | Mayor que cero (`RN-PM-006`), con los decimales que admita su moneda (`RN-PM-007`) |
| Moneda | Sí | En qué moneda se expresa el precio | Debe existir y estar **activa** (`RN-PM-008`) |
| Vigencia | No | Cuántos días dura lo que el producto otorga, contados desde la compra | Entero mayor que cero. **Sin ella, lo adquirido no caduca** (`RN-PM-015`) |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Producto | El producto registrado, con su identificador, su código, su tipo, su precio **tal como quedó almacenado** y su estado, que es siempre `INACTIVO` |
| Membresía destino resuelta | Cuando es un upgrade: el código, el nombre y el nivel del destino, y no solo su identificador |

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

1. El actor envía el código, el tipo, el nombre, el precio, la moneda y —si es un upgrade— la membresía destino.
2. El sistema comprueba que los datos obligatorios de **ese tipo** están presentes y que no llegan los que ese tipo prohíbe.
3. El sistema comprueba que la moneda existe y está activa, y que el precio es mayor que cero y no tiene más decimales que los que esa moneda admite.
4. Si es un upgrade, el sistema comprueba que la membresía destino existe.
5. El sistema comprueba que el código no lo ha tenido nunca otro producto, y que el nombre no lo tiene ya otro producto vivo.
6. El sistema registra el producto **inactivo** y emite el evento de auditoría de creación.
7. El sistema devuelve el producto registrado.

## 9. Flujos alternativos

### FA-001 — Producto de servicio

**Cuándo ocurre:** el tipo es servicio del sistema.

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
| `VAL-004` | Precio obligatorio y mayor que cero | El precio debe ser mayor que cero. |
| `VAL-005` | Decimales del precio según su moneda | El precio no admite más decimales que los de su moneda. |
| `VAL-006` | Moneda obligatoria | La moneda es obligatoria. |
| `VAL-007` | Destino obligatorio en el upgrade | Un producto de upgrade debe declarar su membresía destino. |
| `VAL-008` | Destino prohibido en el servicio | Un producto de servicio no puede declarar membresía destino. |
| `VAL-009` | Código obligatorio | El código del producto es obligatorio. |
| `VAL-010` | Formato del código | El código solo admite letras mayúsculas, dígitos y guion bajo, y debe empezar por letra. |
| `VAL-011` | Vigencia mayor que cero | La vigencia debe ser un número de días mayor que cero. |

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

## 13. Casos límite

- **Nombre con espacios al inicio o al final:** se recortan antes de comparar la unicidad. Sin ese recorte, un espacio burlaría la regla y el catálogo mostraría dos productos que se leen igual.
- **Precio con muchos decimales sobre una moneda sin fracción:** el rechazo debe existir aunque la moneda por defecto tenga dos decimales; el caso se prueba con una moneda de cero.
- **Dos altas simultáneas del mismo upgrade:** dos administradores registran a la vez un upgrade hacia el mismo destino. Una debe quedar y la otra ser rechazada; que las dos queden activas es el desenlace que `RN-PM-004` existe para impedir.
- **Dos altas simultáneas con el mismo nombre:** mismo caso sobre la unicidad de nombre.
- **La membresía destino se elimina mientras se registra:** las membresías no se eliminan (`RN-SP-008`), de modo que este caso no existe. Se escribe para que nadie lo busque.
- **Producto de servicio con nombre de un upgrade retirado:** el nombre de un producto eliminado **queda libre**, porque la unicidad es entre los vivos.

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
