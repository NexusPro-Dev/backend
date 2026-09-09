# SPEC — `RF-SP-045` Registro de clientes por enlace

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-045` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable del proyecto |
| Fecha de aprobación | 09-09-2026 |
| Enmendada | 09-09-2026 — **la cuenta de broker en el formulario** (`RN-SP-042`), obligatoria solo cuando el producto es `FREE → FREE`; y **los dos catálogos públicos dejan de ser una pregunta abierta**. Ver §15 |
| Enmendada | 08-09-2026 — `RN-SP-035` y `RN-SP-037`: el formulario público exige **documento y teléfono**; `CA-SP-600` y `CA-SP-601`, y **el bloqueo del catálogo público crece a dos** (§14) |
| Enmendada | 07-09-2026 — `RN-SP-034`: el formulario público exige país; nace `EX-006`, `VAL-009`, `CA-SP-582` y `CA-SP-583`, y **una pregunta abierta que esta enmienda no puede cerrar** (§14, pregunta 1) |

---

## 1. Objetivo

Que un cliente entre al sistema por sí mismo desde un enlace, quedando con su membresía puesta y atribuido al vendedor que lo trajo.

## 2. Contexto

**Hoy no existe ninguna forma de que alguien se registre.** La única alta de personas es `RF-SP-024`, y exige `users:create` — un permiso de administración. Un cliente que llega desde una campaña no tiene cuenta, no tiene token y no tiene a quién pedírselo: `SecurityConfig` declara seis rutas públicas —la salud, las tres de sesión y las dos de recuperación— y todo lo demás exige estar autenticado.

**El enlace lleva dos datos y ninguno es un secreto**: el producto —por su código o su identificador— y el vendedor que lo generó. De ahí sale todo lo demás: el producto declara la membresía destino (`RN-PM-002` la hace obligatoria en los upgrades) y su vigencia en días, y el vendedor es a quien se atribuye el cliente.

**El cliente cuelga de su vendedor en `user_supervisors`, y no en una estructura propia.** Es la decisión que el responsable del proyecto tomó el 01-09-2026, contra la propuesta de separarlas.

Separarlas tenía un argumento formal: `user_supervisors` nació para la **fuerza comercial**, y `RN-SP-020` exige allí que el superior porte el **rol padre inmediato** del rol vendedor del subordinado — un `CONSUMIDOR` no porta ninguno, de modo que la regla lo rechazaría. Pero eso es un problema de la **regla**, no de la tabla, y se resuelve enmendándola: `RN-SP-020` gana una rama para el consumidor, a quien le basta un superior con **algún** rol `VENDEDOR`.

Lo que se gana con una sola tabla es que **el árbol comercial esté completo**. Subir de un cliente a su agente, de este a su director y de este a su manager —que es exactamente lo que una liquidación multinivel hace— pasa a ser **un recorrido de una tabla**, y no un join con una segunda estructura más un caso especial en la hoja. La hoja es donde está el dinero, y es donde un caso especial se implementa mal.

Se hereda además, sin escribir nada, lo que aquella estructura ya tiene resuelto: **un superior vigente** (`RN-SP-021`), el historial que determina a quién se atribuía cada resultado en cada momento, y la protección de `RN-SP-022`. Lo que **no** viaja en la relación es con qué producto entró el cliente: ese dato pertenece al hecho comisionable —el depósito— y no al vínculo, por el mismo criterio con el que `requirements/pm.md` §1.4 exige que cada compra guarde su propio importe.

**Que el enlace sea adivinable no lo convierte en una llave, y eso hay que argumentarlo** porque es la objeción evidente. El código de producto es legible por diseño —`ck_products_code_format` lo obliga a `^[A-Z][A-Z0-9_]*$`, y se llama `UPGRADE_ORO`—, de modo que cualquiera puede componer un enlace que no le dieron. **No gana nada haciéndolo**, y por dos motivos que se suman:

- Los productos que llevan a una membresía **de pago** no se registran por aquí: van por la pasarela, que es de Finanzas y no existe todavía (§4.2). Forjar ese enlace no lleva a ninguna parte.
- El que lleva a la membresía **gratuita** produce una cuenta que **no puede operar**: nace en `FTD_PENDIENTE` y ahí se queda hasta que haya un depósito. Forjarlo consigue exactamente lo mismo que pedirlo.

Lo que sí se consigue forjando es **atribuirse a un vendedor cualquiera**, y eso queda declarado como riesgo asumido en §14: no concede acceso, pero ensucia la base sobre la que después se comisiona.

**Por eso el enlace no se persiste**: se compone. Una tabla de enlaces emitidos —con caducidad, usos y revocación— es la defensa que haría falta si el enlace concediera algo, y no concede nada. Escribirla hoy sería pagar una tabla, un requerimiento y una operación de emisión para proteger una puerta que ya está cerrada por otro lado.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Persona sin cuenta | Se registra desde el enlace. **No está autenticada**: es el primer endpoint público del sistema que **crea** algo |
| Vendedor | No participa en la operación. Su identificador viaja en el enlace y queda como atribución del cliente |

## 4. Alcance

### 4.1 Incluye

- Crear la cuenta con los datos que la persona declara y **la contraseña que ella elige**.
- Concederle el rol de clasificación `CONSUMIDOR` que corresponde y **la membresía que declara el producto**, en la misma operación (`RN-SP-018`).
- Fijar la **vigencia** de esa membresía a partir de `validity_days` del producto, o sin fin si el producto no la declara.
- Dejar la cuenta en **`FTD_PENDIENTE`**: autentica, y no opera.
- Colgar al cliente de ese vendedor en **`user_supervisors`**, la misma estructura donde ya viven los vendedores entre sí.
- **Declarar su cuenta de broker** —broker e identificador— cuando el producto del enlace es `FREE → FREE` (`RN-SP-042`, 09-09-2026), en la **misma** transacción.

### 4.2 No incluye

- **El camino de pago.** Un producto que lleva a una membresía distinta de la gratuita exige pasarela, y la pasarela es del área de **Finanzas** ([`modules.md` §6](../../../modules.md#6-alcance-por-inventariar)), que no existe. Se rechaza con `EX-004` en lugar de registrarse a medias — vale aquí el argumento de [`requirements/pm.md` §1.4](../../../requirements/pm.md): registrar la compra antes de que exista el cobro produce un objeto que dice que alguien pagó cuando nadie lo verificó.
- **La confirmación del depósito.** La hará el webhook del bróker, y se construye más adelante por decisión del responsable del proyecto (01-09-2026). Mientras tanto la salida de `FTD_PENDIENTE` es manual, por `RF-SP-028` (§7).
- **La retención de quien está en `FTD_PENDIENTE`.** Qué puede hacer y qué no dentro del sistema es `RF-SP-046`. Este requerimiento produce el estado; aquel lo hace valer.
- **Emitir y administrar enlaces.** El enlace se compone, no se guarda (§2).
- **Los enlaces sobre productos `BOT`.** Un bot no declara membresía destino —`ck_products_type_target` se lo prohíbe— y un registro sin nivel violaría `RN-SP-018`. Se rechaza con `EX-003`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-013` | Membresía solo para consumidores | `requirements/sp.md` §5.1 |
| `RN-SP-016` | Nombre de usuario y correo únicos; el nombre de usuario no cambia | `requirements/sp.md` §5.1 |
| `RN-SP-018` | Todo consumidor tiene membresía, y se adquieren juntos | `requirements/sp.md` §5.1 |
| `RN-SP-026` | **Nueva.** El registro por enlace nace en `FTD_PENDIENTE` | §5.1, este requerimiento |
| `RN-SP-027` | **Nueva.** Todo cliente registrado por enlace queda atribuido a un vendedor | §5.1, este requerimiento |
| `RN-SP-028` | **Nueva.** El cliente cuelga de su vendedor en la misma estructura comercial | §5.1, este requerimiento |
| `RN-SP-020` | **Enmendada.** Gana su rama de consumidor: al cliente le basta un superior con **algún** rol `VENDEDOR` | `requirements/sp.md` §5.1 |
| `RN-SP-021` | Un superior vigente por persona, con historial | `requirements/sp.md` §5.1 |
| `RN-SP-022` | **Alcanzada.** «Personas a cargo» pasa a incluir la cartera de clientes | `requirements/sp.md` §5.1 |
| `RN-PM-002` | El upgrade declara membresía destino; el bot no puede | `requirements/pm.md` §5.1 |
| `RN-PM-009` | Solo se ofrece lo activo | `requirements/pm.md` §5.1 |
| `RN-PM-015` | La vigencia se mide en días y es opcional | `requirements/pm.md` §5.1 |
| `RN-SEG-*` | Política de contraseñas | `security.md` §3.2 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Producto | Sí | Código **o** identificador del producto del enlace | Debe existir, estar `ACTIVO`, no retirado y ser `UPGRADE_MEMBRESIA` |
| Vendedor | Sí | **Nombre de usuario** de quien generó el enlace | Debe existir, no estar eliminado y portar un rol `VENDEDOR` |
| Nombre y apellidos | Sí | Datos de la persona | No pueden quedar vacíos |
| Nombre de usuario | Sí | Identidad estable | Única, sin arroba (`RN-SP-016`) |
| Correo | Sí | Identidad corregible | Único, formato válido |
| Contraseña | Sí | **La elige la persona** | Debe cumplir la política |
| Tipo de documento | Sí | **Abreviación** del tipo —`CC`, `PA`— y no su identificador | Debe existir en el catálogo y estar activo. **El catálogo solo lleva documentos de mayor de edad** (`RN-SP-035`), de modo que este formulario **no puede registrar a un menor**: no hay abreviación que enviar |
| Número de documento | Sí | El número | Único junto con el tipo, entre todas las personas |
| Teléfono | Sí | Vía de contacto | `RN-SP-037` |
| Dirección, complemento y ciudad | No | Datos de contacto | Opcionales, como en el alta administrativa |
| País | Sí | **Código ISO 3166-1 alfa-3** del país donde está la persona | Debe existir en el catálogo y estar **activo** (`RN-SP-034`) |
| Broker | **Condicional** | Identificador del broker donde tiene su cuenta | Debe existir y estar activo. **Obligatorio solo si el producto es `FREE → FREE`** (`RN-SP-042`) |
| Identificador de cuenta | **Condicional** | El número de cuenta de la persona **en ese broker** | Ídem. Único junto con el broker en todo el sistema (`RN-SP-038`) |


!!! important "Por qué la cuenta de broker es obligatoria SOLO en el enlace `FREE → FREE`"

    Decisión del responsable del proyecto, 09-09-2026. **La condición no es de forma sino de encierro**: la cuenta que este registro crea nace en `FTD_PENDIENTE`, que **autentica y no opera**, y quien la saca de ahí es el **depósito confirmado por el webhook del broker**. Sin la cuenta declarada, el sistema no puede saber de quién es un depósito cuando llegue — y la persona se queda encerrada en un estado del que nadie puede sacarla salvo a mano.

    **Hoy esa condición se cumple siempre**, y conviene saberlo: el registro solo admite productos cuya membresía destino es la gratuita (§8, paso 3), y `RN-PM-017` impide que un producto apunte por debajo de su origen — de modo que **todo producto admisible aquí es `FREE → FREE`**. La condicionalidad no cambia nada hoy.

    **Lo que compra es el día que se abra el camino de pago.** Cuando `EX-004` deje de rechazar, entrarán productos hacia membresías de pago cuyo acceso **no depende de ningún depósito**, y ahí exigir la cuenta de broker sería pedir un dato que no hace falta. Escrita como condición, ese día no hay que acordarse de nada; escrita como campo obligatorio a secas, alguien tendría que descubrir por qué se pide.

**El tipo de documento viaja por abreviación, igual que el país por código y el producto por su código**, y por la misma razón: es un formulario público al que no se le pide conocer identificadores internos. `CC` es además lo que la persona reconoce.

**Y aquí la validación de mayoría de edad se vuelve visible**: este es el único alta del sistema que **cualquiera** puede ejecutar sin credenciales, y es precisamente donde más valdría una comprobación olvidable. No hay ninguna — el catálogo no ofrece el tipo de un menor, de modo que este endpoint hereda la regla **sin implementarla**. Es el argumento entero de `RF-SP-051` `spec.md` §2, puesto a prueba en el peor sitio.

**El país viaja por código y no por identificador**, y es el mismo criterio que gobierna los otros dos campos de referencia de este formulario: el producto admite su código y el vendedor va por nombre de usuario. La razón aquí es más fuerte todavía — `RN-SP-009` hace que el código de un país **no cambie nunca**, de modo que es el identificador más estable que existe en el sistema, y además es el que la persona reconoce. Meter un UUID en un formulario público sería pedirle al navegador que conociera identificadores internos para dar de alta a alguien que aún no tiene cuenta.

**El vendedor viaja por nombre de usuario y no por identificador**, y es una decisión: el nombre de usuario es inmutable por `RN-SP-016` —de modo que un enlace impreso en un folleto sigue resolviendo dentro de dos años—, es legible, y no expone el identificador interno de un empleado en una dirección que se comparte por redes.

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Confirmación | Que la cuenta quedó creada, su estado y qué falta para operar |

**No se devuelven credenciales de sesión.** Registrarse no inicia sesión: quien acaba de crear su cuenta pasa por `RF-SP-034` como todo el mundo. Devolver un token aquí duplicaría la emisión de sesiones en dos requerimientos, y el segundo acabaría olvidando alguna de las reglas del primero.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- Ninguna. La operación es **pública**: quien la usa no tiene cuenta todavía.

**Postcondiciones**

- Existe una cuenta con estado **`FTD_PENDIENTE`**, con su rol de consumidor y su membresía, cuya vigencia sale de la del producto.
- La contraseña **no queda marcada para cambio obligatorio**: la eligió su titular y nadie más la conoce. Es la misma distinción que `RF-SP-040` hizo frente al restablecimiento por un administrador.
- Existe una fila vigente en `user_supervisors` con el cliente a cargo del vendedor del enlace.
- Existe una fila en `user_brokers` con el broker y el identificador declarados, y **`broker_username` en nulo**: lo rellena el webhook del broker (`RN-SP-040`).
- Queda constancia en la auditoría de cambios y en la de seguridad, con el vendedor y el producto en el detalle.
- **Nada de lo anterior ocurre a medias**: los cuatro hechos son una sola transacción (`plan.md` §7).

## 8. Flujo principal

1. La persona abre el enlace y envía sus datos junto con el producto y el vendedor que este declara.
2. El sistema resuelve el producto y comprueba que existe, está activo, no está retirado y es un upgrade.
3. El sistema comprueba que la membresía destino del producto es **la gratuita**.
4. El sistema resuelve al vendedor por su nombre de usuario y comprueba que porta un rol `VENDEDOR`.
5. El sistema valida los datos de la persona y su contraseña contra la política.
6. El sistema crea la cuenta en estado `FTD_PENDIENTE`.
7. El sistema le concede el rol de consumidor y la membresía del producto, con su vigencia.
8. El sistema cuelga al cliente de ese vendedor en `user_supervisors`.
9. El sistema **declara su cuenta de broker** si el producto es `FREE → FREE`, con el nombre de usuario del broker **en nulo**.
10. El sistema registra los eventos de auditoría.
11. El sistema confirma el registro e indica que falta el depósito para poder operar.

## 9. Flujos alternativos

### FA-001 — El producto no declara vigencia

**Cuándo ocurre:** `validity_days` es nulo (`RN-PM-015`).

1. La membresía se asigna **sin fecha de fin**, que es lo que `user_memberships` entiende por indefinida.
2. No es un error ni un valor de relleno: es el caso normal de lo que no caduca.

## 10. Excepciones

### EX-001 — Producto inexistente, inactivo o retirado

**Respuesta:** se rechaza. **Los tres casos comparten respuesta**: distinguirlos convertiría este endpoint público en una forma de enumerar el catálogo comercial probando códigos, que es exactamente lo que un endpoint sin autenticar no debe permitir.

### EX-002 — Vendedor inexistente, eliminado o sin rol vendedor

**Respuesta:** se rechaza, con los tres casos compartiendo respuesta por el mismo motivo — probando nombres de usuario se averiguaría quién trabaja aquí.

**No se registra al cliente sin atribución.** Es `RN-SP-027`, y la alternativa —admitirlo y dejar la atribución vacía— produciría clientes huérfanos que nadie descubre hasta que hay que pagar una comisión.

### EX-003 — El producto es un `BOT`

**Respuesta:** se rechaza. Un bot no declara membresía destino, y sin nivel la cuenta violaría `RN-SP-018` en el mismo instante de nacer.

### EX-004 — El producto lleva a una membresía de pago

**Respuesta:** se rechaza indicando que ese producto exige pago. **Es la única excepción que sí dice qué pasó**, y la asimetría con `EX-001` es deliberada: aquí el producto existe y está activo —el enlace es legítimo—, y callarlo dejaría a una persona con un enlace bueno sin entender por qué no funciona. No revela nada que el enlace no dijera ya.

### EX-005 — Nombre de usuario o correo ya en uso

**Respuesta:** se rechaza el que esté en uso.

**Aquí sí se distingue, y contradice a `EX-001` a propósito.** Quien se registra necesita saber cuál de sus dos identidades chocó para poder corregirla; callarlo lo deja probando a ciegas. El coste está declarado: este endpoint permite comprobar si un correo está registrado. Se acepta porque **cualquier formulario de registro del mundo lo permite** —es indistinguible de la respuesta que da al usuario legítimo— y porque la defensa real es el límite de tasa de `plan.md` §5, no el silencio.

### EX-007 — Documento inexistente, inactivo o ya en uso

**Respuesta:** se rechaza, y **los tres casos comparten respuesta** con el mismo criterio que `EX-006` aplica al país: distinguirlos no ayuda a rellenar el formulario y sí permitiría enumerar el catálogo probando abreviaciones.

**Y el caso del documento repetido merece leerse aparte, porque es el que se aparta de `EX-005`.** Allí el nombre de usuario y el correo **sí** dicen cuál chocó, para que la persona pueda corregirlo. Aquí **no**: si el documento ya está registrado, decirlo confirmaría a un desconocido que **esa persona tiene cuenta en la plataforma** — y el número de documento de alguien es un dato que se consigue, al contrario que su elección de nombre de usuario. La respuesta es la misma que la de un tipo inexistente.

**El coste está declarado**: quien de verdad ya estaba registrado no sabrá por qué falla. La salida es iniciar sesión o recuperar la contraseña, que es lo que ya tenía que hacer.

### EX-006 — País inexistente o inactivo

**Respuesta:** se rechaza, y **los dos casos comparten respuesta**, al revés que en `RF-SP-024` `EX-009` y en `RF-SP-027` `EX-003`, donde sí se distinguen.

**La asimetría es la misma que separa `EX-001` de `EX-005` en esta especificación, y se resuelve igual: ¿informa al usuario legítimo, o enumera el sistema?** Distinguir «no existe» de «está inactivo» **no ayuda a nadie a rellenar el formulario** —en los dos casos la salida es elegir otro país— y en cambio convierte este endpoint público en una forma de averiguar **en qué mercados opera la plataforma y cuáles retiró**, probando códigos ISO. Es información comercial, y sale gratis obtenerla porque los códigos ISO son públicos y son doscientos cuarenta y nueve.

Dentro del sistema, en cambio, la distinción sí ayuda: un administrador **puede hacer algo distinto** con cada caso.

### EX-008 — Broker inexistente o inactivo

**Respuesta:** se rechaza, y **los dos casos comparten respuesta**, con el mismo criterio que `EX-006` aplica al país y `EX-007` al documento: distinguirlos no ayuda a rellenar el formulario y sí permitiría averiguar con qué brokers opera la plataforma probando identificadores. El catálogo **ya dice cuáles hay** —es público desde el 08-09-2026—, de modo que quien rellena el formulario no necesita esta respuesta para saberlo.

### EX-009 — La cuenta de broker ya está declarada

**Respuesta:** se rechaza. Es `RN-SP-038` —**una cuenta es de una sola persona**— y la sostiene el índice único, no una comprobación previa: dos registros simultáneos con la misma cuenta leen una tabla sin la fila y los dos creen que pueden.

**Aquí se dice qué pasó, y contradice a `EX-007` a propósito.** El documento repetido **no** dice que lo esté, porque confirmarle a un desconocido que esa persona ya tiene cuenta es publicar un dato que él no tenía. Con la cuenta de broker el intercambio es el contrario: **quien la declara es su titular** —tuvo que abrirla en el broker— y necesita saber que ya está tomada, porque significa que alguien se la atribuyó. **Que el número de cuenta sea de quien lo envía es lo que hace segura la respuesta**, y por eso el mismo razonamiento no vale para el documento, que es un dato que se consigue.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Producto informado | Debe indicar el producto del enlace. |
| `VAL-002` | Vendedor informado | Debe indicar quién le compartió el enlace. |
| `VAL-003` | Nombre y apellidos no vacíos | El nombre y los apellidos son obligatorios. |
| `VAL-004` | Nombre de usuario válido y sin arroba | El nombre de usuario no es válido. |
| `VAL-005` | Correo con formato válido | El correo indicado no es válido. |
| `VAL-006` | Contraseña conforme a la política | La contraseña no cumple la política. |
| `VAL-007` | Nombre de usuario único | Ese nombre de usuario ya está en uso. |
| `VAL-008` | Correo único | Ese correo ya está en uso. |
| `VAL-009` | País informado, existente y activo (`RN-SP-034`) | El país indicado no es válido. |
| `VAL-010` | Tipo y número de documento informados y válidos (`RN-SP-035`) | El documento indicado no es válido. |
| `VAL-011` | Teléfono informado y con formato admitido (`RN-SP-037`) | El teléfono indicado no es válido. |
| `VAL-012` | Broker informado **cuando el producto es `FREE → FREE`** (`RN-SP-042`) | Debe indicar el broker donde tiene su cuenta. |
| `VAL-013` | Identificador de cuenta informado en el mismo caso | Debe indicar su identificador de cuenta en el broker. |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-507` | Una persona **sin token** se registra desde un enlace válido y la cuenta queda creada |
| `CA-SP-508` | La cuenta nace en **`FTD_PENDIENTE`** y no en `ACTIVO` |
| `CA-SP-509` | La cuenta nace con el rol de consumidor **y** la membresía del producto, en la misma operación |
| `CA-SP-510` | La vigencia de la membresía sale de `validity_days` del producto |
| `CA-SP-511` | Un producto **sin** vigencia produce una membresía **sin fecha de fin** |
| `CA-SP-512` | La contraseña la elige la persona y la cuenta **no** queda marcada para cambio obligatorio |
| `CA-SP-513` | El cliente queda **a cargo del vendedor en `user_supervisors`**, en una fila vigente |
| `CA-SP-525` | Un vendedor con clientes a cargo **no se puede desactivar ni eliminar** sin reasignarlos (`RN-SP-022`) |
| `CA-SP-526` | `GET /users/{id}/team` devuelve a los clientes junto al equipo, y cada fila lleva los roles que permiten distinguirlos |
| `CA-SP-514` | El producto se admite **por código y por identificador**, con el mismo resultado |
| `CA-SP-515` | Un producto inexistente, inactivo y uno retirado se rechazan **con la misma respuesta** |
| `CA-SP-516` | Un producto de tipo `BOT` se rechaza |
| `CA-SP-517` | Un producto que lleva a una membresía **de pago** se rechaza diciendo que exige pago |
| `CA-SP-518` | Un vendedor inexistente y uno sin rol `VENDEDOR` se rechazan **con la misma respuesta** |
| `CA-SP-519` | Un rechazo **no deja nada escrito**: ni cuenta, ni membresía, ni atribución |
| `CA-SP-520` | Un nombre de usuario ya usado se rechaza señalando ese campo, y lo mismo el correo |
| `CA-SP-521` | El registro **no devuelve credenciales de sesión** |
| `CA-SP-522` | La persona registrada **puede autenticarse** pese a no estar `ACTIVO` |
| `CA-SP-523` | El registro emite evento de seguridad con el vendedor y el producto en el detalle |
| `CA-SP-582` | El registro público **exige país** y la cuenta nace con él, igual que el alta administrativa |
| `CA-SP-583` | Un país inexistente y uno inactivo se rechazan **con la misma respuesta**, y el cuerpo **no dice qué países existen** |
| `CA-SP-600` | El registro público **exige documento y teléfono**, y acepta el alta sin dirección, complemento ni ciudad |
| `CA-SP-601` | **No existe abreviación que registre a un menor**: enviar `TI` se rechaza con la misma respuesta que una abreviación inventada, y el cuerpo **no dice qué tipos existen** |
| `CA-SP-609` | El registro por un enlace `FREE → FREE` **exige broker e identificador de cuenta**, y sin ellos se rechaza |
| `CA-SP-610` | La cuenta de broker queda declarada en la **misma operación** que la cuenta de la persona, con el **nombre de usuario del broker en nulo** |
| `CA-SP-611` | Un broker inexistente y uno inactivo se rechazan **con la misma respuesta** |
| `CA-SP-612` | Una cuenta de broker **ya declarada por otra persona** se rechaza diciendo qué pasó, y **no deja nada escrito** — ni cuenta, ni membresía, ni atribución |
| `CA-SP-613` | La misma persona **puede** declarar en el registro una cuenta que ella ya tiene en **otro** broker: lo único que no se repite es el par broker + identificador |
| `CA-SP-524` | Un administrador puede llevar la cuenta de `FTD_PENDIENTE` a `ACTIVO` por `RF-SP-028` |

## 13. Casos límite

| ID | Caso | Resolución |
|---|---|---|
| `CL-001` | El vendedor del enlace se desactiva entre que lo comparte y alguien lo usa | Se rechaza: `EX-002` mira el estado en el momento del registro. El enlace no reserva nada |
| `CL-002` | El producto se retira mientras alguien rellena el formulario | Se rechaza con `EX-001`. No hay reserva ni bloqueo |
| `CL-003` | Dos personas se registran a la vez con el mismo nombre de usuario | La unicidad la sostiene el índice, no la comprobación previa: la segunda recibe `VAL-007` |
| `CL-004` | Alguien compone un enlace que nadie le dio | Se registra igual, y no gana nada: §2. Lo único que consigue es atribuirse a otro vendedor, que es el riesgo de §14 |
| `CL-005` | La membresía gratuita se renombra o desaparece | El sistema **no arranca**: `plan.md` §5 lo verifica al iniciar, como ya hace el catálogo de monedas |
| `CL-006` | El vendedor del enlace es el propio superadministrador u otro funcionario | Se rechaza: `EX-002` exige rol `VENDEDOR`, y un funcionario no comisiona |
| `CL-007` | El vendedor del enlace es un `MANAGER` o un `DIRECTOR`, no un `AGENTE` | Se admite. La rama de consumidor de `RN-SP-020` **no exige parentesco de roles**: cualquiera de la fuerza comercial puede traer un cliente directamente |
| `CL-008` | Un cliente cambia de vendedor | Es `RF-SP-041` sin cambios de forma: cierra la fila vigente y abre otra, exactamente como con un vendedor. Su validación sí cambia, porque debe aceptar la rama de consumidor |

## 14. Preguntas abiertas

| Pregunta | Estado |
|---|---|
| ~~**De dónde saca el formulario público la lista de TIPOS DE DOCUMENTO.**~~ | **CERRADA el 08-09-2026**, en el mismo lote: los tres catálogos que este formulario necesita —países, tipos de documento y brokers— se leen **sin iniciar sesión**. Era la pregunta que llevaba dos enmiendas abierta, y se resolvió abriendo los catálogos y no dando permisos a quien no tiene cuenta |
| ~~**De dónde saca el formulario público la lista de países.**~~ | **CERRADA el 08-09-2026** por decisión del responsable del proyecto: `GET /api/v1/countries` es **público** (`RN-SP-041`). El bloqueo 6 de `tasks.md` se cierra con ella |
| **La atribución es forjable.** Quien componga el enlace elige a qué vendedor se atribuye. No concede acceso, pero ensucia la base sobre la que `CM` comisionará. Cerrarlo exige que el enlace sea un artefacto emitido y persistido, con su tabla y su operación de emisión | **Abierta, y aceptada por ahora.** No bloquea: hoy no se paga ninguna comisión, porque la liquidación no existe. **La condición para reabrirla queda escrita: en cuanto se liquide una comisión sobre una atribución, el enlace tiene que dejar de ser componible** |
| **El camino de pago.** Los productos que llevan a membresías de pago se rechazan hoy con `EX-004` | **Abierta por dependencia.** Espera al área de Finanzas. La forma de este requerimiento no cambia: cambia la rama que hoy rechaza |
| **La confirmación del depósito.** Será el webhook del bróker | **Abierta, y desde el 09-09-2026 tiene la mitad resuelta.** El webhook es `RF-SP-054`, registrado y sin decidir; lo que ya no falta es **a quién confirmar**: la cuenta de broker se declara en este mismo registro (`RN-SP-042`). Mientras el webhook no exista, la salida de `FTD_PENDIENTE` sigue siendo manual por `RF-SP-028` |
| **Cuál es la membresía gratuita se decide por el código `FREE`** | **Resuelta el 01-09-2026 por el responsable del proyecto.** Se eligió la convención sobre una columna explícita. Queda mitigada con la verificación al arrancar de `CL-005`, que convierte un renombrado en un arranque fallido en lugar de en un registro roto en producción |

## 15. Control de cambios

| Versión | Fecha | Cambio | Responsable |
|---|---|---|---|
| 0.1.0 | 01-09-2026 | Redacción inicial. | Responsable técnico |
| 0.2.0 | 07-09-2026 | El formulario público **exige país** (`RN-SP-034`): nacen `EX-006`, `VAL-009`, `CA-SP-582` y `CA-SP-583`, y con ellos la pregunta abierta de **de dónde saca la lista** quien no tiene cuenta. | Responsable del proyecto |
| 0.3.0 | 08-09-2026 | El formulario público **exige documento y teléfono** (`RN-SP-035`, `RN-SP-037`): nacen `EX-007`, `VAL-010`, `VAL-011`, `CA-SP-600` y `CA-SP-601`. **La pregunta del catálogo crece a dos**, y eso es lo que acaba cambiando la decisión: con uno era una excepción, con dos es un patrón. | Responsable del proyecto |
| **1.0.0** | **09-09-2026** | **APROBADA**, y con tres cambios que la tripleta no podía prever cuando se escribió el 01-09. **Uno: sus dos preguntas abiertas de catálogo se cerraron solas** — los tres catálogos que este formulario necesita se leen **sin iniciar sesión** desde el 08-09-2026 (`RN-SP-041`), y con ellas se cierra el **bloqueo 6**. **Dos: el formulario declara la CUENTA DE BROKER** (`RN-SP-042`), obligatoria **solo cuando el producto es `FREE → FREE`**; nacen `EX-008`, `EX-009`, `VAL-012`, `VAL-013` y `CA-SP-609` a `CA-SP-613`. La condición **hoy se cumple siempre** —todo producto admisible aquí es `FREE → FREE`, porque `RN-PM-017` impide apuntar por debajo del origen y el destino tiene que ser la gratuita— y lo que compra es **el día que se abra el camino de pago**: entonces entrarán productos cuyo acceso no depende de ningún depósito, y pedir la cuenta de broker sería pedir un dato que no hace falta. **Tres: la pregunta de la confirmación del depósito pasa a tener la mitad resuelta** — el webhook sigue sin decidir (`RF-SP-054`), pero **ya se sabe a quién confirmar**. Lo que no cambia: el camino de pago sigue rechazado por `EX-004` y la atribución sigue siendo forjable. | Responsable del proyecto |
