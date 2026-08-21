# SPEC — `RF-SP-024` Registrar usuario

| Campo | Valor |
|---|---|
| Requerimiento | `RF-SP-024` |
| Módulo | `SP` — Sistema Principal |
| Estado | **Aprobada** |
| Autor | Responsable técnico |
| Aprobada por | Responsable técnico |
| Fecha de aprobación | 21-08-2026 |
| Enmendada | 21-08-2026 — `RN-SP-018` obliga a indicar la membresía si el alta concede un rol `CONSUMIDOR`, al aprobar `RF-SP-033` (Art. I.7) |

---

## 1. Objetivo

Dar de alta a una persona para que pueda acceder al sistema, con una identidad propia e irrepetible.

## 2. Contexto

Todo lo que `SP` define —roles, permisos, membresías— se apoya en que exista alguien a quien asignárselo. Sin usuarios, el catálogo de roles es una declaración sin sujeto y los cuatro registros de auditoría no tienen a quién atribuir lo que ocurre.

La identidad que se crea aquí es **la referencia permanente de esa persona en el sistema**. Los cuatro registros de auditoría apuntan al actor por su identificador, y ese identificador debe seguir resolviendo a un usuario para siempre: por eso `security.md` §3.1 prohíbe el borrado físico y por eso `RN-SP-016` **no libera el nombre de usuario ni el correo al eliminar**. Es la diferencia con los roles, cuyo código sí se reutiliza (`RN-SEG-001`): un rol es una etiqueta, un usuario es una persona, y dos personas distintas compartiendo nombre de usuario a lo largo del tiempo harían indistinguible su actividad en la auditoría.

Ese es el motivo por el que esta alta es más rígida de lo que parece: casi nada de lo que fija se puede deshacer después.

**La persona recibe dos identidades, y con cualquiera de las dos puede entrar.** El correo es el dato con el que se la localiza y el que ella reconoce; el nombre de usuario es el dato **estable**, el que no cambia nunca y con el que aparece en la auditoría. El correo sí puede corregirse (`RF-SP-027`), y esa es exactamente la razón por la que no puede ser la única identidad: lo que la auditoría referencia tiene que seguir significando lo mismo dentro de diez años.

Que ambas sirvan para iniciar sesión obliga a una condición sobre el formato: **el nombre de usuario no admite la arroba**. Con esa sola restricción, ningún nombre de usuario puede parecerse a un correo, ningún valor presentado en el inicio de sesión es ambiguo, y no hace falta que las dos columnas compartan un espacio de unicidad común.

## 3. Actores

| Actor | Rol en esta funcionalidad |
|---|---|
| Super Administrador | Registra usuarios sin más límite que el catálogo de roles |
| Administrador | Registra usuarios |

## 4. Alcance

### 4.1 Incluye

- Alta de un usuario con su nombre de usuario, su correo y sus datos de identificación personal.
- Establecimiento de su credencial inicial.
- Asignación opcional de un conjunto inicial de roles.

### 4.2 No incluye

- Asignar o retirar roles después del alta → `RF-SP-030` y `RF-SP-031`.
- Cambiar o renovar la membresía después del alta → `RF-SP-032`. El alta solo la establece cuando concede un rol `CONSUMIDOR`, porque `RN-SP-018` no admite el estado intermedio.
- Cambiar el estado del usuario → `RF-SP-028`.
- Cambiar la contraseña → `RF-SP-037` y `RF-SP-038`.
- El auto-registro de un consumidor desde la aplicación pública: esta funcionalidad es administrativa y exige `users:create`.

## 5. Reglas de negocio aplicables

| ID | Regla | Origen |
|---|---|---|
| `RN-SP-016` | El nombre de usuario y el correo son únicos y **no se liberan al eliminar** | `requirements/sp.md` §5.1 |
| `RN-SEG-010` | Nadie concede privilegios que no posee | `security.md` §4.3 |

## 6. Datos

### 6.1 Entrada

| Dato | Obligatorio | Descripción | Restricción de negocio |
|---|---|---|---|
| Nombre de usuario | Sí | Identidad estable de la persona, y una de las dos con las que inicia sesión | Único entre **todos** los usuarios, incluidos los eliminados. **No admite arroba**, para que nunca pueda confundirse con un correo. No se modifica después |
| Correo | Sí | Correo de la persona, y la otra forma de iniciar sesión | Único entre todos los usuarios, incluidos los eliminados. Formato de correo válido |
| Nombre y apellidos | Sí | Cómo se llama la persona | Es lo que la interfaz y la auditoría muestran cuando hay que decir quién hizo algo |
| Contraseña inicial | Sí | Credencial con la que la persona entra por primera vez | Debe cumplir la política mínima de contraseña (`security.md` §3.2). Nunca se devuelve ni se registra. **La persona deberá cambiarla en su primer inicio de sesión** |
| Roles | No | Roles que se le asignan al crearlo | Cada uno debe existir, estar activo y no exceder los privilegios del actor |
| Membresía | **Condicional** | Nivel de acceso de la persona | **Obligatoria** si alguno de los roles indicados es de clasificación `CONSUMIDOR` (`RN-SP-018`). No se admite en ningún otro caso |

### 6.2 Salida

| Dato | Descripción |
|---|---|
| Identificador | Identificador del usuario creado |
| Usuario | Nombre de usuario, correo, nombre, estado y roles asignados |
| Cambio de contraseña pendiente | Indicador de que la persona debe cambiar su credencial en el primer inicio de sesión |

La contraseña **no forma parte de la salida** en ninguna forma, ni siquiera transformada.

## 7. Precondiciones y postcondiciones

**Precondiciones**

- El actor está autenticado y posee el permiso de creación de usuarios.
- Si se indican roles, todos existen y están activos.

**Postcondiciones**

- El usuario queda registrado y puede autenticarse con la credencial establecida, presentando **indistintamente su correo o su nombre de usuario**.
- Queda **marcado para cambio obligatorio de contraseña**: `RF-SP-034` lo autentica y le advierte, y la marca se limpia cuando ejecuta `RF-SP-037`.
- Su nombre de usuario y su correo quedan reservados de forma permanente: no volverán a estar disponibles ni siquiera si el usuario se elimina.
- Sus roles quedan asignados y sus permisos efectivos son la unión de los permisos de esos roles (`RN-SEG-009`).
- La contraseña queda almacenada con Argon2id y no es recuperable (`security.md` §3.2).
- Queda constancia en la auditoría de cambios y en la de seguridad, sin ningún dato de la credencial (Art. IV.8).

## 8. Flujo principal

1. El actor solicita registrar un usuario y proporciona sus datos.
2. El sistema valida el formato y la obligatoriedad de los datos.
3. El sistema verifica que la contraseña cumple la política mínima.
4. El sistema verifica que el nombre de usuario y el correo no estén en uso por ningún usuario, incluidos los eliminados.
5. El sistema verifica que los roles indicados existan y estén activos.
6. El sistema verifica que los roles indicados no excedan los privilegios del actor.
7. El sistema registra al usuario con su credencial protegida y sus roles, y lo marca para cambio obligatorio de contraseña.
8. El sistema registra el evento en la auditoría de cambios y en la de seguridad.
9. El sistema informa el usuario creado, sin dato alguno de la credencial.

## 9. Flujos alternativos

### FA-001 — Alta sin roles

**Cuándo ocurre:** el actor no indica ningún rol.

1. El sistema omite las verificaciones de rol.
2. El usuario queda registrado sin ningún rol y, por tanto, **sin permiso efectivo alguno**: puede autenticarse, pero no puede hacer nada. Es un estado válido y transitorio, a la espera de `RF-SP-030`.

## 10. Excepciones

### EX-001 — Nombre de usuario o correo ya en uso

**Condición:** algún usuario, vigente **o eliminado**, ya tiene ese nombre de usuario o ese correo.
**Respuesta del sistema:** rechaza el alta, cita `RN-SP-016` e informa cuál de los dos está en uso. No revela si el conflicto es con un usuario vigente o con uno eliminado: la respuesta es la misma en ambos casos, y distinguirla informaría de la existencia de una cuenta.

### EX-002 — Contraseña que no cumple la política

**Condición:** la contraseña es más corta que el mínimo configurado, aparece en la lista de contraseñas comunes o incumple cualquier otra regla de la política.
**Respuesta del sistema:** rechaza el alta e informa **qué** regla incumple, sin reproducir la contraseña en el mensaje ni en ningún registro.

### EX-003 — Rol inexistente o inactivo

**Condición:** alguno de los roles indicados no existe, está eliminado o está inactivo.
**Respuesta del sistema:** rechaza el alta completa e informa qué roles no son válidos. No se crea el usuario sin esos roles: quedaría en un estado que nadie pidió.

### EX-005 — Rol consumidor sin membresía, o membresía sin rol consumidor

**Condición:** el alta incluye un rol de clasificación `CONSUMIDOR` y no indica membresía, o indica membresía sin que ningún rol sea de consumidor.
**Respuesta del sistema:** rechaza el alta completa y cita `RN-SP-018`. El rol de consumidor y el nivel de acceso son inseparables: se conceden juntos o no se concede ninguno.

### EX-004 — Rol fuera del alcance del actor

**Condición:** alguno de los roles indicados concede permisos que el actor no posee.
**Respuesta del sistema:** rechaza el alta, cita `RN-SEG-010` e informa qué roles lo incumplen. Sin esta verificación, quien puede crear usuarios podría fabricarse un superadministrador.

## 11. Validaciones

| ID | Validación | Mensaje esperado |
|---|---|---|
| `VAL-001` | Nombre de usuario obligatorio | El nombre de usuario es obligatorio. |
| `VAL-002` | Correo obligatorio y con formato válido | El correo indicado no es válido. |
| `VAL-003` | Nombre y apellidos obligatorios | El nombre de la persona es obligatorio. |
| `VAL-004` | Contraseña obligatoria | La contraseña es obligatoria. |
| `VAL-005` | Contraseña conforme a la política mínima | La contraseña no cumple la política de seguridad. |
| `VAL-006` | Nombre de usuario único, incluidos los eliminados | Ese nombre de usuario ya está en uso. |
| `VAL-007` | Correo único, incluidos los eliminados | Ese correo ya está en uso. |
| `VAL-008` | Longitud máxima de los campos de texto | El campo excede la longitud permitida. |
| `VAL-009` | Los roles indicados existen y están activos | Uno o más roles no son válidos. |
| `VAL-010` | El nombre de usuario no contiene arroba | El nombre de usuario no puede contener el carácter «@». |

## 12. Criterios de aceptación

| ID | Criterio |
|---|---|
| `CA-SP-192` | El sistema registra un usuario con datos válidos y lo deja en condiciones de autenticarse, **indistintamente con su correo o con su nombre de usuario** |
| `CA-SP-341` | El sistema rechaza un nombre de usuario que contenga arroba, de modo que nunca puede confundirse con un correo |
| `CA-SP-342` | El usuario creado queda marcado para cambio obligatorio de contraseña, y la marca se limpia al ejecutar `RF-SP-037` |
| `CA-SP-193` | El sistema rechaza el alta cuando el nombre de usuario o el correo ya están en uso por un usuario vigente |
| `CA-SP-194` | El sistema rechaza el alta cuando el nombre de usuario o el correo pertenecen a un usuario **eliminado**, con la misma respuesta que en el caso vigente |
| `CA-SP-195` | El sistema rechaza una contraseña que no cumple la política mínima, indicando qué regla incumple |
| `CA-SP-196` | La contraseña no aparece en la respuesta, ni en los registros de operación, ni en los de auditoría |
| `CA-SP-197` | El sistema permite registrar un usuario sin roles, y ese usuario queda sin permiso efectivo alguno |
| `CA-SP-198` | El sistema rechaza el alta completa cuando alguno de los roles indicados no existe o está inactivo |
| `CA-SP-199` | El sistema rechaza el alta cuando algún rol indicado excede los privilegios del actor |
| `CA-SP-200` | El sistema registra el alta en la auditoría de cambios y en la de seguridad |
| `CA-SP-201` | El alta concurrente del mismo nombre de usuario produce un error de duplicado, no un error interno |
| `CA-SP-372` | El sistema rechaza el alta que concede un rol `CONSUMIDOR` sin indicar membresía, y la que indica membresía sin rol de consumidor |
| `CA-SP-373` | El alta que concede un rol `CONSUMIDOR` con su membresía deja ambas cosas escritas en la misma transacción |
| `CA-SP-202` | El sistema rechaza el alta a un actor sin el permiso de creación de usuarios |

## 13. Casos límite

- **Nombre de usuario de un usuario eliminado:** se rechaza. Es la diferencia deliberada con los roles, donde `CA-SP-006` sí permite reutilizar el código de uno eliminado.
- **Correo con mayúsculas o espacios sobrantes:** se normaliza antes de verificar la unicidad, para que no entren duplicados que solo difieren en el caso.
- **Nombre de usuario que coincide con el correo de otra persona:** imposible por construcción, porque `VAL-010` prohíbe la arroba. Es lo que permite que el inicio de sesión acepte ambos sin espacio de unicidad común.
- **Alta concurrente del mismo nombre de usuario:** la restricción única del esquema resuelve el empate; el segundo intento recibe el error de duplicado.
- **Alta con un rol que se desactiva a la vez:** ambas operaciones se serializan sobre la fila del rol, igual que exige `RF-SP-009`. El alta o ve el rol activo o lo rechaza, pero no crea un usuario con un rol inactivo.
- **Usuario sin ningún rol:** puede autenticarse y recibirá un token sin códigos de rol. Toda petición posterior será denegada por autorización. No es un error, pero la interfaz debería hacerlo evidente.
- **Contraseña igual al nombre de usuario o al correo:** la política mínima de `security.md` §3.2 no lo cubre explícitamente. Conviene que la política declarada lo incluya, o el alta admitirá credenciales triviales que cumplen la longitud.
- **Primer usuario del sistema:** no se crea por esta funcionalidad. El superadministrador inicial se siembra por migración, porque esta operación exige un actor autenticado con `users:create` y no habría ninguno.

## 14. Preguntas abiertas

Ninguna. Las cinco se resolvieron el 21-08-2026, antes de aprobar la especificación. Tres de ellas alcanzan a otras especificaciones y quedan fijadas para todas: `RF-SP-027`, `RF-SP-034`, `RF-SP-037` y `RF-SP-038`.

| # | Pregunta | Resolución |
|---|---|---|
| 1 | ¿El alta fija la contraseña, o el usuario nace `PENDIENTE` y la establece él? | **El actor fija una contraseña inicial y el usuario nace `ACTIVO`.** El camino de `PENDIENTE` exige un canal de correo y un flujo de activación que hoy no existen en ningún requerimiento: habría que inventarlos enteros para dar de alta a la primera persona. El estado `PENDIENTE` de `security.md` §3.1 **queda declarado y sin usar** hasta que ese flujo exista, y así lo recoge `RF-SP-028`, que no lo admite en su dominio. El coste —que el actor conoce temporalmente la credencial de otra persona— se acota con la resolución 2 |
| 2 | ¿Debe obligarse a cambiar la contraseña en el primer inicio de sesión? | **Sí.** El usuario nace con un indicador de **cambio obligatorio de contraseña**, y eso reduce a un solo inicio de sesión la ventana en que dos personas conocen la misma credencial. Sin él, la auditoría no podría distinguir quién actuó. Reparte obligaciones en tres requerimientos más: `RF-SP-034` **autentica y advierte**, no rechaza —la persona necesita una sesión para poder cambiarla—; `RF-SP-037` limpia la marca; y `RF-SP-038` la vuelve a fijar al restablecer. `CA-SP-342` lo verifica de extremo a extremo |
| 3 | ¿Qué datos personales lleva el usuario, además del nombre? | **Nombre y apellidos, y nada más por ahora.** `modelo-datos.md` §1 no registraba ninguno de los dos, y la auditoría y la interfaz necesitan poder decir quién es la persona y no solo su nombre de usuario. Documento de identidad, teléfono y país se dejan fuera de forma deliberada: cada uno arrastra decisiones propias —formato, unicidad, qué pasa con quien no tenga documento del país— que ningún requerimiento respalda todavía. Se añadirán cuando uno lo pida, y añadir una columna a `users` es una migración corriente, no una revisión de nada ya calculado |
| 4 | ¿El nombre de usuario es un dato aparte del correo? | **Sí, los dos, y con cualquiera de los dos se inicia sesión.** El correo es el dato que la persona reconoce y el que puede corregirse (`RF-SP-027`); el nombre de usuario es el dato **estable**, el que no cambia nunca y con el que aparece en la auditoría. Justamente porque el correo cambia no puede ser la única identidad: lo que la auditoría referencia tiene que seguir significando lo mismo dentro de diez años. Que ambos sirvan para entrar obliga a una condición de formato —**el nombre de usuario no admite arroba** (`VAL-010`)—, y con ella ningún valor presentado en el inicio de sesión es ambiguo y las dos columnas no necesitan compartir espacio de unicidad. `RF-SP-034` recibe la obligación de aceptar ambos |
| 5 | ¿La verificación de `RN-SEG-010` sobre los roles se hace aquí, o se delega en `RF-SP-030`? | **En ambos sitios, con un componente compartido.** Un alta que asigna roles concede privilegios igual que una asignación posterior; dejarla fuera aquí abriría el hueco de fabricarse un superadministrador en la misma petición del alta, que es exactamente lo que `RN-SEG-010` existe para impedir. `EX-004` la declara, y el `plan.md` debe resolverla con **un único componente** compartido con `RF-SP-030` y con `RF-SP-005`, para que las tres no puedan divergir con el tiempo |
