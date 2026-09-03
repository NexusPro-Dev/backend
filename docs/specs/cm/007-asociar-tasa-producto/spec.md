# SPEC — `RF-CM-007` Asociar una tasa de rol a un producto

| Campo | Valor |
|---|---|
| Requerimiento | `RF-CM-007` |
| Módulo | `CM` — Comisiones |
| Versión | 0.2.0 |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 03-09-2026 |

!!! info "Qué va en este documento"

    **Qué debe pasar, y por qué.** Nada más.

    **Prueba de pertenencia:** si un cambio de tecnología lo invalidaría, no pertenece aquí — va a `plan.md`.

!!! danger "Este requerimiento se construyó ANTES de tener especificación"

    Es una excepción al Art. I.1, registrada en `requirements/cm.md` §4 y en la matriz. Y es **el que la justifica**: sin esta operación el catálogo entero no paga nada a nadie, de modo que rehacer los cinco primeros sin ella habría dejado un módulo imposible de probar de punta a punta.

---

## 1. Objetivo

**Poner una tasa de comisión en vigor**, declarando sobre qué producto rige.

## 2. Contexto

Es la operación más pequeña del módulo y la que lo sostiene entero. **Sin ella, el catálogo por rol es una lista de números que no paga nada a nadie** (`RN-CM-012`).

**Nació de invertir el significado de la ausencia.** Hasta el 01-09-2026 una tarifa sin producto regía sobre **todo el catálogo**: existir era estar en vigor, y no había nada que asociar. Al decidir el responsable del proyecto que el producto saliera a una tabla propia, **la ausencia pasó de significar «todos» a significar «ninguno»**, y esta operación es lo que llena ese hueco.

**Por qué el producto salió de la tasa.** Porque una tasa **rige sobre varios productos** y un producto **tiene una tasa por cada rol** de la cadena. Es una relación de muchos a muchos, y meterla como columna habría obligado a duplicar la tasa una vez por producto — con el resultado previsible de que corregir un porcentaje exigiera corregir cincuenta filas y una se quedara atrás.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Administrador | Declara que un producto paga comisión a un rol, y cuánto |

## 4. Alcance

### 4.1 Incluye

- Asociar una tasa de rol **viva** a un producto **no retirado**.
- Garantizar que **un rol tiene un solo porcentaje sobre un producto** (`RN-CM-013`).
- **Garantizar que ese producto no quede pagando más del 100 % de sí mismo** entre todas sus tasas de rol asociadas (`RN-CM-019`).
- Devolver **todas** las asociaciones de la tasa, no solo la nueva.
- Dejar constancia en la auditoría de cambios.

### 4.2 No incluye

- **Elegir el rol.** No se envía: **se toma de la tasa**. Ver §6.1.
- **Asociar una tasa personalizada.** No se acotan a productos (`RN-CM-014`), y viven en otra tabla — esta operación no puede alcanzarlas.
- **Sustituir la asociación existente.** Si el rol ya paga por ese producto, la operación se rechaza; cambiarla es desasociar y volver a asociar, que son dos decisiones.
- **Declarar desde cuándo rige la asociación.** No tiene vigencia: rige desde que existe hasta que se retira.
- **Retirar la asociación**, que es `RF-CM-008`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-CM-002` | El producto asociado debe existir | `requirements/cm.md` §5.1 |
| `RN-CM-010` | No se configura lo que ya no se vende | `requirements/cm.md` §5.1 |
| `RN-CM-012` | Una tasa de rol no rige hasta que se asocia | `requirements/cm.md` §5.1 |
| `RN-CM-013` | Un solo porcentaje por rol y producto | `requirements/cm.md` §5.1 |
| `RN-CM-014` | Solo las tasas de rol se asocian a productos | `requirements/cm.md` §5.1 |
| `RN-CM-019` | Un producto no puede pagar más del 100 % de sí mismo | `requirements/cm.md` §5.1 |

**`RN-CM-014` no se comprueba: se cumple por construcción.** Las tasas personalizadas viven en otra tabla y esta operación no tiene forma de nombrarlas.

**`RN-CM-019` sí se comprueba, y es la única regla de esta operación que necesita leer más de una fila y más de una tabla.** Suma el porcentaje ocupado de cada asociación viva del producto —el de una tasa `PORCENTAJE` tal cual, el de una `FIJO` convertido a `fixed_amount ÷ precio × 100` contra el precio que `PM` publica hoy— más el de la tasa que se está asociando, y rechaza si pasa de cien.

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Tasa | Sí | Cuál se pone en vigor | Debe existir y **estar viva** |
| Producto | Sí | Sobre cuál rige | Debe existir y **no estar retirado** (`RN-CM-002`, `RN-CM-010`) |

**El rol no se recibe, y su ausencia es una decisión y no un olvido.** Se toma de la tasa. Aceptarlo permitiría enviar uno distinto del que la tasa declara, y aunque el sistema lo rechazaría, el error llegaría como un problema de integridad en lugar de como lo que es: **un dato que nadie tenía que dar**.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| **Todas** las asociaciones de la tasa | De cada una: el producto resuelto, el rol resuelto y el porcentaje |

**Se devuelven todas y no solo la que se acaba de crear**, porque la pregunta que quien asocia tiene después es «¿sobre qué rige ahora esta tasa?», y responderla exigiría una segunda llamada.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de modificación de comisiones.
- La tasa existe y está viva. El producto existe y no está retirado.

**Postcondiciones**

- **Ese producto pasa a pagar ese porcentaje a ese rol, inmediatamente.**
- Ningún otro producto cambia.
- Ese rol **no tiene ninguna otra tasa** asociada a ese producto (`RN-CM-013`).
- **Ese producto no queda pagando más del 100 % de sí mismo** entre todas sus tasas de rol asociadas (`RN-CM-019`).
- La auditoría de cambios contiene el evento con la tasa, el rol, el producto y el porcentaje.

## 8. Flujo principal

1. El actor envía la tasa y el producto.
2. El sistema comprueba que la tasa existe y está viva.
3. El sistema comprueba que el producto existe y no está retirado.
4. El sistema suma el porcentaje ocupado de las asociaciones vivas de ese producto más el de la tasa que se va a asociar, y rechaza si la suma pasa de cien (`RN-CM-019`).
5. El sistema registra la asociación, **copiando el rol de la tasa**.
6. El sistema comprueba que ese rol no tenía ya otra tasa asociada a ese producto.
7. El sistema emite el evento de auditoría y devuelve todas las asociaciones de la tasa.

**Los pasos 5 y 6 ocurren a la vez y en ese orden**, y no al revés: comprobar antes de escribir sería una carrera. Ver `plan.md` §4.

**El paso 4 comprueba una suma, y una suma sí se puede leer antes de escribir** — al contrario que `RN-CM-013`, no depende de que dos peticiones no se crucen sobre la fila que cada una va a insertar, depende de las filas que **ya existen**. Lo que sí puede cruzarse son **dos peticiones sobre el mismo producto**, y de eso habla `plan.md` §4.1.

## 9. Flujos alternativos

### FA-001 — Una tasa sobre varios productos

**Cuándo ocurre:** la misma tasa ya rige sobre otros productos.

1. Se admite. **Es el caso que justifica que el producto viva en una tabla aparte.**
2. La tasa no se duplica: corregir su porcentaje cambia lo que pagan todos.

### FA-002 — Un producto que paga a varios roles

**Cuándo ocurre:** el producto ya tiene tasas asociadas de otros roles.

1. Se admite. Es el **override** de `RN-CM-011` visto desde el producto: cada nivel de la cadena gana su propio porcentaje sobre el mismo importe.
2. **Desde el 03-09-2026 la suma de esos porcentajes ya no puede pasar de cien** (`RN-CM-019`): se admite mientras quepa, y se rechaza la asociación que la haría pasarse — ver `EX-005`.
3. **Esto no cierra `RN-CM-011` entero.** Si algún nivel de la cadena cobra por una tasa **personalizada** en lugar de por el rol asociado a este producto, esa fila no está aquí para sumarse: la personalizada no se asocia a nada (`RN-CM-004`). El tope de la cadena completa sigue sin dueño hasta que exista la liquidación.

### FA-003 — Asociar una tasa del cero por ciento

**Cuándo ocurre:** la tasa declara cero.

1. Se admite, y es **la única forma de declarar que un producto no paga a un rol**.
2. **No es lo mismo que no asociar nada**: `RF-CM-005` devuelve «resuelta con cero» en un caso y «sin tarifa» en el otro.

## 10. Excepciones

### EX-001 — La tasa no existe o está retirada

**Condición:** el identificador no corresponde a ninguna tasa viva.
**Respuesta del sistema:** rechaza la asociación diciendo que la tasa indicada no existe.

**Una tasa retirada no se asocia**, y es coherente con `RN-CM-015` visto desde el otro lado: **poner en vigor lo que alguien declaró que no debió existir es lo contrario de lo que el retiro significa.**

### EX-002 — El producto está retirado

**Condición:** el producto existe y ha sido retirado del catálogo.
**Respuesta del sistema:** rechaza la asociación diciendo que no se pueden asociar tasas a un producto retirado (`RN-CM-010`).

**Configurar lo que nadie puede vender no falla nunca y no sirve nunca**: se rechaza al declararlo, que es el único momento en que alguien está mirando. Las asociaciones **que ya existían permanecen** — la regla prohíbe declarar, no conservar.

### EX-003 — El producto no existe

**Condición:** el identificador no corresponde a ningún producto.
**Respuesta del sistema:** rechaza la asociación diciendo que el producto indicado no existe. Se distingue de `EX-002`: quien escribió bien el identificador no debe buscar el error donde no está.

### EX-004 — Ese rol ya paga por ese producto

**Condición:** ya existe una asociación de **ese rol** con **ese producto**, sea con esta tasa o con otra.
**Respuesta del sistema:** rechaza la asociación diciendo que ese rol ya tiene una tasa asociada a ese producto, y que hay que retirar la existente antes de declarar otra.

**El conflicto no es «esta tasa ya está asociada» sino «este rol ya cobra por este producto»**, que puede ser con otra tasa distinta. Decir lo primero mandaría a buscar el problema en la tasa que se está asociando, cuando está en la que ya estaba.

### EX-005 — La asociación haría pasar al producto de cien

**Condición:** sumando el porcentaje ocupado de las tasas de rol ya asociadas a ese producto más el de la tasa que se quiere asociar, el resultado supera cien.
**Respuesta del sistema:** rechaza la asociación diciendo que ese producto quedaría pagando más del 100 % de sí mismo, y nombra los porcentajes ya ocupados (`RN-CM-019`).

**No es el mismo conflicto que `EX-004`.** Ahí el problema es que ese rol ya cobra por ese producto; aquí el rol es nuevo y el problema está en la **suma con los demás roles** que ya cobran. Pueden darse los dos a la vez —un rol repetido que además haría pasar la suma de cien— y el sistema informa el que encuentra primero: `RN-CM-019` se comprueba **antes** de escribir (§8, paso 4), y `RN-CM-013` la cierra la clave primaria **al escribir** (§8, paso 6). Un rol repetido cuya tasa además haría pasar la suma de cien se rechaza como `EX-005`, no como `EX-004` — la petición nunca llega a intentar el `INSERT` que `EX-004` traduce.

## 11. Validaciones

| ID | Regla | Mensaje |
|---|---|---|
| `VAL-001` | Producto obligatorio | El producto es obligatorio. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-CM-063` | Asocia la tasa y devuelve **todas** sus asociaciones, con el producto, el rol y el porcentaje resueltos |
| `CA-CM-064` | **La asociación pone la tasa en vigor**: el producto pasa a comisionar a ese rol |
| `CA-CM-065` | El rol se copia **de la tasa**; enviarlo en la petición **se rechaza**, no se ignora |
| `CA-CM-066` | Rechaza asociar **otra tasa del mismo rol** al mismo producto |
| `CA-CM-067` | **Dos roles distintos** sobre el mismo producto conviven |
| `CA-CM-068` | **Una misma tasa** rige sobre varios productos **sin duplicarse** |
| `CA-CM-069` | Rechaza un producto **retirado**, y lo distingue de uno inexistente |
| `CA-CM-070` | Rechaza asociar desde una tasa **retirada** |
| `CA-CM-071` | Dos asociaciones **simultáneas** del mismo rol al mismo producto: solo una entra |
| `CA-CM-072` | Exige el permiso de modificación de comisiones |
| `CA-CM-105` | Asociar una tasa que deja la suma del producto en **exactamente cien** se admite |
| `CA-CM-106` | Asociar una tasa que haría **pasar de cien** la suma del producto se rechaza, **y no queda ninguna fila nueva** |
| `CA-CM-107` | La suma cuenta un **valor fijo** convertido a `fixed_amount ÷ precio × 100` **contra el precio de ese producto**, no como cifra directa |
| `CA-CM-108` | Dos asociaciones **simultáneas** al mismo producto, cada una dentro del tope por separado pero **juntas fuera**: solo una entra |
| `CA-CM-109` | Asociar sobre un producto **sin ninguna asociación previa** solo compara contra la tasa que se está asociando |



## 13. Casos límite

- **Asociar una tasa que ya rige sobre veinte productos:** se admite sin límite. Es el uso previsto, y la consecuencia está en `RF-CM-003`: corregir esa tasa cambia lo que pagan los veintiuno **a la vez**.
- **El producto se retira después de asociarse:** la asociación **permanece**, y el producto sigue resolviendo su comisión con normalidad. `RN-CM-010` prohíbe declarar, no conservar, y preguntar qué se pagaba por algo que ya no se vende es legítimo.
- **La tasa se retira después de asociarse:** **no puede** (`RN-CM-015`). Hay que desasociar primero.
- **Un rol quiere dos porcentajes sobre el mismo producto:** no tiene sentido y el sistema lo impide. Con dos, la resolución tendría **dos respuestas válidas** y la elección quedaría a criterio de cómo se ejecute la consulta.
- **Asociar la misma tasa al mismo producto dos veces:** es el caso particular de `EX-004` donde la tasa existente es la misma. Se rechaza igual: **la operación no es idempotente**, y no lo es porque el conflicto es informativo.
- **Un producto sin ninguna asociación:** no comisiona a nadie. **No es un estado inválido**, y `RF-CM-005` lo dice devolviendo «sin tarifa».
- **Un producto de precio cero:** no existe. `ck_products_price_positive` (`PM`, `V39`) exige `price > 0` desde antes de que este módulo existiera, así que la conversión de un valor fijo nunca divide entre cero — no es una comprobación de este requerimiento, es una garantía que ya traía el esquema de `PM`.
- **El precio del producto cambia después de asociar:** la suma se comprobó contra el precio de **ese instante**, y nadie la repite cuando `RF-PM-004` cambia el precio. Puede quedar pasada de cien sin que nada lo detecte. Es el hueco que `cm.md` `RN-CM-019` acepta a conciencia, y solo lo cierra la liquidación futura.
- **La cadena comercial incluye una tasa personalizada:** la suma de esta operación no la ve — la personalizada no se asocia a ningún producto (`RN-CM-004`) — y el tope de `RN-CM-011` para ese caso sigue sin dueño.

## 14. Preguntas abiertas

| # | Pregunta | Responsable | Estado |
|---|---|---|---|
| — | Ninguna | — | — |

**Queda declarada una decisión discutible que ya está tomada:** asociar reutiliza el permiso de **modificación** y no estrena uno propio, aunque sea **lo único que pone una tasa en vigor**. El criterio, en `cm.md` §6: asociar cambia lo que se paga tanto como corregir un porcentaje, de modo que quien puede lo uno debería poder lo otro. Separarlos tendría sentido el día que alguien deba poder **revisar tarifas sin poder activarlas**.

**Y queda declarada otra, del 03-09-2026:** `RN-CM-019` compara una suma, y una suma no se puede leer y comprobar de forma segura frente a otra petición que hace lo mismo sobre el mismo producto sin algún tipo de bloqueo — al contrario que `RN-CM-013`, que se cierra sola en la clave primaria. Se decidió cerrar esa ventana con un bloqueo consultivo de Postgres por producto, y no dejarla como hueco aceptado; el porqué y el detalle técnico están en `plan.md` §4.1.

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 02-09-2026 | Redacción inicial, **después de construirse el requerimiento** — excepción al Art. I.1 declarada en cabecera, y **este es el requerimiento que la justifica**: sin él el catálogo no paga nada y el módulo no se puede probar de punta a punta. Recoge la operación que nació al invertirse el significado de la ausencia el 01-09-2026, y §2 explica por qué el producto salió de la tasa: es una relación de muchos a muchos, y como columna habría obligado a duplicar la tasa una vez por producto. §6.1 declara por qué **el rol no se recibe**, y §10 por qué el conflicto de `EX-004` habla del **rol** y no de la tasa. §13 recoge las dos asimetrías con `RN-CM-010` y `RN-CM-015`: el producto retirado **conserva** sus asociaciones pero no admite nuevas, y la tasa retirada **no puede** tenerlas. | Responsable técnico |
| 0.2.0 | 03-09-2026 | **Nace `RN-CM-019`** (`cm.md` v0.8.0), y esta operación es una de las dos que la comprueban. §4.1 y §7 la suman a lo que asociar garantiza; §8 gana un paso nuevo, **antes** de escribir, que suma el porcentaje ocupado de las asociaciones vivas del producto —convirtiendo cada valor fijo contra el precio que `PM` publica— más el de la tasa entrante, y rechaza si pasa de cien. `FA-002` **se corrige**: decía que la suma de varios roles sobre un producto podía pasar de cien sin que nada lo impidiera, y desde hoy **sí hay algo que lo impide**, aunque no todo — se aclara que una tasa **personalizada** en la cadena sigue fuera de esta suma, porque no se asocia a ningún producto. Nace `EX-005`, distinto de `EX-004`: aquí el rol es nuevo y el conflicto está en la suma con los demás roles que ya cobran, no en que ese rol repita asociación. §13 suma tres casos límite: el precio cero —que resulta **imposible**, porque `ck_products_price_positive` (`PM`, `V39`) ya lo impedía—; el precio que cambia después de asociar, que **nadie vuelve a comprobar**; y la cadena con una tasa personalizada, que esta suma no alcanza. §14 registra la decisión de cerrar con un **bloqueo consultivo de Postgres** la ventana que abre comprobar una suma antes de escribir — la primera vez que el módulo usa ese mecanismo en lugar de una restricción del esquema, porque una suma agregada entre filas hermanas, que además lee el precio de otro módulo, no cabe en ningún `CHECK` ni `EXCLUDE`. | Responsable del proyecto |
