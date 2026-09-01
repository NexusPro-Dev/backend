# SPEC — `RF-SP-045` Registro de clientes por enlace

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-045` |
| Módulo | `SP` — Sistema Principal |
| Estado | **En revisión** |
| Autor | Responsable técnico |
| Aprobada por | Pendiente |
| Fecha de aprobación | — |

---

## 1. Objetivo

Que un cliente entre al sistema por sí mismo desde un enlace, quedando con su membresía puesta y atribuido al vendedor que lo trajo.

## 2. Contexto

**Hoy no existe ninguna forma de que alguien se registre.** La única alta de personas es `RF-SP-024`, y exige `users:create` — un permiso de administración. Un cliente que llega desde una campaña no tiene cuenta, no tiene token y no tiene a quién pedírselo: `SecurityConfig` declara seis rutas públicas —la salud, las tres de sesión y las dos de recuperación— y todo lo demás exige estar autenticado.

**El enlace lleva dos datos y ninguno es un secreto**: el producto —por su código o su identificador— y el vendedor que lo generó. De ahí sale todo lo demás: el producto declara la membresía destino (`RN-PM-002` la hace obligatoria en los upgrades) y su vigencia en días, y el vendedor es a quien se atribuye el cliente.

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
- Registrar la **atribución al vendedor** del enlace.

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
| `RN-SP-028` | **Nueva.** La atribución es única vigente y conserva su historial | §5.1, este requerimiento |
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
- Existe una atribución vigente del cliente al vendedor del enlace.
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
8. El sistema registra la atribución al vendedor.
9. El sistema registra los eventos de auditoría.
10. El sistema confirma el registro e indica que falta el depósito para poder operar.

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

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-507` | Una persona **sin token** se registra desde un enlace válido y la cuenta queda creada |
| `CA-SP-508` | La cuenta nace en **`FTD_PENDIENTE`** y no en `ACTIVO` |
| `CA-SP-509` | La cuenta nace con el rol de consumidor **y** la membresía del producto, en la misma operación |
| `CA-SP-510` | La vigencia de la membresía sale de `validity_days` del producto |
| `CA-SP-511` | Un producto **sin** vigencia produce una membresía **sin fecha de fin** |
| `CA-SP-512` | La contraseña la elige la persona y la cuenta **no** queda marcada para cambio obligatorio |
| `CA-SP-513` | Queda registrada la atribución del cliente al vendedor del enlace |
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

## 14. Preguntas abiertas

| Pregunta | Estado |
|---|---|
| **La atribución es forjable.** Quien componga el enlace elige a qué vendedor se atribuye. No concede acceso, pero ensucia la base sobre la que `CM` comisionará. Cerrarlo exige que el enlace sea un artefacto emitido y persistido, con su tabla y su operación de emisión | **Abierta, y aceptada por ahora.** No bloquea: hoy no se paga ninguna comisión, porque la liquidación no existe. **La condición para reabrirla queda escrita: en cuanto se liquide una comisión sobre una atribución, el enlace tiene que dejar de ser componible** |
| **El camino de pago.** Los productos que llevan a membresías de pago se rechazan hoy con `EX-004` | **Abierta por dependencia.** Espera al área de Finanzas. La forma de este requerimiento no cambia: cambia la rama que hoy rechaza |
| **La confirmación del depósito.** Será el webhook del bróker | **Aplazada por decisión del responsable** (01-09-2026). Mientras tanto, la salida es manual por `RF-SP-028` |
| **Cuál es la membresía gratuita se decide por el código `FREE`** | **Resuelta el 01-09-2026 por el responsable del proyecto.** Se eligió la convención sobre una columna explícita. Queda mitigada con la verificación al arrancar de `CL-005`, que convierte un renombrado en un arranque fallido en lugar de en un registro roto en producción |
